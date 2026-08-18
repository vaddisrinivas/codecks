#!/usr/bin/env python3
from __future__ import annotations

import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from collect_m19_diagnostics_support import canonical_digest
from validate_m19_diagnostics_support import RECEIPT, ROOT, safe_path, validate


class M19EvidenceTest(unittest.TestCase):
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
        self.assert_rejected(lambda data: data["summary"].update({"pass": "5"}))

    def test_stale_source_artifact_digest_rejected(self) -> None:
        stale = hashlib.sha256(b"stale source artifact").hexdigest()
        self.assert_rejected(lambda data: data["sources"][0].update({"sha256": stale}), "source digest")

    def test_managed_source_diff_digest_rejected(self) -> None:
        def mutate(data):
            data["managedResult"]["sourceDiffSha256"] = "0" * 64
            data["receiptDigest"] = canonical_digest(data)
        self.assert_rejected(
            mutate,
            "binding mismatch",
        )

    def test_missing_commit_rejected(self) -> None:
        self.assert_rejected(lambda data: data.update({"sourceCommit": "0" * 40}), "commit object")

    def test_stale_live_result_artifact_digest_rejected(self) -> None:
        stale = hashlib.sha256(b"stale live result artifact").hexdigest()
        self.assert_rejected(lambda data: data["unitResult"].update({"sha256": stale}), "result digest")

    def test_absolute_path_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe path"):
            safe_path("/tmp/m19-absolute-probe")

    def test_traversal_path_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe path"):
            safe_path("tools/evidence/../../outside")

    def test_symlink_path_rejected(self) -> None:
        parent = ROOT / "tools/evidence"
        with tempfile.TemporaryDirectory(prefix="m19-link-", dir=parent) as directory:
            directory_path = Path(directory)
            target = directory_path / "target"
            target.write_text("probe", encoding="utf-8")
            link = directory_path / "link"
            link.symlink_to(target)
            relative = link.relative_to(ROOT).as_posix()
            with self.assertRaisesRegex(ValueError, "symlink forbidden"):
                safe_path(relative)

    def test_exact_nonancestor_commit_rejected(self) -> None:
        tree = subprocess.run(
            ["git", "rev-parse", "HEAD^{tree}"], cwd=ROOT, check=True, capture_output=True, text=True,
        ).stdout.strip()
        env = os.environ | {
            "GIT_AUTHOR_NAME": "M19 verifier", "GIT_AUTHOR_EMAIL": "m19@example.invalid",
            "GIT_COMMITTER_NAME": "M19 verifier", "GIT_COMMITTER_EMAIL": "m19@example.invalid",
        }
        nonancestor = subprocess.run(
            ["git", "commit-tree", tree, "-m", "M19 nonancestor probe"],
            cwd=ROOT, check=True, capture_output=True, text=True, env=env,
        ).stdout.strip()
        self.assert_rejected(lambda data: data.update({"sourceCommit": nonancestor}), "not an ancestor")

    def test_method_claim_rejected(self) -> None:
        def mutation(data):
            data["unitResult"]["methods"][0] = data["unitResult"]["methods"][1]
        self.assert_rejected(mutation, "methods|method")

    def test_external_lane_cannot_be_promoted(self) -> None:
        self.assert_rejected(lambda data: data["lanes"][-1].update({"status": "PASS"}), "lane")

    def test_privacy_claim_cannot_be_promoted(self) -> None:
        self.assert_rejected(lambda data: data["privacy"].update({"rawContentRecorded": True}), "privacy|constant")

    def test_receipt_digest_rejected(self) -> None:
        self.assert_rejected(lambda data: data.update({"receiptDigest": "0" * 64}), "receipt digest")

    def test_sensitive_keys_rejected(self) -> None:
        for key in ("password", "hostname", "username", "fingerprint", "clipboardText", "prompt", "accountId"):
            with self.subTest(key=key):
                def mutation(data, field=key):
                    data["sources"][0][field] = "M19_CONTENT_CANARY"
                self.assert_rejected(mutation)

    def test_sensitive_values_rejected(self) -> None:
        canaries = (
            "password=M19_SECRET_CANARY", "token=M19_SECRET_CANARY", "hostname=private.example",
            "username=private-user", "private-user@example.com", "192.0.2.10",
            "-----BEGIN OPENSSH " + "PRIVATE KEY-----", "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5",
            "clipboard=M19_CONTENT_CANARY", "account_id=M19_IDENTITY_CANARY",
        )
        for canary in canaries:
            with self.subTest(canary=canary):
                def mutation(data, value=canary):
                    data["limitations"][0] = value
                    data["receiptDigest"] = canonical_digest(data)
                self.assert_rejected(mutation)

    def test_unbounded_collection_rejected(self) -> None:
        def mutation(data):
            data["lanes"] = copy.deepcopy(data["lanes"]) * 2
        self.assert_rejected(mutation)

    def test_raw_production_support_code_literal_rejected(self) -> None:
        probe = ROOT / "app/src/main/java/io/codecks/M19RawSupportCodeProbe.kt"
        probe.write_text('package io.codecks\nval probe = "CX-HID-FAIL"\n', encoding="utf-8")
        self.addCleanup(probe.unlink, missing_ok=True)
        with self.assertRaisesRegex(ValueError, "raw production support-code literal"):
            validate()

    def test_managed_source_target_test_xml_and_device_mutations_fail_closed(self) -> None:
        mutations = (
            lambda data: data["managedResult"]["sources"][0].update({"sha256": "0" * 64}),
            lambda data: data["managedResult"]["targetApk"].update({"sha256": "0" * 64}),
            lambda data: data["managedResult"]["testApk"].update({"sha256": "0" * 64}),
            lambda data: data["managedResult"]["result"].update({"sha256": "0" * 64}),
            lambda data: data["managedResult"]["device"]["properties"].update({"device": "physical"}),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                def sealed(data, change=mutation):
                    change(data)
                    data["receiptDigest"] = canonical_digest(data)
                self.assert_rejected(sealed, "binding mismatch")


if __name__ == "__main__":
    unittest.main()
