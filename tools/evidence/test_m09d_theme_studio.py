#!/usr/bin/env python3

from __future__ import annotations

import copy
import json
import os
from pathlib import Path
import tempfile
import unittest

from collect_m09d_theme_studio import (
    DIRTY_REVIEW_MODIFIED_PATHS, DIRTY_REVIEW_PATHS, RECEIPT, ROOT,
    atomic_copy_bytes, validate_dirty_review_entries,
)
from strict_json_schema import validate_json_schema
from validate_m09d_theme_studio import validate_data


class M09DThemeStudioReceiptTest(unittest.TestCase):
    def test_dirty_review_scope_rejects_extra_and_status_substitution(self) -> None:
        entries = [
            f"{' M' if path in DIRTY_REVIEW_MODIFIED_PATHS else '??'} {path}"
            for path in sorted(DIRTY_REVIEW_PATHS)
        ]
        validate_dirty_review_entries(entries)
        with self.assertRaises(ValueError):
            validate_dirty_review_entries(entries + ["?? unexpected.txt"])
        changed = list(entries)
        changed[0] = "?? " + changed[0][3:]
        with self.assertRaises(ValueError):
            validate_dirty_review_entries(changed)

    @classmethod
    def setUpClass(cls) -> None:
        cls.receipt = json.loads((ROOT / RECEIPT).read_text(encoding="utf-8"))
        cls.detector = Path(os.environ["IMPECCABLE_DETECTOR_PATH"])

    def test_current_receipt_passes(self) -> None:
        validate_data(copy.deepcopy(self.receipt), self.detector)

    def assert_rejected(self, mutate) -> None:
        value = copy.deepcopy(self.receipt)
        mutate(value)
        with self.assertRaises(Exception):
            validate_data(value, self.detector)

    def test_mutations_fail_closed(self) -> None:
        mutations = (
            lambda value: value["sourceBinding"].update(sourceDiffSha256="0" * 64),
            lambda value: value["sourceBinding"]["sources"][0].update(sha256="0" * 64),
            lambda value: value["targetApk"].update(sha256="0" * 64),
            lambda value: value["testApk"].update(sha256="0" * 64),
            lambda value: value["targetApk"].update(signerSha256="0" * 64),
            lambda value: value["tooling"]["apksigner"].update(sha256="0" * 64),
            lambda value: value["commitChain"].update(artifactCommit="0" * 40),
            lambda value: value["commitChain"]["artifacts"][0].update(sha256="0" * 64),
            lambda value: value["runs"][0]["result"].update(sha256="0" * 64),
            lambda value: value["runs"][1]["deviceInfo"].update(sha256="0" * 64),
            lambda value: value["runs"][0]["textproto"].update(sha256="0" * 64),
            lambda value: value["runs"][1].update(device="pixel6Api35"),
            lambda value: value["runs"][0]["topology"].update(model="Pixel Tablet"),
            lambda value: value["runs"][0]["topology"].update(formFactor="tablet"),
            lambda value: value["runs"][1]["topology"].update(smallestScreenWidthRule="<600dp"),
            lambda value: value["runs"][1]["topology"].update(resultPathRoot=value["runs"][0]["topology"]["resultPathRoot"]),
            lambda value: value["detectorGate"].update(sha256="0" * 64),
            lambda value: value["detectorGate"].update(result=[{"finding": "regression"}]),
            lambda value: value["detectorGate"]["targets"][0].update(sha256="0" * 64),
            lambda value: value["runs"][0]["result"].update(tests=12),
            lambda value: value["runs"][0]["result"]["methods"].pop(),
            lambda value: value["excludedAttempts"][0].update(status="PASS"),
            lambda value: value["excludedAttempts"][0].update(eligibleForPass=True),
            lambda value: value["excludedAttempts"][1]["artifact"].update(sha256="0" * 64),
            lambda value: value.update(extra="not closed"),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                self.assert_rejected(mutate)

    def test_atomic_writer_replaces_complete_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_bytes(b"old")
            atomic_copy_bytes(b"new\n", path)
            self.assertEqual(b"new\n", path.read_bytes())
            self.assertFalse(any(item.name.startswith(".receipt.json.") for item in path.parent.iterdir()))

    def test_phone_and_tablet_artifact_bytes_cannot_be_swapped(self) -> None:
        phone = ROOT / "tasks/test-evidence/m09d-theme-studio/runtime/phone/device-info.pb"
        tablet = ROOT / "tasks/test-evidence/m09d-theme-studio/runtime/tablet/device-info.pb"
        phone_raw, tablet_raw = phone.read_bytes(), tablet.read_bytes()
        try:
            atomic_copy_bytes(tablet_raw, phone)
            atomic_copy_bytes(phone_raw, tablet)
            with self.assertRaises(Exception):
                validate_data(copy.deepcopy(self.receipt), self.detector)
        finally:
            atomic_copy_bytes(phone_raw, phone)
            atomic_copy_bytes(tablet_raw, tablet)

    def test_textproto_device_path_substitution_is_rejected(self) -> None:
        path = ROOT / "tasks/test-evidence/m09d-theme-studio/runtime/phone/test-result.sanitized.textproto"
        original = path.read_bytes()
        substituted = original.replace(b"pixel6Api35", b"m10TabletApi35")
        self.assertNotEqual(original, substituted)
        try:
            atomic_copy_bytes(substituted, path)
            with self.assertRaises(Exception):
                validate_data(copy.deepcopy(self.receipt), self.detector)
        finally:
            atomic_copy_bytes(original, path)

    def test_schema_drift_is_executed_and_rejected(self) -> None:
        schema_path = ROOT / "tools/evidence/schemas/codecks-m09d-theme-studio-v1.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        schema["properties"]["status"]["const"] = "FAIL"
        with self.assertRaises(ValueError):
            validate_json_schema(copy.deepcopy(self.receipt), schema)
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        schema["$defs"]["source"]["unimplementedKeyword"] = True
        with self.assertRaises(ValueError):
            validate_json_schema(copy.deepcopy(self.receipt), schema)


if __name__ == "__main__":
    unittest.main()
