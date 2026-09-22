#!/usr/bin/env python3
"""Filesystem-boundary tests for source-inventory generation."""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

from generate_autonomous_maturity_source_inventory import atomic_write, generate, read_source  # noqa: E402
from validate_autonomous_maturity_evidence import load  # noqa: E402


class InventoryFilesystemSafetyTest(unittest.TestCase):
    def make_repo(self, directory: Path) -> Path:
        repo = directory / "repo"
        repo.mkdir()
        subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
        return repo

    def test_tracked_source_symlink_cannot_read_victim(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repo = self.make_repo(root)
            victim = root / "victim.kt"
            victim.write_text("TOP_SECRET")
            link = repo / "Leak.kt"
            link.symlink_to(victim)
            subprocess.run(["git", "add", "Leak.kt"], cwd=repo, check=True)
            with self.assertRaisesRegex(ValueError, "source symlink or missing file is forbidden"):
                generate(repo)

    def test_output_symlink_cannot_overwrite_victim(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repo = self.make_repo(root)
            victim = repo / "victim.json"
            victim.write_bytes(b"KEEP")
            output = repo / "inventory.json"
            output.symlink_to(victim)
            with self.assertRaisesRegex(ValueError, "output symlink is forbidden"):
                atomic_write(repo, output, b"REPLACE")
            self.assertEqual(victim.read_bytes(), b"KEEP")

    def test_output_path_escape_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repo = self.make_repo(root)
            victim = root / "victim.json"
            victim.write_bytes(b"KEEP")
            with self.assertRaisesRegex(ValueError, "output path escapes repository"):
                atomic_write(repo, victim, b"REPLACE")
            self.assertEqual(victim.read_bytes(), b"KEEP")

    def test_validator_refuses_symlinked_input_without_reading_victim(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            victim = root / "victim.json"
            victim.write_text('{"secret":"DO_NOT_READ"}')
            link = root / "inventory.json"
            link.symlink_to(victim)
            with self.assertRaisesRegex(ValueError, "refusing symlinked evidence input"):
                load(link)

    @unittest.skipUnless(hasattr(os, "O_NOFOLLOW"), "O_NOFOLLOW unavailable")
    def test_source_swap_to_symlink_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repo = self.make_repo(root)
            victim = root / "victim.kt"
            victim.write_text("TOP_SECRET")
            source = repo / "Source.kt"
            source.write_text("safe")
            subprocess.run(["git", "add", "Source.kt"], cwd=repo, check=True)
            source.unlink()
            source.symlink_to(victim)
            with self.assertRaisesRegex(ValueError, "source symlink or missing file is forbidden"):
                generate(repo)

    def test_source_parent_swap_cannot_redirect_open_fd(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            repo = self.make_repo(root)
            source_dir = repo / "src"
            source_dir.mkdir()
            (source_dir / "Source.kt").write_bytes(b"SAFE")
            outside = root / "outside"
            outside.mkdir()
            (outside / "Source.kt").write_bytes(b"VICTIM")
            original_open = os.open
            swapped = False

            def racing_open(path: object, flags: int, *args: object, **kwargs: object) -> int:
                nonlocal swapped
                if path == "Source.kt" and kwargs.get("dir_fd") is not None and not swapped:
                    swapped = True
                    source_dir.rename(repo / "moved")
                    source_dir.symlink_to(outside, target_is_directory=True)
                return original_open(path, flags, *args, **kwargs)

            with patch("generate_autonomous_maturity_source_inventory.os.open", side_effect=racing_open):
                self.assertEqual(read_source(repo, "src/Source.kt"), b"SAFE")

    def test_output_parent_swap_cannot_overwrite_victim(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            repo = self.make_repo(root)
            output_dir = repo / "out"
            output_dir.mkdir()
            outside = root / "outside"
            outside.mkdir()
            victim = outside / "inventory.json"
            victim.write_bytes(b"KEEP")
            original_replace = os.replace
            swapped = False

            def racing_replace(src: object, dst: object, **kwargs: object) -> None:
                nonlocal swapped
                if not swapped:
                    swapped = True
                    output_dir.rename(repo / "moved")
                    output_dir.symlink_to(outside, target_is_directory=True)
                original_replace(src, dst, **kwargs)

            with patch("generate_autonomous_maturity_source_inventory.os.replace", side_effect=racing_replace):
                atomic_write(repo, output_dir / "inventory.json", b"SAFE")
            self.assertEqual(victim.read_bytes(), b"KEEP")
            self.assertEqual((repo / "moved/inventory.json").read_bytes(), b"SAFE")


if __name__ == "__main__":
    unittest.main()
