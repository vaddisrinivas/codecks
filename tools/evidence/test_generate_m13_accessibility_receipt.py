import tempfile
import json
import unittest
from pathlib import Path
from unittest.mock import patch

from generate_m13_accessibility_receipt import (
    BASE_COMMIT,
    MANAGED_REQUIRED,
    SOURCE_PATHS,
    TEST_SOURCE_PATHS,
    UNIT_CLASSES,
    UNIT_REQUIRED,
    RepositoryIdentity,
    build_receipt,
    canonical_managed_path,
    canonical_managed_companions,
    canonical_unit_paths,
)
from validate_m13_accessibility_receipt import RECEIPT, validate


class M13ReceiptTest(unittest.TestCase):
    def setUp(self) -> None:
        self.binding = patch(
            "generate_m13_accessibility_receipt.collect_binding",
            return_value={"strict": "fixture"},
        )
        self.binding.start()
        self.addCleanup(self.binding.stop)

    def fixture(self) -> tuple[Path, Path, RepositoryIdentity]:
        root = Path(tempfile.mkdtemp()).resolve()
        for relative in SOURCE_PATHS + TEST_SOURCE_PATHS:
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(relative)
        managed = canonical_managed_path(root)
        managed.parent.mkdir(parents=True, exist_ok=True)
        self.write_managed(managed, MANAGED_REQUIRED)
        for companion in canonical_managed_companions(root):
            companion.write_bytes(b"gradle-managed-evidence")
        by_class = {class_name: [] for class_name in UNIT_CLASSES}
        for identity in UNIT_REQUIRED:
            class_name, _ = identity.rsplit(".", 1)
            by_class[class_name].append(identity)
        for path, class_name in zip(canonical_unit_paths(root), UNIT_CLASSES, strict=True):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(self.suite_xml(by_class[class_name]))
        identity = RepositoryIdentity(BASE_COMMIT, "1" * 40, True)
        return root, managed, identity

    @staticmethod
    def cases(identities: set[str] | list[str], child: str = "") -> str:
        return "".join(
            f'<testcase classname="{identity.rsplit(".", 1)[0]}" '
            f'name="{identity.rsplit(".", 1)[1]}">{child}</testcase>'
            for identity in sorted(identities)
        )

    def suite_xml(self, identities: set[str] | list[str]) -> str:
        return "<testsuite>" + self.cases(identities) + "</testsuite>"

    def write_managed(self, path: Path, identities: set[str] | list[str]) -> None:
        path.write_text(
            '<testsuites><testsuite><properties><property name="device" value="pixel6Api35"/>'
            '<property name="flavor" value="playInternal"/><property name="project" value=":app"/>'
            "</properties>" + self.cases(identities) + "</testsuite></testsuites>"
        )

    def test_complete_canonical_evidence_produces_commit_and_source_bound_receipt(self) -> None:
        root, managed, identity = self.fixture()
        receipt = build_receipt(root, managed, "pixel6Api35", identity)
        self.assertEqual("PASS", receipt["status"])
        self.assertEqual("PASS", receipt["proof_boundary"]["managed_android_api35_accessibility_matrix"])
        self.assertEqual("NOT_RUN", receipt["proof_boundary"]["physical_device"])
        self.assertEqual(identity.source_commit, receipt["repository"]["source_commit"])
        self.assertEqual(len(TEST_SOURCE_PATHS), len(receipt["test_source_sha256"]))
        self.assertEqual("GRADLE_MANAGED_DEVICE_JUNIT_XML", receipt["managed_evidence"]["type"])

    def test_noncanonical_spoofed_or_incomplete_managed_evidence_fails_closed(self) -> None:
        root, managed, identity = self.fixture()
        spoof = root / "spoof.xml"
        spoof.write_bytes(managed.read_bytes())
        with self.assertRaisesRegex(ValueError, "not canonical"):
            build_receipt(root, spoof, "pixel6Api35", identity)

        self.write_managed(managed, MANAGED_REQUIRED - {next(iter(MANAGED_REQUIRED))})
        with self.assertRaisesRegex(ValueError, "managed tests missing"):
            build_receipt(root, managed, "pixel6Api35", identity)

        self.write_managed(managed, MANAGED_REQUIRED)
        text = managed.read_text().replace('value="pixel6Api35"', 'value="physicalPhone"')
        managed.write_text(text)
        with self.assertRaisesRegex(ValueError, "metadata mismatch"):
            build_receipt(root, managed, "pixel6Api35", identity)

        root, managed, identity = self.fixture()
        canonical_managed_companions(root)[0].unlink()
        with self.assertRaisesRegex(ValueError, "companion evidence missing"):
            build_receipt(root, managed, "pixel6Api35", identity)

    def test_cross_type_injection_failure_and_ancestry_spoofs_fail_closed(self) -> None:
        root, managed, identity = self.fixture()
        self.write_managed(managed, MANAGED_REQUIRED | {next(iter(UNIT_REQUIRED))})
        with self.assertRaisesRegex(ValueError, "Unit testcase injected"):
            build_receipt(root, managed, "pixel6Api35", identity)

        root, managed, identity = self.fixture()
        failed = next(iter(MANAGED_REQUIRED))
        self.write_managed(managed, MANAGED_REQUIRED)
        managed.write_text(managed.read_text().replace(f'name="{failed.rsplit(".", 1)[1]}">', f'name="{failed.rsplit(".", 1)[1]}"><failure/>'))
        with self.assertRaisesRegex(ValueError, "not passing"):
            build_receipt(root, managed, "pixel6Api35", identity)

        root, managed, _ = self.fixture()
        with self.assertRaisesRegex(ValueError, "ancestry"):
            build_receipt(root, managed, "pixel6Api35", RepositoryIdentity(BASE_COMMIT, "2" * 40, False))

    def test_dtd_oversize_and_wrong_xml_type_fail_closed(self) -> None:
        root, managed, identity = self.fixture()
        managed.write_text("<!DOCTYPE testsuites><testsuites/>")
        with self.assertRaisesRegex(ValueError, "DTD"):
            build_receipt(root, managed, "pixel6Api35", identity)

        root, managed, identity = self.fixture()
        managed.write_text("<testsuite/>")
        with self.assertRaisesRegex(ValueError, "root type"):
            build_receipt(root, managed, "pixel6Api35", identity)

    def test_bound_source_target_test_xml_and_device_mutations_fail_closed(self) -> None:
        mutations = (
            lambda data: data["managed_execution"]["sources"][0].update({"sha256": "0" * 64}),
            lambda data: data["managed_execution"]["targetApk"].update({"sha256": "0" * 64}),
            lambda data: data["managed_execution"]["testApk"].update({"sha256": "0" * 64}),
            lambda data: data["managed_execution"]["result"].update({"sha256": "0" * 64}),
            lambda data: data["managed_execution"]["device"]["properties"].update({"device": "physical"}),
        )
        for mutation in mutations:
            data = json.loads(RECEIPT.read_text())
            mutation(data)
            directory = tempfile.TemporaryDirectory()
            self.addCleanup(directory.cleanup)
            path = Path(directory.name) / "receipt.json"
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "binding mismatch"):
                validate(path)


if __name__ == "__main__":
    unittest.main()
