#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from generate_m09_current_source_census import OUTPUT, canonical_digest
from validate_m09_current_source_census import validate


class M09CurrentSourceCensusTest(unittest.TestCase):
    def rejected(self, mutate, pattern: str, recompute: bool = False) -> None:
        data = json.loads(OUTPUT.read_text())
        mutate(data)
        if recompute:
            data["receiptDigest"] = canonical_digest(data)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, pattern):
                validate(path)

    def test_current_receipt_passes(self) -> None:
        validate()

    def test_source_digest_mutation_fails(self) -> None:
        self.rejected(lambda d: d["files"][0].update({"sha256": "0" * 64}), "deterministic")

    def test_excess_mutation_fails_even_with_digest(self) -> None:
        self.rejected(lambda d: d["publicProduction"].update({"excessLines": 1}), "deterministic", True)

    def test_missing_owner_reason_fails_even_with_digest(self) -> None:
        self.rejected(lambda d: d["publicProduction"]["featureOwnerReasons"].pop(next(iter(d["publicProduction"]["featureOwnerReasons"]))), "deterministic", True)

    def test_baseline_digest_mutation_fails(self) -> None:
        self.rejected(lambda d: d["baseline"].update({"inventorySha256": "0" * 64}), "deterministic")

    def test_runtime_boundary_cannot_be_promoted(self) -> None:
        self.rejected(lambda d: d["boundaries"].update({"deviceMeasured": True}), "constant|deterministic")


if __name__ == "__main__":
    unittest.main()
