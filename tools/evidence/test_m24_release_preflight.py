#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from collect_m24_release_preflight import classify_android_device
from validate_m24_release_preflight import RECEIPT, _reject_sensitive, validate


class M24PreflightEvidenceTest(unittest.TestCase):
    def mutated(self, mutation) -> Path:
        data = json.loads(RECEIPT.read_text(encoding="utf-8"))
        mutation(data)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "receipt.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def test_current_no_go_receipt_is_closed(self) -> None:
        validate()

    def test_go_promotion_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            validate(self.mutated(lambda data: data.update({"verdict": "GO"})))

    def test_absent_phone_cannot_claim_pass(self) -> None:
        with self.assertRaises(ValueError):
            validate(self.mutated(lambda data: data["connectedDevice"].update({"status": "PASS"})))

    def test_deferred_candidate_cannot_claim_digest(self) -> None:
        with self.assertRaisesRegex(ValueError, "candidate"):
            validate(self.mutated(lambda data: data["candidate"].update({"apkSha256": "a" * 64})))

    def test_public_signer_drift_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "signer baseline"):
            validate(self.mutated(lambda data: data["publishedBaseline"].update({"signerSha256": "a" * 64})))

    def test_source_drift_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "source digest"):
            validate(self.mutated(lambda data: data["sources"][0].update({"sha256": "a" * 64})))

    def test_missing_blocker_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "blocker"):
            validate(self.mutated(lambda data: data["blockers"].pop()))

    def test_install_authorization_cannot_be_forged(self) -> None:
        with self.assertRaises(ValueError):
            validate(self.mutated(lambda data: data["safeInPlaceUpdate"].update({"authorizedNow": True})))

    def test_destructive_forbidden_action_cannot_be_removed(self) -> None:
        def remove_clear(data):
            data["safeInPlaceUpdate"]["forbidden"].remove("CLEAR_PROTECTED_PACKAGE_DATA")
        with self.assertRaises(ValueError):
            validate(self.mutated(remove_clear))

    def test_connected_device_raw_serial_field_is_rejected(self) -> None:
        def add_raw_serial(data):
            data["connectedDevice"]["rawSerial"] = "serial-value"
        with self.assertRaises(ValueError):
            validate(self.mutated(add_raw_serial))

    def test_arbitrary_update_field_is_rejected(self) -> None:
        def add_update_field(data):
            data["safeInPlaceUpdate"]["command"] = "do something"
        with self.assertRaises(ValueError):
            validate(self.mutated(add_update_field))

    def test_required_update_order_is_immutable(self) -> None:
        def reorder(data):
            order = data["safeInPlaceUpdate"]["requiredOrder"]
            order[3], order[4] = order[4], order[3]
        with self.assertRaisesRegex(ValueError, "required update order"):
            validate(self.mutated(reorder))

    def test_gate_code_must_match_status_and_identity(self) -> None:
        def change_code(data):
            data["gates"][1]["code"] = "single_physical"
        with self.assertRaisesRegex(ValueError, "gate id/status/code"):
            validate(self.mutated(change_code))

    def test_stale_m23_not_complete_gate_code_is_rejected(self) -> None:
        def stale_code(data):
            data["gates"][7]["code"] = "m23_not_complete"
        with self.assertRaisesRegex(ValueError, "gate id/status/code"):
            validate(self.mutated(stale_code))

    def test_stale_m23_gate_name_is_rejected(self) -> None:
        def stale_name(data):
            data["gates"][7]["id"] = "candidate.m23_complete"
        with self.assertRaisesRegex(ValueError, "gate id/status/code"):
            validate(self.mutated(stale_name))

    def test_stale_m23_update_step_is_rejected(self) -> None:
        def stale_step(data):
            data["safeInPlaceUpdate"]["requiredOrder"][2] = "bind_exact_m23_candidate_source_and_checksum"
        with self.assertRaisesRegex(ValueError, "required update order"):
            validate(self.mutated(stale_step))

    def test_old_candidate_reason_is_rejected(self) -> None:
        def old_reason(data):
            data["candidate"]["reason"] = "M23_NOT_COMPLETE"
        with self.assertRaises(ValueError):
            validate(self.mutated(old_reason))

    def test_m20_cannot_revert_to_pending(self) -> None:
        def pending(data):
            data["dependencies"][0]["status"] = "PENDING"
        with self.assertRaises(ValueError):
            validate(self.mutated(pending))

    def test_m20_receipt_digest_drift_is_rejected(self) -> None:
        def drift(data):
            data["m20Closure"]["receiptSha256"] = "a" * 64
        with self.assertRaisesRegex(ValueError, "M20 closure binding"):
            validate(self.mutated(drift))

    def test_m20_validator_digest_drift_is_rejected(self) -> None:
        def drift(data):
            data["m20Closure"]["validatorSha256"] = "a" * 64
        with self.assertRaisesRegex(ValueError, "M20 closure binding"):
            validate(self.mutated(drift))

    def test_m20_integration_commit_drift_is_rejected(self) -> None:
        def drift(data):
            data["m20Closure"]["integrationCommit"] = "a" * 40
        with self.assertRaises(ValueError):
            validate(self.mutated(drift))

    def test_deferred_m21_m23_are_not_blockers(self) -> None:
        def wrong_blocker(data):
            data["blockers"][0] = "M23_DEFERRED"
        with self.assertRaises(ValueError):
            validate(self.mutated(wrong_blocker))

    def test_recursive_secret_and_dangerous_command_rejection(self) -> None:
        for payload in (
            {"nested": [{"rawSerial": "private"}]},
            {"nested": ["password=M24_SECRET_CANARY"]},
            {"nested": ["adb uninstall app.codecks"]},
            {"nested": ["pm clear app.codecks"]},
            {"nested": ["adb install -d candidate.apk"]},
        ):
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                _reject_sensitive(payload)

    def test_device_classifier_uses_read_only_properties_fail_closed(self) -> None:
        self.assertEqual("EMULATOR", classify_android_device({
            "ro.kernel.qemu": "1", "ro.build.characteristics": "phone", "ro.product.model": "Pixel",
        }))
        self.assertEqual("EMULATOR", classify_android_device({
            "ro.kernel.qemu": "0", "ro.build.characteristics": "emulator", "ro.product.model": "Pixel",
        }))
        self.assertEqual("EMULATOR", classify_android_device({
            "ro.kernel.qemu": "0", "ro.build.characteristics": "phone", "ro.product.model": "sdk_gphone64_arm64",
        }))
        self.assertEqual("PHYSICAL", classify_android_device({
            "ro.kernel.qemu": "0", "ro.build.characteristics": "phone", "ro.product.model": "SM-S918U",
        }))
        self.assertEqual("UNKNOWN", classify_android_device({
            "ro.kernel.qemu": "", "ro.build.characteristics": "", "ro.product.model": "",
        }))

    def test_secret_canary_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            validate(self.mutated(lambda data: data["blockers"].__setitem__(0, "password=M24_SECRET_CANARY")))


if __name__ == "__main__":
    unittest.main()
