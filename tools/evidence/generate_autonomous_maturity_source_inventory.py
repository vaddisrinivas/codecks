#!/usr/bin/env python3
"""Generate a deterministic inventory of tracked Kotlin, Java, and Swift sources."""

from __future__ import annotations

import hashlib
import json
import os
import stat
import subprocess
from argparse import ArgumentParser
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"
SOURCE_SUFFIXES = {".kt": "Kotlin", ".java": "Java", ".swift": "Swift"}
METHOD = {
    "scope": "tracked *.kt, *.java, and *.swift files from git ls-files",
    "line_definition": "Python str.splitlines physical lines; blank and comment lines included",
    "generated_policy": "tracked build/generated sources are separate; untracked build outputs are excluded",
    "classification": "path/source-set rules in tools/evidence/generate_autonomous_maturity_source_inventory.py",
}


def tracked_sources(root: Path = ROOT) -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "*.kt", "*.java", "*.swift"],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    )
    return sorted(path for path in result.stdout.splitlines() if path)


def relative_parts(relative: str) -> tuple[str, ...]:
    path = Path(relative)
    if path.is_absolute() or not path.parts or any(part in {"", ".", ".."} for part in path.parts):
        raise ValueError(f"unsafe repository path: {relative}")
    return path.parts


def open_root(root: Path) -> int:
    resolved = root.resolve(strict=True)
    return os.open(resolved, os.O_RDONLY | os.O_DIRECTORY | getattr(os, "O_NOFOLLOW", 0))


def open_parent(root: Path, relative: str) -> tuple[int, str]:
    parts = relative_parts(relative)
    descriptor = open_root(root)
    try:
        for part in parts[:-1]:
            child = os.open(
                part,
                os.O_RDONLY | os.O_DIRECTORY | getattr(os, "O_NOFOLLOW", 0),
                dir_fd=descriptor,
            )
            os.close(descriptor)
            descriptor = child
        return descriptor, parts[-1]
    except Exception:
        os.close(descriptor)
        raise


def read_source(root: Path, relative: str) -> bytes:
    parent, name = open_parent(root, relative)
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(name, flags, dir_fd=parent)
    except OSError as exc:
        os.close(parent)
        raise ValueError(f"source symlink or missing file is forbidden: {relative}") from exc
    try:
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise ValueError(f"source is not a regular file: {relative}")
        chunks = []
        while chunk := os.read(descriptor, 1024 * 1024):
            chunks.append(chunk)
        return b"".join(chunks)
    finally:
        os.close(descriptor)
        os.close(parent)


def atomic_write(root: Path, output: Path, payload: bytes) -> None:
    try:
        relative = str(output.absolute().relative_to(root.absolute()))
    except ValueError as exc:
        raise ValueError(f"output path escapes repository: {output}") from exc
    parent, name = open_parent(root, relative)
    temporary = f".{name}.{os.getpid()}.tmp"
    try:
        try:
            current = os.stat(name, dir_fd=parent, follow_symlinks=False)
            if stat.S_ISLNK(current.st_mode):
                raise ValueError(f"output symlink is forbidden: {output}")
        except FileNotFoundError:
            pass
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            0o600,
            dir_fd=parent,
        )
        try:
            view = memoryview(payload)
            while view:
                written = os.write(descriptor, view)
                view = view[written:]
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        os.replace(temporary, name, src_dir_fd=parent, dst_dir_fd=parent)
        os.fsync(parent)
    finally:
        try:
            os.unlink(temporary, dir_fd=parent)
        except FileNotFoundError:
            pass
        os.close(parent)


def classify(path: str) -> tuple[str, str]:
    parts = Path(path).parts
    if path == "macHelper/Package.swift":
        return "build_definition", "mac_helper_package_manifest"
    if "build" in parts and "generated" in parts:
        return "generated", "generated"
    if path.startswith("macHelper/Tests/"):
        return "tests", "mac_helper_tests"
    if path.startswith("macHelper/Sources/"):
        return "companion", "mac_helper"
    if path.startswith("shared/src/"):
        source_set = parts[2]
        if "test" in source_set.lower():
            return "tests", f"shared_{source_set}"
        return "companion", f"shared_{source_set}"
    if path.startswith("backend/src/"):
        source_set = parts[2]
        if "test" in source_set.lower():
            return "tests", f"backend_{source_set}"
        return "companion", f"backend_{source_set}"
    if path.startswith("app/src/"):
        source_set = parts[2]
        lowered = source_set.lower()
        if "test" in lowered:
            return "tests", f"android_{source_set}"
        if source_set == "main":
            return "public_production", "android_main"
        if source_set == "oss":
            return "public_production", "android_oss"
        if source_set == "play":
            return "public_production", "android_play_production_dark"
        if source_set == "playInternal":
            return "internal_lab", "android_play_internal"
        if source_set == "debug":
            return "debug_only", "android_debug"
        return "other_source_set", f"android_{source_set}"
    return "other_source_set", "repository_other"


def owner(path: str) -> str:
    marker = "/java/io/codecks/"
    if marker in path:
        tail = path.split(marker, 1)[1].split("/")
        return "/".join(tail[:2]) if len(tail) > 1 else "root"
    if path.startswith("shared/"):
        return "shared_protocol"
    if path.startswith("backend/"):
        return "backend_contracts"
    if path.startswith("macHelper/"):
        return "mac_helper"
    return "other"


def generate(root: Path = ROOT) -> dict:
    entries = []
    category_lines: Counter[str] = Counter()
    source_set_lines: Counter[str] = Counter()
    language_lines: Counter[str] = Counter()

    for relative in tracked_sources(root):
        raw = read_source(root, relative)
        text = raw.decode("utf-8")
        lines = len(text.splitlines())
        category, source_set = classify(relative)
        language = SOURCE_SUFFIXES[Path(relative).suffix]
        entry = {
            "path": relative,
            "category": category,
            "source_set": source_set,
            "feature_owner": owner(relative),
            "language": language,
            "physical_lines": lines,
            "sha256": hashlib.sha256(raw).hexdigest(),
        }
        entries.append(entry)
        category_lines[category] += lines
        source_set_lines[source_set] += lines
        language_lines[language] += lines

    payload = {
        "schema": "codecks.autonomous-maturity.source-inventory.v1",
        "method": METHOD,
        "summary": {
            "file_count": len(entries),
            "physical_lines": sum(item["physical_lines"] for item in entries),
            "by_category": dict(sorted(category_lines.items())),
            "by_source_set": dict(sorted(source_set_lines.items())),
            "by_language": dict(sorted(language_lines.items())),
        },
        "files": entries,
    }
    return payload


def main() -> None:
    parser = ArgumentParser()
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    payload = (json.dumps(generate(ROOT), indent=2, sort_keys=False) + "\n").encode()
    output = args.output.absolute()
    if args.check:
        try:
            relative = str(output.relative_to(ROOT.absolute()))
            current = read_source(ROOT, relative)
        except (OSError, ValueError) as exc:
            raise ValueError(f"inventory is stale or unsafe: {output}") from exc
        if current != payload:
            raise ValueError(f"inventory is stale or unsafe: {output}")
        print("PASS: source inventory matches tracked repository sources")
    else:
        atomic_write(ROOT, output, payload)


if __name__ == "__main__":
    main()
