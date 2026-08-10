from __future__ import annotations

import importlib.util
import io
import json
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).parents[1] / "verify_release_documentation.py"
SPEC = importlib.util.spec_from_file_location("verify_release_documentation", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

RELEASE_COMMIT = "0dc3cac1f7e6b02fa4d5f069eda8664789852370"
OTHER_COMMIT = "1" * 40


def public_state() -> dict[str, object]:
    return {
        "version": "0.1.37",
        "version_code": 37,
        "tag": "v0.1.37",
        "commit": RELEASE_COMMIT,
        "status": "public_beta",
        "release_notes": "docs/release/RELEASE_NOTES_v0.1.37.md",
    }


def git_runner(*, peel: tuple[int, str] = (0, RELEASE_COMMIT), head: str = OTHER_COMMIT, ancestor: int = 0):
    responses = {
        ("rev-parse", "--verify", "refs/tags/v0.1.37^{commit}"): peel,
        ("rev-parse", "HEAD"): (0, head),
        ("merge-base", "--is-ancestor", RELEASE_COMMIT, head): (ancestor, ""),
        ("show", "v0.1.37:app/build.gradle.kts"): (0, 'versionCode = 37\nversionName = "0.1.37"'),
        ("show", "v0.1.37:docs/release/RELEASE_NOTES_v0.1.37.md"): (
            0,
            "# Codecks v0.1.37 release notes\nhttps://github.com/vaddisrinivas/codecks/releases/tag/v0.1.37",
        ),
    }
    return lambda arguments: responses[tuple(arguments)]


class ReleaseDocumentationTest(unittest.TestCase):
    def test_repository_is_consistent(self) -> None:
        self.assertEqual(0, MODULE.main())

    def test_duplicate_state_key_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "state.json"
            state.write_text('{"schema":"one","schema":"two"}')
            with patch.object(MODULE, "STATE_PATH", state):
                with redirect_stderr(io.StringIO()), redirect_stdout(io.StringIO()):
                    self.assertEqual(1, MODULE.main())

    def test_commercial_enablement_fails_closed(self) -> None:
        original = json.loads(MODULE.STATE_PATH.read_text())
        original["commercial"]["ads"] = "ON"
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "state.json"
            state.write_text(json.dumps(original))
            with patch.object(MODULE, "STATE_PATH", state):
                with redirect_stderr(io.StringIO()), redirect_stdout(io.StringIO()):
                    self.assertEqual(1, MODULE.main())

    def test_broken_local_link_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            document = root / "doc.md"
            document.write_text("[missing](missing.md)")
            errors: list[str] = []
            with patch.object(MODULE, "ROOT", root):
                MODULE.validate_local_links([document], errors)
            self.assertEqual(["broken local link: doc.md -> missing.md"], errors)

    def test_broken_fragment_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target.md"
            target.write_text("# Real heading\n")
            document = root / "doc.md"
            document.write_text("[wrong](target.md#missing-heading)")
            errors: list[str] = []
            with patch.object(MODULE, "ROOT", root):
                MODULE.validate_local_links([document], errors)
            self.assertEqual(["broken local fragment: doc.md -> target.md#missing-heading"], errors)

    def test_symlinked_evidence_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target.txt"
            target.write_text("proof")
            (root / "link.txt").symlink_to(target)
            errors: list[str] = []
            with patch.object(MODULE, "ROOT", root):
                MODULE.validate_evidence(["link.txt"], errors)
            self.assertEqual(["missing or symlinked evidence: link.txt"], errors)

    def test_symlinked_state_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target.json"
            target.write_text("{}")
            state = root / "state.json"
            state.symlink_to(target)
            errors: list[str] = []
            with patch.object(MODULE, "ROOT", root), patch.object(MODULE, "STATE_PATH", state):
                self.assertEqual({}, MODULE.load_state(errors))
            self.assertEqual(["production-state.json must be a regular repository file"], errors)

    def test_completed_checklist_without_link_fails_closed(self) -> None:
        errors: list[str] = []
        MODULE.validate_completed_checklist("- [x] unsupported claim\n", errors)
        self.assertEqual(["completed commercial checklist item 1 lacks an evidence link"], errors)

    def test_stale_launch_version_fails_closed(self) -> None:
        docs = {
            "README.md": "v0.1.37 :app:testOssReleaseUnitTest :app:lintOssDebug :app:assembleOssDebug",
            "launch plan": "v0.1.37 Current release is `v0.1.36`",
            "release ledger": "v0.1.37 | Version | `0.1.37` (`versionCode` 37) |",
            "feature guide": "v0.1.37 Applies to: public beta v0.1.37",
            "commercial plan": "v0.1.37",
            "commercial checklist": "v0.1.37",
            "FOSS readiness": "v0.1.37",
        }
        errors: list[str] = []
        MODULE.validate_document_truth(docs, "0.1.37", 37, errors)
        self.assertIn("stale v0.1.36 launch baseline returned", errors)

    def test_missing_release_tag_fails_closed(self) -> None:
        errors: list[str] = []
        MODULE.validate_git_release(public_state(), "0.1.37", 37, errors, git_runner(peel=(128, "")))
        self.assertIn("release tag missing: v0.1.37", errors)

    def test_moved_release_tag_fails_closed(self) -> None:
        errors: list[str] = []
        MODULE.validate_git_release(public_state(), "0.1.37", 37, errors, git_runner(peel=(0, OTHER_COMMIT)))
        self.assertIn("release tag moved: v0.1.37", errors)

    def test_nonancestor_release_fails_closed(self) -> None:
        errors: list[str] = []
        MODULE.validate_git_release(public_state(), "0.1.37", 37, errors, git_runner(ancestor=1))
        self.assertIn("release commit is not an ancestor of HEAD", errors)

    def test_head_equal_to_release_fails_closed(self) -> None:
        errors: list[str] = []
        MODULE.validate_git_release(
            public_state(),
            "0.1.37",
            37,
            errors,
            git_runner(head=RELEASE_COMMIT),
        )
        self.assertIn("working HEAD must be strictly after the public release tag", errors)


if __name__ == "__main__":
    unittest.main()
