#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from collect_pairing_phase_ab_evidence import canonical_digest, safe_path
from validate_pairing_phase_ab_evidence import RECEIPT, validate


class PairingPhaseAbEvidenceTest(unittest.TestCase):
    def rejected(self, mutation, pattern: str) -> None:
        data = json.loads(RECEIPT.read_text(encoding="utf-8"))
        mutation(data)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, pattern):
                validate(path)

    def test_current_receipt_passes(self) -> None:
        validate()

    def test_unknown_key_rejected(self) -> None:
        self.rejected(lambda data: data.update({"unknown": True}), "unknown key|keys")

    def test_stale_source_rejected(self) -> None:
        self.rejected(lambda data: data["sources"][0].update({"sha256": hashlib.sha256(b"stale").hexdigest()}), "source digest")

    def test_source_commit_cannot_float(self) -> None:
        self.rejected(lambda data: data["sources"][0].update({"commit": data["phaseCommits"]["phaseA"]}), "source commit")

    def test_execution_result_cannot_be_promoted_or_replaced(self) -> None:
        self.rejected(lambda data: data["executions"][0].update({"tests": 54}), "execution receipt")

    def test_phase_commit_rejected(self) -> None:
        self.rejected(lambda data: data["phaseCommits"].update({"phaseA": "0" * 40}), "phase commit")

    def test_runtime_lane_cannot_be_promoted(self) -> None:
        self.rejected(lambda data: data["lanes"][-1].update({"status": "PASS"}), "lane claim")

    def test_signed_boundary_cannot_be_promoted(self) -> None:
        self.rejected(lambda data: data["boundaries"].update({"signedArtifactBuilt": True}), "constant|boundary")

    def test_encryption_boundary_cannot_be_promoted(self) -> None:
        self.rejected(lambda data: data["boundaries"].update({"tcpPayloadEncrypted": True}), "constant|boundary")

    def test_receipt_digest_rejected(self) -> None:
        self.rejected(lambda data: data.update({"receiptDigest": "0" * 64}), "receipt digest")

    def test_absolute_path_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe path"):
            safe_path("/tmp/pairing-proof")

    def test_changed_limitation_with_recomputed_digest_rejected(self) -> None:
        def mutation(data):
            data["limitations"].pop()
            data["receiptDigest"] = canonical_digest(data)
        self.rejected(mutation, "minimum|limitations")


if __name__ == "__main__":
    unittest.main()
