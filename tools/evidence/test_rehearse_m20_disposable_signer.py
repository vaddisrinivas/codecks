import unittest
from pathlib import Path
import tempfile

from rehearse_m20_disposable_signer import PINNED_BUILD_TOOLS_VERSION, rehearse, select_apksigner


class DisposableSignerRehearsalTest(unittest.TestCase):
    def test_same_key_continues_and_different_key_fails(self):
        result = rehearse()
        self.assertTrue(result["sameKeyAccepted"])
        self.assertTrue(result["differentKeyRejected"])
        self.assertFalse(result["privateMaterialPersisted"])
        self.assertFalse(result["productionKeyAccessed"])
        self.assertFalse(result["protectedPackageTouched"])
        self.assertEqual(PINNED_BUILD_TOOLS_VERSION, result["apksigner"]["buildToolsVersion"])
        self.assertEqual(f"build-tools/{PINNED_BUILD_TOOLS_VERSION}/apksigner", result["apksigner"]["sdkRelativePath"])
        self.assertRegex(result["apksigner"]["sha256"], r"^[0-9a-f]{64}$")

    def test_mixed_versions_never_override_pin_or_use_lexicographic_order(self):
        with tempfile.TemporaryDirectory() as raw:
            sdk = Path(raw)
            for version in ("9.0.0", "10.0.0", PINNED_BUILD_TOOLS_VERSION, "37.0.0-rc1"):
                tool = sdk / "build-tools" / version / "apksigner"
                tool.parent.mkdir(parents=True)
                tool.write_bytes(version.encode())
            self.assertEqual(
                PINNED_BUILD_TOOLS_VERSION,
                select_apksigner(sdk).parent.name,
            )
            with self.assertRaisesRegex(RuntimeError, "pinned apksigner 35.0.0 unavailable"):
                select_apksigner(sdk, "35.0.0")


if __name__ == "__main__":
    unittest.main()
