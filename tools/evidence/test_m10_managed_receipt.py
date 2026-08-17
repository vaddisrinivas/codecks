import copy
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

MODULE_PATH = Path(__file__).with_name("m10_managed_receipt.py")
SPEC = importlib.util.spec_from_file_location("m10_managed_receipt", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class M10ManagedReceiptTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / "repo"
        self.sdk = Path(self.temp.name) / "sdk"
        for name in MODULE.SOURCE_PATHS:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name, encoding="utf-8")
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.email", "m10@example.invalid"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "M10 Test"], cwd=self.root, check=True)
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-qm", "fixture"], cwd=self.root, check=True)
        self.result_dir = self.root / "app/build/outputs/androidTest-results/managedDevice/debug/flavors/oss/m10CompactApi35"
        self.result_dir.mkdir(parents=True)
        cases = "".join(f'<testcase classname="{MODULE.TEST_CLASS}" name="{name}" />' for name in sorted(MODULE.TESTS))
        (self.result_dir / "TEST-result.xml").write_text(
            f'<testsuites tests="5" failures="0" errors="0" skipped="0"><testsuite><properties><property name="device" value="m10CompactApi35"/><property name="flavor" value="oss"/><property name="project" value=":app"/></properties>{cases}</testsuite></testsuites>',
            encoding="utf-8",
        )
        (self.result_dir / "device-info.pb").write_bytes(b"managed-device")
        (self.result_dir / "test-result.textproto").write_text("scheduled_test_case_count: 5\ntest_status: PASSED\n", encoding="utf-8")
        self.target = self.root / "app/build/outputs/apk/oss/debug/app-oss-debug.apk"
        self.test_apk = self.root / "app/build/outputs/apk/androidTest/oss/debug/app-oss-debug-androidTest.apk"
        for apk in (self.target, self.test_apk):
            apk.parent.mkdir(parents=True, exist_ok=True)
            apk.write_bytes(b"PK\x03\x04fixture")
        self.image = self.sdk / "system-images/android-35/default/arm64-v8a/package.xml"
        self.image.parent.mkdir(parents=True)
        self.image.write_text("<localPackage path='system-images;android-35;default;arm64-v8a'/>", encoding="utf-8")
        self.args = SimpleNamespace(
            root=self.root, sdk=self.sdk, api=35, shape="compact",
            task=":app:m10CompactApi35OssDebugAndroidTest", result_dir=self.result_dir,
            target_apk=self.target, test_apk=self.test_apk, image_package=self.image,
        )

    def tearDown(self):
        self.temp.cleanup()

    def test_valid_receipt_round_trips(self):
        receipt = MODULE.create(self.args)
        MODULE.validate(receipt, self.root, self.sdk)

    def test_source_artifact_device_and_package_mutations_fail_closed(self):
        original = MODULE.create(self.args)
        mutations = []
        for path, value in (
            (("source", "digest"), "0" * 64),
            (("artifacts", "targetApk", "sha256"), "0" * 64),
            (("device", "managedName"), "m10TabletApi35"),
            (("package",), "app.codecks"),
        ):
            mutated = copy.deepcopy(original)
            owner = mutated
            for key in path[:-1]:
                owner = owner[key]
            owner[path[-1]] = value
            mutations.append(mutated)
        for mutated in mutations:
            with self.subTest(receipt=json.dumps(mutated, sort_keys=True)[:120]):
                with self.assertRaises(ValueError):
                    MODULE.validate(mutated, self.root, self.sdk)

    def test_other_api_system_image_is_rejected(self):
        receipt = MODULE.create(self.args)
        other = self.sdk / "system-images/android-34/default/arm64-v8a/package.xml"
        other.parent.mkdir(parents=True)
        other.write_bytes(self.image.read_bytes())
        receipt["device"]["packagePath"] = "system-images/android-34/default/arm64-v8a/package.xml"
        with self.assertRaisesRegex(ValueError, "system image coordinate"):
            MODULE.validate(receipt, self.root, self.sdk)

    def test_failed_or_stale_result_is_rejected(self):
        receipt = MODULE.create(self.args)
        xml = self.result_dir / "TEST-result.xml"
        xml.write_text(xml.read_text().replace('failures="0"', 'failures="1"'), encoding="utf-8")
        with self.assertRaises(ValueError):
            MODULE.validate(receipt, self.root, self.sdk)


if __name__ == "__main__":
    unittest.main()
