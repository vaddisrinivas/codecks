from __future__ import annotations

import copy
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import collect_m24_release_preflight as collector
from collect_m24_release_preflight import classify_android_device
from validate_m24_release_preflight import RECEIPT, _reject_sensitive, validate, validate_receipt_commit
from validate_autonomous_maturity_evidence import validate_schema_node


class FakeSocket:
    def __init__(self, response: bytes):
        self.response = bytearray(response)
        self.sent = b""

    def sendall(self, value: bytes) -> None:
        self.sent += value

    def recv(self, size: int) -> bytes:
        chunk = bytes(self.response[:size])
        del self.response[:size]
        return chunk

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False


class M24PreflightEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt = json.loads(RECEIPT.read_text())
        cls.receipt["generatedAtUtc"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        cls.receipt["provenance"] = {
            "baseCommit": collector.BASE_COMMIT, "implementationCommit": collector.BASE_COMMIT,
            "implementationPaths": [
                {"path": path, "change": "MODIFIED", "baseSha256": "0" * 64,
                 "implementationSha256": "1" * 64, "patchSha256": "2" * 64}
                for path in collector.IMPLEMENTATION_PATHS
            ],
            "implementationDiffSha256": "3" * 64,
            "receiptPath": "tasks/test-evidence/autonomous-maturity-m24-preflight.json",
            "receiptCommitRequired": True,
        }
        adb = cls.receipt["adbClassification"]
        for key in ("listenerPrecheck", "commands", "devicesOutputSha256"):
            adb.pop(key, None)
        adb.update({
            "endpoint": {"host": "127.0.0.1", "port": 5037},
            "protocol": "ADB_SERVER_HOST_PROTOCOL_V1", "request": "host:devices-l",
            "responseSha256": "4" * 64, "adbCliInvoked": False,
        })
        cls.receipt["limitations"][0] = (
            "Read-only ADB classification used one raw host:devices-l server request; no adb CLI, "
            "package query, pull, install, or instrumentation ran."
        )

    def mutated(self, edit) -> Path:
        value = copy.deepcopy(self.receipt)
        edit(value)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "receipt.json"
        path.write_text(json.dumps(value))
        return path

    def receipt_repo(self, *, extra_in_receipt: bool = False, intermediate: bool = False):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        subprocess.run(["git", "init", "-q"], cwd=root, check=True)
        subprocess.run(["git", "config", "user.name", "M24 Test"], cwd=root, check=True)
        subprocess.run(["git", "config", "user.email", "m24@example.invalid"], cwd=root, check=True)
        (root / "implementation.txt").write_text("implementation\n")
        subprocess.run(["git", "add", "implementation.txt"], cwd=root, check=True)
        subprocess.run(["git", "commit", "-qm", "implementation"], cwd=root, check=True)
        implementation = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=root, check=True, capture_output=True, text=True,
        ).stdout.strip()
        if intermediate:
            (root / "intermediate.txt").write_text("intermediate\n")
            subprocess.run(["git", "add", "intermediate.txt"], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "intermediate"], cwd=root, check=True)
        receipt = root / "tasks/test-evidence/autonomous-maturity-m24-preflight.json"
        receipt.parent.mkdir(parents=True)
        receipt.write_text("{}\n")
        subprocess.run(["git", "add", str(receipt.relative_to(root))], cwd=root, check=True)
        if extra_in_receipt:
            (root / "extra.txt").write_text("extra\n")
            subprocess.run(["git", "add", "extra.txt"], cwd=root, check=True)
        subprocess.run(["git", "commit", "-qm", "receipt"], cwd=root, check=True)
        return root, implementation, receipt

    def test_phase_one_refuses_collection_before_implementation_commit(self):
        with self.assertRaisesRegex(ValueError, "commit M24 implementation before collection"):
            collector.collect()

    def test_schema_closes_m16_limitations_and_timestamp_shape(self):
        schema = json.loads(collector.safe_path(
            "tools/evidence/schemas/autonomous-maturity-m24-preflight-v2.schema.json"
        ).read_text())
        for edit in (
            lambda data: data["m16"].update(unreviewed=True),
            lambda data: data["m16"].update(observedHours=1),
            lambda data: data["limitations"].__setitem__(0, "broader claim"),
            lambda data: data.update(generatedAtUtc="2026-08-18 12:00:00"),
            lambda data: data["adbClassification"].update(protocol="ADB_SERVER_HOST_PROTOCOL_V2"),
            lambda data: data["adbClassification"]["endpoint"].update(port=5038),
            lambda data: data["adbClassification"].update(request="host:version"),
        ):
            with self.subTest(edit=edit), self.assertRaises(ValueError):
                candidate = copy.deepcopy(self.receipt)
                edit(candidate)
                validate_schema_node(candidate, schema, schema, "m24-test")

    def test_future_timestamp_is_rejected(self):
        future = (datetime.now(timezone.utc) + timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
        with self.assertRaisesRegex(ValueError, "future"):
            validate(self.mutated(lambda data: data.update(generatedAtUtc=future)), require_receipt_commit=False)

    def test_stale_timestamp_is_rejected(self):
        stale = (datetime.now(timezone.utc) - timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
        with self.assertRaisesRegex(ValueError, "older than 15 minutes"):
            validate(self.mutated(lambda data: data.update(generatedAtUtc=stale)), require_receipt_commit=False)

    def test_provenance_path_and_hash_fabrication_are_rejected(self):
        expected = copy.deepcopy(self.receipt["provenance"])
        for edit in (
            lambda data: data["provenance"]["implementationPaths"][0].update(path="unreviewed.txt"),
            lambda data: data["provenance"]["implementationPaths"][0].update(implementationSha256="f" * 64),
            lambda data: data["provenance"].update(implementationDiffSha256="e" * 64),
        ):
            with self.subTest(edit=edit), patch(
                "validate_m24_release_preflight.implementation_provenance", return_value=expected
            ), self.assertRaisesRegex(ValueError, "provenance changed"):
                validate(self.mutated(edit), require_receipt_commit=False)

    def test_current_uncommitted_receipt_is_not_final(self):
        with patch("validate_m24_release_preflight.implementation_provenance",
                   return_value=self.receipt["provenance"]), self.assertRaisesRegex(ValueError, "parent"):
            validate(self.mutated(lambda data: None), require_receipt_commit=True)

    def test_receipt_commit_exact_head_parent_blob_and_diff(self):
        root, implementation, receipt = self.receipt_repo()
        validate_receipt_commit(implementation, root, receipt)
        receipt.write_text("{\"drift\":true}\n")
        with self.assertRaisesRegex(ValueError, "exact HEAD blob"):
            validate_receipt_commit(implementation, root, receipt)
        subprocess.run(["git", "restore", str(receipt.relative_to(root))], cwd=root, check=True)
        (root / "drift.txt").write_text("uncommitted\n")
        with self.assertRaisesRegex(ValueError, "uncommitted drift"):
            validate_receipt_commit(implementation, root, receipt)

        root, implementation, receipt = self.receipt_repo(extra_in_receipt=True)
        with self.assertRaisesRegex(ValueError, "receipt-only"):
            validate_receipt_commit(implementation, root, receipt)

        root, implementation, receipt = self.receipt_repo(intermediate=True)
        with self.assertRaisesRegex(ValueError, "parent"):
            validate_receipt_commit(implementation, root, receipt)

    def test_verdict_cannot_promote(self):
        with self.assertRaises(ValueError):
            validate(self.mutated(lambda data: data.update(verdict="GO")))

    def test_stale_m21_deferral_is_rejected(self):
        def stale(data):
            data["dependencies"][2]["status"] = "DEFERRED"
            data["m21Closure"]["status"] = "DEFERRED"
        with self.assertRaises(ValueError):
            validate(self.mutated(stale))

    def test_stale_source_commit_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "implementation commit"):
            validate(self.mutated(lambda data: data.update(sourceCommit="0" * 40)))

    def test_candidate_promotion_is_rejected(self):
        def promote(data):
            data["candidate"].update(status="BUILT", artifactPresent=True, sourceSha="a" * 40)
        with self.assertRaises(ValueError):
            validate(self.mutated(promote))

    def test_m23_candidate_promotion_is_rejected(self):
        def promote(data):
            data["m23"].update(status="CANDIDATE", versionAssigned=True, artifactAdmitted=True)
        with self.assertRaises(ValueError):
            validate(self.mutated(promote))

    def test_m16_capacity_or_time_promotion_is_rejected(self):
        for field in ("capacityStatus", "timeStatus"):
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate(self.mutated(lambda data, field=field: data["m16"].update({field: "PASS"})))

    def test_phone_or_installed_identity_promotion_is_rejected(self):
        def promote(data):
            data["phone"].update(status="PASS", physicalDeviceCount=1, installedIdentityStatus="PASS")
        with self.assertRaises(ValueError):
            validate(self.mutated(promote))

    def test_signing_presence_promotion_is_rejected(self):
        def present(data):
            data["signing"].update(status="PRESENT", presentNames=[collector.SIGNING_NAMES[0]])
        with self.assertRaises(ValueError):
            validate(self.mutated(present))

    def test_adb_protocol_expansion_or_raw_serial_is_rejected(self):
        with self.assertRaises(ValueError):
            validate(self.mutated(lambda data: data["adbClassification"].update(request="host:transport-any")))
        with self.assertRaises(ValueError):
            validate(self.mutated(lambda data: data["adbClassification"].update(rawSerial="private")))

    def test_closure_receipt_and_validator_drift_are_rejected(self):
        for closure, field in (("m20Closure", "receiptSha256"), ("m21Closure", "validatorSha256")):
            with self.subTest(closure=closure, field=field), self.assertRaises(ValueError):
                validate(self.mutated(lambda data, closure=closure, field=field: data[closure].update({field: "0" * 64})))

    def test_blocker_removal_is_rejected(self):
        with self.assertRaises(ValueError):
            validate(self.mutated(lambda data: data["blockers"].remove("M16_168H_NOT_RUN")))

    def test_secret_and_dangerous_commands_are_rejected(self):
        for payload in (
            {"rawSerial": "private"}, {"nested": ["password=M24_SECRET_CANARY"]},
            {"nested": ["adb uninstall app.codecks"]}, {"nested": ["pm clear app.codecks"]},
            {"nested": ["adb install candidate.apk"]},
        ):
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                _reject_sensitive(payload)

    def test_device_classifier_fails_closed(self):
        self.assertEqual("EMULATOR", classify_android_device("emulator-5554", {
            "product": "sdk_gphone64_arm64", "model": "sdk_gphone64_arm64",
            "device": "emu64a", "transportId": "1",
        }))
        self.assertEqual("PHYSICAL", classify_android_device("serial", {
            "product": "dm3q", "model": "SM_S918U", "device": "dm3q", "transportId": "2",
        }))
        self.assertEqual("UNKNOWN", classify_android_device("serial", {
            "product": "", "model": "", "device": "", "transportId": "",
        }))

    def test_atomic_failure_preserves_previous_receipt(self):
        with tempfile.TemporaryDirectory() as raw:
            path = Path(raw) / "receipt.json"
            path.write_bytes(b"previous\n")
            with patch.object(collector.os, "replace", side_effect=OSError("interrupted")):
                with self.assertRaisesRegex(OSError, "interrupted"):
                    collector.atomic_write(path, b"new\n")
            self.assertEqual(b"previous\n", path.read_bytes())
            self.assertEqual([path], list(Path(raw).iterdir()))

    def test_atomic_write_preserves_ownership_mode_and_fsyncs_parent(self):
        with tempfile.TemporaryDirectory() as raw:
            path = Path(raw) / "receipt.json"
            path.write_bytes(b"previous\n")
            path.chmod(0o640)
            before = path.stat()
            real_fsync = collector.os.fsync
            with patch.object(collector.os, "fsync", wraps=real_fsync) as fsync:
                collector.atomic_write(path, b"new\n")
            after = path.stat()
            self.assertEqual((before.st_uid, before.st_gid, stat.S_IMODE(before.st_mode)),
                             (after.st_uid, after.st_gid, stat.S_IMODE(after.st_mode)))
            self.assertGreaterEqual(fsync.call_count, 2)

    def test_adb_listener_and_device_states_fail_closed(self):
        with patch.object(collector.socket, "create_connection", side_effect=OSError("closed")), \
             self.assertRaisesRegex(ValueError, "no adb CLI"):
            collector.adb_classification()

        for raw, message in (
            (b"NOPE", "protocol/version"),
            (b"FAIL0004nope", "rejected"),
            (b"OKAYzzzz", "length is malformed"),
            (b"OKAY0005abc", "declared length"),
        ):
            with self.subTest(raw=raw), patch.object(
                collector.socket, "create_connection", return_value=FakeSocket(raw)
            ), self.assertRaisesRegex(ValueError, message):
                collector.adb_classification()

        for state in ("offline", "unauthorized"):
            payload = f"serial\t{state} product:x model:x device:x transport_id:1\n".encode()
            raw = b"OKAY" + f"{len(payload):04x}".encode() + payload
            with self.subTest(state=state), patch.object(
                collector.socket, "create_connection", return_value=FakeSocket(raw)
            ), self.assertRaisesRegex(ValueError, "not eligible"):
                collector.adb_classification()

        payload = b"serial\tdevice transport_id:1\n"
        raw = b"OKAY" + f"{len(payload):04x}".encode() + payload
        with patch.object(collector.socket, "create_connection", return_value=FakeSocket(raw)), \
             self.assertRaisesRegex(ValueError, "metadata is incomplete"):
            collector.adb_classification()

        connection = FakeSocket(b"OKAY0000")
        with patch.object(collector.socket, "create_connection", return_value=connection):
            result = collector.adb_classification()
        self.assertEqual(b"000ehost:devices-l", connection.sent)
        self.assertEqual([], result["devices"])
        self.assertFalse(result["adbCliInvoked"])


if __name__ == "__main__":
    unittest.main()
