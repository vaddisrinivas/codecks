#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from validate_m18_automation_proof import RECEIPT, validate


class M18EvidenceTest(unittest.TestCase):
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
        with self.assertRaises(ValueError):
            validate(self.mutated_receipt(lambda data: data.update({"unexpected": True})))

    def test_source_digest_mismatch_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data["sources"][0].update({"sha256": "0" * 64}))
        with self.assertRaisesRegex(ValueError, "source digest"):
            validate(path)

    def test_every_direct_policy_source_digest_is_enforced(self) -> None:
        policy_sources = {
            "app/src/main/java/io/codecks/core/actions/ActionContracts.kt",
            "app/src/main/java/io/codecks/core/actions/CommandRevision.kt",
            "app/src/main/java/io/codecks/core/actions/RawCommandPolicy.kt",
            "app/src/main/java/io/codecks/domain/CommandTrust.kt",
        }
        receipt = json.loads(RECEIPT.read_text(encoding="utf-8"))
        bound = {item["path"] for item in receipt["sources"]}
        self.assertTrue(policy_sources <= bound)
        for source in sorted(policy_sources):
            with self.subTest(source=source):
                def mutate(data, target=source):
                    next(item for item in data["sources"] if item["path"] == target)["sha256"] = "0" * 64
                with self.assertRaisesRegex(ValueError, "source digest"):
                    validate(self.mutated_receipt(mutate))

    def test_result_digest_mismatch_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data["unitResult"].update({"sha256": "0" * 64}))
        with self.assertRaisesRegex(ValueError, "result digest"):
            validate(path)

    def test_missing_commit_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data.update({"sourceCommit": "0" * 40}))
        with self.assertRaisesRegex(ValueError, "source commit object is missing"):
            validate(path)

    def test_runtime_lane_cannot_be_promoted(self) -> None:
        path = self.mutated_receipt(lambda data: data["lanes"][-1].update({"status": "PASS"}))
        with self.assertRaisesRegex(ValueError, "lane status"):
            validate(path)

    def test_pass_lane_cannot_be_demoted(self) -> None:
        path = self.mutated_receipt(lambda data: data["lanes"][0].update({"status": "NOT_RUN"}))
        with self.assertRaisesRegex(ValueError, "lane status"):
            validate(path)

    def test_privacy_flag_cannot_be_promoted(self) -> None:
        path = self.mutated_receipt(lambda data: data["privacy"].update({"commandContentRecorded": True}))
        with self.assertRaises(ValueError):
            validate(path)

    def test_duplicate_method_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data["unitResult"]["methods"].__setitem__(0, data["unitResult"]["methods"][1]))
        with self.assertRaisesRegex(ValueError, "methods"):
            validate(path)

    def test_secret_canary_is_rejected(self) -> None:
        path = self.mutated_receipt(lambda data: data["limitations"].__setitem__(0, "password=M18_SECRET_CANARY"))
        with self.assertRaisesRegex(ValueError, "prohibited receipt value"):
            validate(path)


if __name__ == "__main__":
    unittest.main()
