#!/usr/bin/env python3
from __future__ import annotations

import json
import copy
import hashlib
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from contextlib import contextmanager
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

from collect_m11_dex_proxy import ROOT
from validate_m11_dex_proxy import RECEIPT, validate


class M11ReceiptTest(unittest.TestCase):
    def mutated_receipt(self, mutation) -> Path:
        data = json.loads(RECEIPT.read_text())
        mutation(data)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "receipt.json"
        path.write_text(json.dumps(data))
        return path

    @contextmanager
    def mutated_xml(self, artifact_index: int = 0):
        data = json.loads(RECEIPT.read_text())
        artifact = data["artifacts"][artifact_index]
        source = ROOT / artifact["path"]
        with tempfile.TemporaryDirectory(dir=ROOT / "tasks/test-evidence") as directory:
            target = Path(directory) / source.name
            target.write_bytes(source.read_bytes())
            artifact["path"] = str(target.relative_to(ROOT))
            receipt = target.parent / "receipt.json"
            yield data, artifact, target, receipt

    def seal_mutation(self, data: dict, artifact: dict, target: Path, receipt: Path) -> None:
        artifact["sha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
        receipt.write_text(json.dumps(data))

    def test_current_receipt_is_closed(self) -> None:
        if RECEIPT.exists():
            validate()

    def test_physical_dex_pass_is_rejected(self) -> None:
        if not RECEIPT.exists():
            self.skipTest("runtime receipt not collected")
        data = json.loads(RECEIPT.read_text())
        data["external"][0]["status"] = "PASS"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "NOT_RUN"):
                validate(path)

    def test_stale_runtime_digest_is_rejected(self) -> None:
        if not RECEIPT.exists():
            self.skipTest("runtime receipt not collected")
        data = json.loads(RECEIPT.read_text())
        data["artifacts"][0]["sha256"] = "0" * 64
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "digest"):
                validate(path)

    def test_duplicate_rerun_method_is_rejected(self) -> None:
        if not RECEIPT.exists():
            self.skipTest("runtime receipt not collected")
        data = json.loads(RECEIPT.read_text())
        if len(data["artifacts"]) < 2:
            self.skipTest("receipt came from one full run")
        data["artifacts"][1]["passedMethods"][0] = data["artifacts"][0]["passedMethods"][0]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "duplicated"):
                validate(path)

    def test_schema_unknown_key_is_rejected(self) -> None:
        data = json.loads(RECEIPT.read_text())
        data["unexpected"] = True
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "unknown key"):
                validate(path)

    def test_wrong_testcase_classname_is_rejected(self) -> None:
        with self.mutated_xml() as (data, artifact, target, receipt):
            tree = ET.parse(target)
            tree.getroot().find(".//testcase").set("classname", "wrong.Class")
            tree.write(target, encoding="utf-8", xml_declaration=True)
            self.seal_mutation(data, artifact, target, receipt)
            with self.assertRaisesRegex(ValueError, "testcase classname"):
                validate(receipt)

    def test_wrong_suite_classname_is_rejected(self) -> None:
        with self.mutated_xml() as (data, artifact, target, receipt):
            tree = ET.parse(target)
            tree.getroot().find("testsuite").set("name", "wrong.Suite")
            tree.write(target, encoding="utf-8", xml_declaration=True)
            self.seal_mutation(data, artifact, target, receipt)
            with self.assertRaisesRegex(ValueError, "suite classname"):
                validate(receipt)

    def test_wrong_device_property_is_rejected(self) -> None:
        with self.mutated_xml() as (data, artifact, target, receipt):
            tree = ET.parse(target)
            node = next(item for item in tree.getroot().findall(".//property") if item.get("name") == "device")
            node.set("value", "untrustedDevice")
            tree.write(target, encoding="utf-8", xml_declaration=True)
            self.seal_mutation(data, artifact, target, receipt)
            with self.assertRaisesRegex(ValueError, "device/flavor/project"):
                validate(receipt)

    def test_duplicate_testcase_name_is_rejected(self) -> None:
        with self.mutated_xml() as (data, artifact, target, receipt):
            tree = ET.parse(target)
            suite = tree.getroot().find("testsuite")
            suite.append(copy.deepcopy(suite.find("testcase")))
            tree.write(target, encoding="utf-8", xml_declaration=True)
            self.seal_mutation(data, artifact, target, receipt)
            with self.assertRaisesRegex(ValueError, "duplicate testcase"):
                validate(receipt)

    def test_non_chronological_rerun_is_rejected(self) -> None:
        if len(json.loads(RECEIPT.read_text())["artifacts"]) < 2:
            self.skipTest("receipt came from one full run")
        with self.mutated_xml(1) as (data, artifact, target, receipt):
            tree = ET.parse(target)
            root = tree.getroot()
            root.set("timestamp", "2026-08-10T22:00:00")
            root.find("testsuite").set("timestamp", "2026-08-10T22:00:00")
            artifact["timestamp"] = "2026-08-10T22:00:00"
            tree.write(target, encoding="utf-8", xml_declaration=True)
            self.seal_mutation(data, artifact, target, receipt)
            with self.assertRaisesRegex(ValueError, "chronological"):
                validate(receipt)

    def test_probe_test_is_play_internal_only(self) -> None:
        internal = ROOT / "app/src/androidTestPlayInternal/java/io/codecks/internalquality/M11DexProxyInstrumentedTest.kt"
        public = ROOT / "app/src/androidTest/java/io/codecks/internalquality/M11DexProxyInstrumentedTest.kt"
        self.assertTrue(internal.is_file())
        self.assertFalse(public.exists())

    def test_source_target_test_xml_and_device_mutations_fail_closed(self) -> None:
        mutations = (
            lambda data: data["managedExecution"]["sources"][0].update({"sha256": "0" * 64}),
            lambda data: data["managedExecution"].update({"sourceDiffSha256": "0" * 64}),
            lambda data: data["managedExecution"]["targetApk"].update({"sha256": "0" * 64}),
            lambda data: data["managedExecution"]["testApk"].update({"sha256": "0" * 64}),
            lambda data: data["managedExecution"]["result"].update({"sha256": "0" * 64}),
            lambda data: data["managedExecution"]["device"]["properties"].update({"device": "physical"}),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                with self.assertRaisesRegex(ValueError, "binding mismatch"):
                    validate(self.mutated_receipt(mutation))

    def test_managed_binding_rejects_missing_and_tag_objects(self) -> None:
        with self.assertRaisesRegex(ValueError, "commit object"):
            validate(self.mutated_receipt(
                lambda data: data["managedExecution"].update({"sourceCommit": "0" * 40}),
            ))
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True,
        ).stdout.strip()
        tag_payload = (
            f"object {head}\ntype commit\ntag m11-binding-probe\n"
            "tagger M11 verifier <m11@example.invalid> 0 +0000\n\nprobe\n"
        )
        tag = subprocess.run(
            ["git", "hash-object", "-t", "tag", "-w", "--stdin"], cwd=ROOT, check=True,
            input=tag_payload, capture_output=True, text=True,
        ).stdout.strip()
        with self.assertRaisesRegex(ValueError, "commit object"):
            validate(self.mutated_receipt(
                lambda data: data["managedExecution"].update({"sourceCommit": tag}),
            ))


if __name__ == "__main__":
    unittest.main()
