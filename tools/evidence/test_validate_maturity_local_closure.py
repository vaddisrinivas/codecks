import copy
import json
import unittest
from pathlib import Path
from unittest.mock import patch
import tempfile

import collect_maturity_local_closure as collector
import generate_autonomous_maturity_source_inventory as inventory
import validate_maturity_local_closure as validator


class LocalClosureNegativeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.todo = validator.TODO.read_text()
        cls.metrics = validator.METRICS.read_text()
        cls.census = json.loads(validator.CENSUS.read_text())
        cls.baseline = json.loads(validator.BASELINE.read_text())

    def test_current_metrics_pass(self):
        validator.validate_metrics(self.metrics, self.census, self.baseline)

    def test_metric_mutations_fail(self):
        for value in (
            self.metrics.replace("60,270", "60,269"),
            self.metrics.replace("`NOT_RUN`", "`PASS`"),
            self.metrics.replace("| Production dependency declarations | 22 | 22 |", "| Production dependency declarations | 21 | 22 |"),
            self.metrics.replace("not startup, APK, device, signer, candidate, or release evidence", "release evidence"),
        ):
            with self.assertRaises(ValueError):
                validator.validate_metrics(value, self.census, self.baseline)

    def test_derived_census_mutation_fails(self):
        changed = copy.deepcopy(self.census)
        changed["publicProduction"]["excessLines"] += 1
        with self.assertRaises(ValueError):
            validator.validate_metrics(self.metrics, changed, self.baseline)

    def test_todo_external_promotions_fail(self):
        validator.validate_todo(self.todo)
        for claim in ("M09D pass the same lifecycle on a live provider", "M15 run foreground/background", "M20 verify real release-key custody"):
            changed = self.todo.replace(f"- [ ] {claim}", f"- [x] {claim}")
            with self.assertRaises(ValueError):
                validator.validate_todo(changed)

    def test_platform_semantic_sources_pass(self):
        validator.validate_semantic_platform_split()

    def test_receipt_mutations_fail(self):
        value = json.loads(validator.RECEIPT.read_text())
        validator.validate_local_receipt(value)
        mutations = []
        for edit in (
            lambda x: x["lanes"][4].update(status="PASS"),
            lambda x: x["sources"][0].update(sha256="0" * 64),
            lambda x: x["validations"][0].update(stdoutSha256="0" * 64),
            lambda x: x["m20DisposableSigner"]["apksigner"].update(sdkRelativePath="build-tools/35.0.0/apksigner"),
            lambda x: x["m20DisposableSigner"].update(differentSignerCertificateSha256=x["m20DisposableSigner"]["sameSignerCertificateSha256"]),
            lambda x: x.update(status="PASS"),
            lambda x: x.update(extra=True),
        ):
            changed = copy.deepcopy(value)
            edit(changed)
            mutations.append(changed)
        for changed in mutations:
            with self.assertRaises(ValueError):
                validator.validate_local_receipt(changed)

    def test_arbitrary_signer_hex_with_recomputed_receipt_digest_fails(self):
        value = json.loads(validator.RECEIPT.read_text())
        value["m20DisposableSigner"]["sameSignerCertificateSha256"] = "a" * 64
        value["receiptDigest"] = validator.digest(value)
        with self.assertRaisesRegex(ValueError, "retained signer digest binding"):
            validator.validate_local_receipt(value, value["validations"])

    def test_receipt_atomic_failure_preserves_previous_bytes(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            output = root / "receipt.json"
            output.write_bytes(b"previous\n")
            with patch.object(inventory.os, "replace", side_effect=OSError("interrupted")):
                with self.assertRaisesRegex(OSError, "interrupted"):
                    collector.write_receipt(output, {"new": True}, root)
            self.assertEqual(b"previous\n", output.read_bytes())
            self.assertEqual([output], list(root.iterdir()))

    def test_preexisting_pid_temp_is_not_deleted(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            output = root / "receipt.json"
            temporary = root / f".receipt.json.{inventory.os.getpid()}.tmp"
            temporary.write_bytes(b"not-owned\n")
            with self.assertRaises(FileExistsError):
                collector.write_receipt(output, {"new": True}, root)
            self.assertEqual(b"not-owned\n", temporary.read_bytes())

    def test_receipt_atomic_write_fsyncs_file_and_same_directory(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            output = root / "receipt.json"
            calls = []
            original_fsync = inventory.os.fsync
            original_replace = inventory.os.replace

            def record_fsync(descriptor):
                calls.append(("fsync", descriptor))
                return original_fsync(descriptor)

            def record_replace(source, target, *, src_dir_fd, dst_dir_fd):
                calls.append(("replace", source, target, src_dir_fd, dst_dir_fd))
                return original_replace(source, target, src_dir_fd=src_dir_fd, dst_dir_fd=dst_dir_fd)

            with patch.object(inventory.os, "fsync", side_effect=record_fsync), patch.object(inventory.os, "replace", side_effect=record_replace):
                collector.write_receipt(output, {"new": True}, root)
            self.assertGreaterEqual(sum(call[0] == "fsync" for call in calls), 2)
            replacement = next(call for call in calls if call[0] == "replace")
            self.assertEqual(replacement[3], replacement[4])
            self.assertEqual({"new": True}, json.loads(output.read_text()))


if __name__ == "__main__":
    unittest.main()
