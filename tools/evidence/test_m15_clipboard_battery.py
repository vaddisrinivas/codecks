#!/usr/bin/env python3
from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from validate_m15_clipboard_battery import RECEIPT, validate


class M15EvidenceTest(unittest.TestCase):
    def mutated_receipt(self, mutation) -> Path:
        data = json.loads(RECEIPT.read_text(encoding="utf-8"))
        mutation(data)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "receipt.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def test_current_receipt_is_closed(self) -> None:
        validate()

    def test_unknown_key_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data.update({"unexpected": True}))
        with self.assertRaises(ValueError):
            validate(path)

    def test_source_digest_mismatch_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data["sources"][0].update({"sha256": "0" * 64}))
        with self.assertRaisesRegex(ValueError, "source digest"):
            validate(path)

    def test_wrong_commit_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data.update({"sourceCommit": "0" * 40}))
        with self.assertRaisesRegex(ValueError, "source commit"):
            validate(path)

    def test_runtime_lane_cannot_be_promoted(self) -> None:
        def mutation(data):
            data["lanes"][-1]["status"] = "PASS"
        path = self.mutated_receipt(mutation)
        with self.assertRaisesRegex(ValueError, "lane status"):
            validate(path)

    def test_managed_digest_mismatch_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data["managedResult"].update({"sha256": "0" * 64}))
        with self.assertRaisesRegex(ValueError, "managed result digest"):
            validate(path)

    def test_privacy_flag_cannot_be_promoted(self) -> None:
        path = self.mutated_receipt(lambda data: data["privacy"].update({"clipboardContentRecorded": True}))
        with self.assertRaisesRegex(ValueError, "privacy|constant"):
            validate(path)

    def test_duplicate_method_is_rejected(self) -> None:
        def mutation(data):
            data["unitResult"]["methods"][0] = data["unitResult"]["methods"][1]
        path = self.mutated_receipt(mutation)
        with self.assertRaisesRegex(ValueError, "methods"):
            validate(path)

    def test_prohibited_sensitive_key_is_rejected(self) -> None:
        def mutation(data):
            data["limitations"] = copy.deepcopy(data["limitations"])
            data["sources"][0]["clipboardContent"] = "canary"
        path = self.mutated_receipt(mutation)
        with self.assertRaises(ValueError):
            validate(path)

    def test_secret_assignment_in_limitations_is_rejected(self) -> None:
        def mutation(data):
            data["limitations"][0] = "password=M15_SECRET_CANARY"
        path = self.mutated_receipt(mutation)
        with self.assertRaisesRegex(ValueError, "prohibited receipt value"):
            validate(path)

    def test_secret_host_user_and_key_value_canaries_are_rejected(self) -> None:
        canaries = (
            "token=M15_SECRET_CANARY",
            "credential: M15_SECRET_CANARY",
            "api_key=M15_SECRET_CANARY",
            "hostname=private.example",
            "username=private-user",
            "private-user@example.com",
            "192.0.2.10",
            "-----BEGIN OPENSSH PRIVATE KEY-----",
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5",
        )
        for canary in canaries:
            with self.subTest(canary=canary):
                def mutation(data, value=canary):
                    data["limitations"][0] = value
                path = self.mutated_receipt(mutation)
                with self.assertRaisesRegex(ValueError, "prohibited receipt value"):
                    validate(path)

    def test_managed_scope_cannot_claim_binary_binding(self) -> None:
        def mutation(data):
            data["managedResult"]["executedBinaryBinding"] = "APK_DIGESTS_BOUND"
        path = self.mutated_receipt(mutation)
        with self.assertRaises(ValueError):
            validate(path)


if __name__ == "__main__":
    unittest.main()
