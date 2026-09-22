#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from collect_m20_rollback_rehearsal import canonical_digest
from validate_m20_rollback_rehearsal import RECEIPT, ROOT, safe_path, validate


class M20EvidenceTest(unittest.TestCase):
    def mutated(self, mutation) -> Path:
        data = json.loads(RECEIPT.read_text(encoding="utf-8"))
        mutation(data)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "receipt.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def assert_rejected(self, mutation, pattern: str | None = None) -> None:
        path = self.mutated(mutation)
        context = self.assertRaisesRegex(ValueError, pattern) if pattern else self.assertRaises(ValueError)
        with context:
            validate(path)

    def test_current_receipt_passes(self) -> None:
        validate()

    def test_unknown_key_rejected(self) -> None:
        self.assert_rejected(lambda data: data.update({"unknown": True}))

    def test_wrong_type_rejected(self) -> None:
        self.assert_rejected(lambda data: data["summary"].update({"pass": "7"}))

    def test_stale_source_digest_rejected(self) -> None:
        stale = hashlib.sha256(b"stale source").hexdigest()
        self.assert_rejected(lambda data: data["sources"][0].update({"sha256": stale}), "source digest")

    def test_missing_commit_rejected(self) -> None:
        self.assert_rejected(lambda data: data.update({"sourceCommit": "0" * 40}), "commit object")

    def test_nonancestor_commit_rejected(self) -> None:
        tree = subprocess.run(
            ["git", "rev-parse", "HEAD^{tree}"], cwd=ROOT, check=True, capture_output=True, text=True,
        ).stdout.strip()
        env = os.environ | {
            "GIT_AUTHOR_NAME": "M20 verifier", "GIT_AUTHOR_EMAIL": "m20@example.invalid",
            "GIT_COMMITTER_NAME": "M20 verifier", "GIT_COMMITTER_EMAIL": "m20@example.invalid",
        }
        nonancestor = subprocess.run(
            ["git", "commit-tree", tree, "-m", "M20 nonancestor probe"],
            cwd=ROOT, check=True, capture_output=True, text=True, env=env,
        ).stdout.strip()
        self.assert_rejected(lambda data: data.update({"sourceCommit": nonancestor}), "not an ancestor")

    def test_stale_result_digest_rejected(self) -> None:
        stale = hashlib.sha256(b"stale result").hexdigest()
        self.assert_rejected(lambda data: data["unitResult"].update({"sha256": stale}), "result digest")

    def test_absolute_path_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe path"):
            safe_path("/tmp/m20-absolute-probe")

    def test_traversal_path_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe path"):
            safe_path("tools/evidence/../../outside")

    def test_symlink_path_rejected(self) -> None:
        parent = ROOT / "tools/evidence"
        with tempfile.TemporaryDirectory(prefix="m20-link-", dir=parent) as directory:
            directory_path = Path(directory)
            target = directory_path / "target"
            target.write_text("probe", encoding="utf-8")
            link = directory_path / "link"
            link.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "symlink forbidden"):
                safe_path(link.relative_to(ROOT).as_posix())

    def test_method_claim_rejected(self) -> None:
        def mutation(data):
            data["unitResult"]["methods"][0] = data["unitResult"]["methods"][1]
        self.assert_rejected(mutation, "method")

    def test_outcome_claim_rejected(self) -> None:
        self.assert_rejected(
            lambda data: data["drill"]["outcomes"][0].update({"result": "FORGED_OUTCOME"}),
            "outcome",
        )

    def test_downgrade_outcome_cannot_be_promoted_to_committed(self) -> None:
        def mutation(data):
            downgrade = next(item for item in data["drill"]["outcomes"] if item["scenario"] == "older_version")
            downgrade["result"] = "VERIFIED_UPDATE_COMMITTED"
        self.assert_rejected(mutation, "outcome")

    def test_recovery_time_claim_rejected(self) -> None:
        self.assert_rejected(
            lambda data: data["drill"].update({"recoveryMillis": data["drill"]["recoveryMillis"] + 1}),
            "timing",
        )

    def test_timestamp_claim_rejected(self) -> None:
        self.assert_rejected(lambda data: data["drill"].update({"startedAtUtc": "2026-01-01T00:00:00Z"}), "timing")

    def test_external_lane_cannot_be_promoted(self) -> None:
        self.assert_rejected(lambda data: data["lanes"][-1].update({"status": "PASS"}), "lane")

    def test_safety_boundary_cannot_be_promoted(self) -> None:
        self.assert_rejected(lambda data: data["safety"].update({"olderApkInstalled": True}), "safety|constant")

    def test_follow_up_actions_cannot_change(self) -> None:
        self.assert_rejected(
            lambda data: data["drill"]["followUpActions"].__setitem__(0, "install_older_apk"),
            "follow-up",
        )

    def test_receipt_digest_rejected(self) -> None:
        self.assert_rejected(lambda data: data.update({"receiptDigest": "0" * 64}), "receipt digest")

    def test_sensitive_value_rejected(self) -> None:
        def mutation(data):
            data["limitations"][0] = "M20_SECRET_CANARY"
            data["receiptDigest"] = canonical_digest(data)
        self.assert_rejected(mutation, "prohibited value")


if __name__ == "__main__":
    unittest.main()
