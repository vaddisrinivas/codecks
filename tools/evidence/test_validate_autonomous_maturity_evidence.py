#!/usr/bin/env python3
"""Adversarial mutation tests for the autonomous-maturity evidence validator."""

from __future__ import annotations

import copy
import hashlib
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_TOOLS = ROOT / "tools/evidence"
sys.path.insert(0, str(EVIDENCE_TOOLS))

from validate_autonomous_maturity_evidence import validate_evidence  # noqa: E402


BASELINE_PATH = ROOT / "tasks/test-evidence/autonomous-maturity-m00-baseline.json"
INVENTORY_PATH = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"


class EvidenceMutationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.baseline = json.loads(BASELINE_PATH.read_text())
        self.inventory = json.loads(INVENTORY_PATH.read_text())

    @staticmethod
    def inventory_bytes(inventory: dict) -> bytes:
        return (json.dumps(inventory, indent=2, sort_keys=False) + "\n").encode()

    def sync_inventory_reference(self, baseline: dict, inventory: dict) -> bytes:
        raw = self.inventory_bytes(inventory)
        baseline["source_inventory"]["sha256"] = hashlib.sha256(raw).hexdigest()
        baseline["source_inventory"]["summary"] = copy.deepcopy(inventory["summary"])
        return raw

    def assert_rejected(
        self,
        baseline: dict,
        inventory: dict,
        expected: str,
        *,
        sync_inventory: bool = False,
    ) -> None:
        raw = (
            self.sync_inventory_reference(baseline, inventory)
            if sync_inventory
            else INVENTORY_PATH.read_bytes()
        )
        with self.assertRaisesRegex(ValueError, expected):
            validate_evidence(baseline, inventory, raw)

    def test_empty_required_sections_are_rejected(self) -> None:
        for section in ("source", "release_artifact", "android", "toolchain", "source_inventory"):
            with self.subTest(section=section):
                baseline = copy.deepcopy(self.baseline)
                baseline[section] = {}
                self.assert_rejected(baseline, self.inventory, f"baseline.{section}: missing keys")

    def test_empty_fresh_checks_is_rejected(self) -> None:
        self.baseline["fresh_checks"] = []
        self.assert_rejected(self.baseline, self.inventory, "too few items")

    def test_unknown_baseline_key_is_rejected(self) -> None:
        self.baseline["android"]["invented"] = True
        self.assert_rejected(self.baseline, self.inventory, "baseline.android: unknown key invented")

    def test_unknown_inventory_key_is_rejected(self) -> None:
        self.inventory["files"][0]["invented"] = True
        self.assert_rejected(
            self.baseline, self.inventory, "inventory.files\\[0\\]: unknown key invented", sync_inventory=True
        )

    def test_missing_inventory_method_is_rejected(self) -> None:
        del self.inventory["method"]
        self.assert_rejected(self.baseline, self.inventory, "missing keys \\['method'\\]", sync_inventory=True)

    def test_forged_inventory_method_is_rejected(self) -> None:
        self.inventory["method"]["scope"] = "trust supplied labels"
        self.assert_rejected(self.baseline, self.inventory, "does not match generator method", sync_inventory=True)

    def test_forged_category_is_rejected(self) -> None:
        self.inventory["files"][0]["category"] = "public_production"
        self.assert_rejected(self.baseline, self.inventory, "inventory category mismatch", sync_inventory=True)

    def test_summary_tampering_is_rejected(self) -> None:
        self.inventory["summary"]["by_category"]["tests"] += 1
        self.assert_rejected(self.baseline, self.inventory, "inventory summary mismatch", sync_inventory=True)

    def test_line_count_tampering_is_rejected(self) -> None:
        self.inventory["files"][0]["physical_lines"] += 1
        self.assert_rejected(self.baseline, self.inventory, "inventory line mismatch", sync_inventory=True)

    def test_digest_tampering_is_rejected(self) -> None:
        self.inventory["files"][0]["sha256"] = "0" * 64
        self.assert_rejected(self.baseline, self.inventory, "inventory sha256 mismatch", sync_inventory=True)

    def test_receipt_pass_with_failed_check_is_rejected(self) -> None:
        self.baseline["fresh_checks"][0]["status"] = "FAIL"
        self.baseline["fresh_checks"][0]["exit_code"] = 1
        self.assert_rejected(self.baseline, self.inventory, "cannot be PASS while a fresh check is FAIL")

    def test_not_run_used_as_executed_status_is_rejected(self) -> None:
        self.baseline["fresh_checks"][0]["status"] = "NOT_RUN"
        self.assert_rejected(self.baseline, self.inventory, "value outside enum")

    def test_pass_used_for_not_run_lane_is_rejected(self) -> None:
        self.baseline["not_run"][0]["status"] = "PASS"
        self.assert_rejected(self.baseline, self.inventory, "expected constant 'NOT_RUN'")


if __name__ == "__main__":
    unittest.main()
