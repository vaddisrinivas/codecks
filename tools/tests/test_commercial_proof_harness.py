from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zipfile


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import commercial_proof_harness as proof  # noqa: E402


def make_artifact(
    path: Path,
    *,
    manifest_values: tuple[str, ...] = ("app.codecks", "play", "production_dark"),
    dex_values: tuple[bytes, ...] = (b"PRODUCTION_DARK",),
    binary_manifest: bool = True,
) -> None:
    manifest = "\u0000".join(manifest_values)
    manifest_bytes = manifest.encode("utf-16le") if binary_manifest else manifest.encode()
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("AndroidManifest.xml", manifest_bytes)
        archive.writestr("classes.dex", b"dex\n" + b"\x00".join(dex_values))


def status(result: dict, check_id: str) -> str:
    return next(item["status"] for item in result["checks"] if item["id"] == check_id)


class ArtifactScannerTest(unittest.TestCase):
    def test_binary_manifest_and_false_positive_strings_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "production.apk"
            make_artifact(
                artifact,
                manifest_values=("app.codecks", "play", "production_dark", "FileProvider", "Advertisement"),
                dex_values=(
                    b"PRODUCTION_DARK",
                    b"tokenization",
                    b"billingAddress",
                    b"localhost",
                    b"-----BEGIN " + b"PRIVATE KEY-----",
                ),
            )
            result = proof.scan_artifact(artifact)

        self.assertEqual(proof.NOT_RUN, status(result, "manifest.package"))
        self.assertEqual(proof.PASS, status(result, "artifact.internal_or_sdk_namespace"))
        self.assertEqual(proof.PASS, status(result, "manifest.commercial_surface"))
        self.assertNotEqual(proof.FAIL, result["overall"])

    def test_binary_xml_ad_id_and_admob_metadata_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "production.apk"
            make_artifact(
                artifact,
                manifest_values=(
                    "app.codecks",
                    "play",
                    "production_dark",
                    "android.permission.AD_ID",
                    "com.google.android.gms.permission.AD_ID",
                    "com.google.android.gms.ads.APPLICATION_ID",
                ),
            )
            result = proof.scan_artifact(artifact)

        self.assertEqual(proof.FAIL, status(result, "manifest.commercial_surface"))
        self.assertEqual(proof.FAIL, result["overall"])

    def test_binary_manifest_never_false_passes_export_or_route_without_decoder(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "production.apk"
            make_artifact(
                artifact,
                manifest_values=("app.codecks", "account", 'android:exported="true"'),
            )
            result = proof.scan_artifact(artifact)

        self.assertEqual(proof.NOT_RUN, status(result, "manifest.exported_commercial_components"))
        self.assertEqual(proof.NOT_RUN, status(result, "manifest.commercial_routes"))

    def test_lab_artifact_renamed_as_production_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "app-codecks-production-signed.apk"
            make_artifact(
                artifact,
                dex_values=(
                    b"PRODUCTION_DARK",
                    b"io/codecks/internalcommercial/CommercialTestOverrideMarker",
                ),
            )
            with zipfile.ZipFile(artifact, "a") as archive:
                archive.writestr("META-INF/CERT.RSA", b"CN=Codecks Production")
            result = proof.scan_artifact(artifact)

        self.assertEqual(proof.FAIL, status(result, "artifact.internal_or_sdk_namespace"))

    def test_test_endpoint_in_dex_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "production.apk"
            make_artifact(
                artifact,
                dex_values=(b"PRODUCTION_DARK", b"https://10.0.2.2/test-backend"),
            )
            result = proof.scan_artifact(artifact)

        self.assertEqual(proof.FAIL, status(result, "artifact.test_endpoints"))

    def test_exported_commercial_component_and_key_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "production.apk"
            make_artifact(
                artifact,
                manifest_values=(
                    "app.codecks",
                    '<service android:name=".commercial.BillingService" android:exported="true">',
                ),
                dex_values=(b"PRODUCTION_DARK", b"AIza" + b"A" * 36),
                binary_manifest=False,
            )
            result = proof.scan_artifact(artifact)

        self.assertEqual(proof.FAIL, status(result, "manifest.exported_commercial_components"))
        self.assertEqual(proof.FAIL, status(result, "artifact.keys"))

    def test_commercial_deep_link_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "production.apk"
            make_artifact(
                artifact,
                manifest_values=("app.codecks", '<data android:scheme="codecks" android:host="purchase">'),
                binary_manifest=False,
            )
            result = proof.scan_artifact(artifact)

        self.assertEqual(proof.FAIL, status(result, "manifest.commercial_routes"))

    def test_nonexported_commercial_component_still_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "production.apk"
            make_artifact(
                artifact,
                manifest_values=("app.codecks", '<service android:name=".account.AccountService" android:exported="false">'),
                binary_manifest=False,
            )
            result = proof.scan_artifact(artifact)

        self.assertEqual(proof.FAIL, status(result, "manifest.commercial_components"))

    def test_every_analyzable_split_package_is_checked(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "base.apk"
            second = root / "other.apk"
            make_artifact(first)
            make_artifact(second)
            with mock.patch.object(
                proof,
                "_apkanalyzer_values",
                return_value=(
                    [(first, "app.codecks", "28", "<manifest />"),
                     (second, "app.codecks.internal", "28", "<manifest />")],
                    [],
                    "fake-apkanalyzer",
                ),
            ):
                result = proof.scan_artifact(root)

        self.assertEqual(proof.FAIL, status(result, "manifest.package"))

    def test_base_minimum_and_higher_conditional_split_minimums_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base-master.apk"
            sdk_29 = root / "base-sdk_29.apk"
            sdk_32 = root / "base-sdk_32.apk"
            for artifact in (base, sdk_29, sdk_32):
                make_artifact(artifact)
            with mock.patch.object(
                proof,
                "_apkanalyzer_values",
                return_value=(
                    [
                        (base, "app.codecks", "28", "<manifest />"),
                        (sdk_29, "app.codecks", "29", "<manifest />"),
                        (sdk_32, "app.codecks", "32", "<manifest />"),
                    ],
                    [],
                    "fake-apkanalyzer",
                ),
            ):
                result = proof.scan_artifact(root)

        self.assertEqual(proof.PASS, status(result, "manifest.min_sdk"))
        self.assertEqual({"28": 1, "29": 1, "32": 1}, result["min_sdk_distribution"])
        self.assertTrue(result["base_apk"].endswith("base-master.apk"))

    def test_conditional_split_minimum_below_base_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base-master.apk"
            conditional = root / "base-sdk_27.apk"
            make_artifact(base)
            make_artifact(conditional)
            with mock.patch.object(
                proof,
                "_apkanalyzer_values",
                return_value=(
                    [
                        (base, "app.codecks", "28", "<manifest />"),
                        (conditional, "app.codecks", "27", "<manifest />"),
                    ],
                    [],
                    "fake-apkanalyzer",
                ),
            ):
                result = proof.scan_artifact(root)

        self.assertEqual(proof.FAIL, status(result, "manifest.min_sdk"))

    def test_unknown_conditional_split_minimum_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base-master.apk"
            conditional = root / "base-sdk_unknown.apk"
            make_artifact(base)
            make_artifact(conditional)
            with mock.patch.object(
                proof,
                "_apkanalyzer_values",
                return_value=(
                    [
                        (base, "app.codecks", "28", "<manifest />"),
                        (conditional, "app.codecks", "unknown", "<manifest />"),
                    ],
                    [],
                    "fake-apkanalyzer",
                ),
            ):
                result = proof.scan_artifact(root)

        self.assertEqual(proof.FAIL, status(result, "manifest.min_sdk"))

    def test_unknown_base_minimum_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory) / "base.apk"
            make_artifact(base)
            with mock.patch.object(
                proof,
                "_apkanalyzer_values",
                return_value=([(base, "app.codecks", "unknown", "<manifest />")], [], "fake-apkanalyzer"),
            ):
                result = proof.scan_artifact(base)

        self.assertEqual(proof.FAIL, status(result, "manifest.min_sdk"))

    def test_missing_or_ambiguous_base_fails(self) -> None:
        scenarios = (
            ("missing", ("base-sdk_29.apk", "base-sdk_32.apk")),
            ("ambiguous", ("base-master.apk", "universal.apk")),
        )
        for label, names in scenarios:
            with self.subTest(label=label), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                artifacts = [root / name for name in names]
                for artifact in artifacts:
                    make_artifact(artifact)
                with mock.patch.object(
                    proof,
                    "_apkanalyzer_values",
                    return_value=(
                        [(artifact, "app.codecks", "28", "<manifest />") for artifact in artifacts],
                        [],
                        "fake-apkanalyzer",
                    ),
                ):
                    result = proof.scan_artifact(root)

                self.assertEqual(proof.FAIL, status(result, "manifest.min_sdk"))

    def test_unsafe_apks_member_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apks = Path(directory) / "unsafe.apks"
            with zipfile.ZipFile(apks, "w") as archive:
                archive.writestr("../escape.apk", b"not an apk")
            result = proof.scan_artifact(apks)

        self.assertEqual(proof.FAIL, result["overall"])

    def test_split_apks_are_aggregated(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.apk"
            config = root / "split_config.en.apk"
            make_artifact(base)
            with zipfile.ZipFile(config, "w") as archive:
                archive.writestr("resources.arsc", b"safe")
            apks = root / "set.apks"
            with zipfile.ZipFile(apks, "w") as archive:
                archive.write(base, "splits/base-master.apk")
                archive.write(config, "splits/split_config.en.apk")

            result = proof.scan_artifact(apks)

        self.assertEqual(2, result["split_count"])
        self.assertEqual(proof.PASS, status(result, "artifact.payloads"))

    def test_missing_artifact_is_not_run_not_pass(self) -> None:
        result = proof.scan_artifact(Path("/definitely/missing/codecks.apk"))
        self.assertEqual(proof.NOT_RUN, result["overall"])


class StaticScannerTest(unittest.TestCase):
    def test_backup_rules_require_both_backup_and_transfer_exclusions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "app/src/main/AndroidManifest.xml"
            rules = root / "app/src/main/res/xml/data_extraction_rules.xml"
            manifest.parent.mkdir(parents=True)
            rules.parent.mkdir(parents=True)
            manifest.write_text(
                '<application xmlns:android="http://schemas.android.com/apk/res/android" '
                'android:allowBackup="false" android:fullBackupContent="false" '
                'android:dataExtractionRules="@xml/data_extraction_rules" />',
            )
            rules.write_text(
                '<data-extraction-rules><cloud-backup><exclude domain="root" path="." />'
                '</cloud-backup><device-transfer><exclude domain="root" path="." />'
                '</device-transfer></data-extraction-rules>',
            )
            valid = proof.scan_backup_rules(root)
            rules.write_text('<data-extraction-rules><cloud-backup /></data-extraction-rules>')
            invalid = proof.scan_backup_rules(root)

        self.assertEqual(proof.PASS, valid["overall"])
        self.assertEqual(proof.FAIL, invalid["overall"])

    def test_backup_comments_cannot_spoof_exclusions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "app/src/main/AndroidManifest.xml"
            rules = root / "app/src/main/res/xml/data_extraction_rules.xml"
            manifest.parent.mkdir(parents=True)
            rules.parent.mkdir(parents=True)
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application '
                'android:allowBackup="false" android:fullBackupContent="false" '
                'android:dataExtractionRules="@xml/data_extraction_rules" /></manifest>',
            )
            rules.write_text(
                '<data-extraction-rules><cloud-backup><!-- <exclude domain="root" path="." /> -->'
                '</cloud-backup><device-transfer /></data-extraction-rules>',
            )
            result = proof.scan_backup_rules(root)

        self.assertEqual(proof.FAIL, result["overall"])

    def test_operational_compose_ad_reference_and_override_parser_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/io/codecks/ui/Screen.kt"
            source.parent.mkdir(parents=True)
            (root / "app/build.gradle.kts").write_text(
                "isMinifyEnabled = false\nisShrinkResources = false\n",
            )
            source.write_text(
                "@Composable fun Screen() { MobileAds.initialize(); CommercialTestOverrideMarker.toString() }",
            )
            result = proof.scan_operational_sources(root)

        self.assertEqual(proof.FAIL, status(result, "source.operational_ads"))
        self.assertEqual(proof.FAIL, status(result, "source.production_override_parser"))

    def test_commercial_contract_definitions_do_not_count_as_operational_refs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contract = root / "app/src/main/java/io/codecks/domain/commercial/Ads.kt"
            contract.parent.mkdir(parents=True)
            (root / "app/build.gradle.kts").write_text(
                "isMinifyEnabled = false\nisShrinkResources = false\n",
            )
            contract.write_text("interface CommercialAdEligibilityService")
            result = proof.scan_operational_sources(root)

        self.assertEqual(proof.PASS, result["overall"])

    def test_comments_do_not_create_operational_or_route_false_positives(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/io/codecks/ui/Screen.kt"
            manifest = root / "app/src/main/AndroidManifest.xml"
            source.parent.mkdir(parents=True)
            manifest.parent.mkdir(parents=True, exist_ok=True)
            (root / "app/build.gradle.kts").write_text(
                "isMinifyEnabled = false\nisShrinkResources = false\n",
            )
            source.write_text("// MobileAds.initialize()\n@Composable fun Screen() = Unit")
            manifest.write_text('<!-- <data android:scheme="codecks" android:host="purchase" /> -->')
            result = proof.scan_operational_sources(root)

        self.assertEqual(proof.PASS, result["overall"])

    def test_production_binding_is_fixed_dark_and_not_a_user_flag(self) -> None:
        root = TOOLS.parents[0]
        result = proof.scan_production_dark_binding(root)
        self.assertEqual(proof.PASS, result["overall"])

    def test_device_scripts_are_emulator_guarded_and_non_destructive(self) -> None:
        root = TOOLS.parents[0]
        attack = (root / "scripts/commercial_surface_attack.sh").read_text()
        cold = (root / "scripts/collect_commercial_cold_start.sh").read_text()
        combined = attack + cold
        self.assertEqual(2, combined.count("ro.kernel.qemu"))
        for forbidden in ("adb uninstall", "pm uninstall", "pm clear", "adb install"):
            self.assertNotIn(forbidden, combined)
        for marker in ("am start", "am broadcast", "am startservice", "codecks://account"):
            self.assertIn(marker, attack)
        self.assertIn('-p "$PACKAGE"', attack)
        self.assertIn('find "$OUTPUT_DIR/results" -type f -delete', attack)
        self.assertIn('expected = {', attack)
        for marker in (
            "logcat", "dumpsys activity", "jobscheduler", "alarm", "work.txt",
            "binder", "network", "perfetto", "NOT_RUN",
        ):
            self.assertIn(marker, cold)
        self.assertIn("cold_start.launch_truth", cold)
        self.assertIn("collect_optional", cold)
        self.assertIn("cmd package resolve-activity --brief", cold)
        self.assertIn("cmd package query-activities --brief --components", cold)
        self.assertGreaterEqual(cold.count('-p "$PACKAGE"'), 2)
        self.assertNotIn('$PACKAGE/.MainActivity', cold)
        self.assertIn('"launcher_component":sys.argv[5]', cold)

    def test_cold_start_launcher_resolution_accepts_one_matching_exported_component(self) -> None:
        result, receipt = self._run_fake_cold_start(
            resolve="app.codecks/io.codecks.MainActivity",
            inventory="app.codecks/io.codecks.MainActivity",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("app.codecks/io.codecks.MainActivity", receipt["launcher_component"])
        self.assertEqual(proof.PASS, status(receipt, "cold_start.launch_truth"))

    def test_cold_start_launcher_resolution_rejects_absent_ambiguous_and_wrong_package(self) -> None:
        cases = (
            ("No activity found", "No activities found"),
            (
                "app.codecks/io.codecks.MainActivity",
                "app.codecks/io.codecks.MainActivity\napp.codecks/io.codecks.OtherActivity",
            ),
            ("evil.example/io.codecks.MainActivity", "evil.example/io.codecks.MainActivity"),
        )
        for resolved, inventory in cases:
            with self.subTest(resolved=resolved, inventory=inventory):
                result, receipt = self._run_fake_cold_start(resolve=resolved, inventory=inventory)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(proof.FAIL, receipt["overall"])
                self.assertEqual(proof.FAIL, status(receipt, "cold_start.launcher_component"))

    def _run_fake_cold_start(self, *, resolve: str, inventory: str) -> tuple[subprocess.CompletedProcess[str], dict]:
        root = TOOLS.parents[0]
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            fake_bin = temp / "bin"
            output = temp / "output"
            fake_bin.mkdir()
            adb = fake_bin / "adb"
            adb.write_text(
                """#!/bin/sh
case "$*" in
  *"shell getprop ro.kernel.qemu"*) echo 1 ;;
  *"shell pm path app.codecks"*) echo package:/data/app/app.codecks/base.apk ;;
  *"shell cmd package resolve-activity --brief"*) printf '%s\\n' "$FAKE_RESOLVE" ;;
  *"shell cmd package query-activities --brief --components"*) printf '%s\\n' "$FAKE_INVENTORY" ;;
  *"shell am start -W -n "*) for value in "$@"; do last="$value"; done; echo "Status: ok"; echo "Activity: $last" ;;
  *"shell command -v perfetto"*) exit 1 ;;
  *"logcat -d -v threadtime"*) echo logcat ;;
  *"shell dumpsys"*) echo inventory ;;
  *) exit 0 ;;
esac
""",
            )
            adb.chmod(0o755)
            sleep = fake_bin / "sleep"
            sleep.write_text("#!/bin/sh\nexit 0\n")
            sleep.chmod(0o755)
            environment = os.environ.copy()
            environment.update(
                {
                    "PATH": f"{fake_bin}{os.pathsep}{environment['PATH']}",
                    "FAKE_RESOLVE": resolve,
                    "FAKE_INVENTORY": inventory,
                },
            )
            result = subprocess.run(
                [
                    str(root / "scripts/collect_commercial_cold_start.sh"),
                    "emulator-test",
                    str(output),
                    "--run",
                ],
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )
            receipt = json.loads((output / "receipt.json").read_text())
            return result, receipt

    def test_static_ci_step_has_no_network_or_device_action(self) -> None:
        root = TOOLS.parents[0]
        runner = (root / "scripts/run_commercial_static_proof.sh").read_text()
        workflow = (root / ".github/workflows/quality.yml").read_text()
        for forbidden in ("adb ", "curl ", "wget ", "bundletool", "gradlew"):
            self.assertNotIn(forbidden, runner)
        self.assertIn("PYTHONHASHSEED=0", runner)
        self.assertIn("./scripts/run_commercial_static_proof.sh", workflow)


class SeedAndPayloadTest(unittest.TestCase):
    def test_stale_lab_seed_cannot_raise_production_policy(self) -> None:
        fixture = json.loads((TOOLS / "tests/fixtures/stale_lab_seed.json").read_text())
        result = proof.prove_production_dark_seed(fixture)
        self.assertEqual(proof.PASS, result["overall"])
        self.assertTrue(all(value == "DENIED_BUILD_PRODUCTION_DARK" for value in result["decisions"].values()))

    def test_transport_payload_allows_opaque_ids_but_rejects_secrets(self) -> None:
        safe = json.loads((TOOLS / "tests/fixtures/transport_safe.json").read_text())
        unsafe = json.loads((TOOLS / "tests/fixtures/transport_unsafe.json").read_text())
        self.assertEqual(proof.PASS, proof.scan_transport_payload(safe)["overall"])
        self.assertEqual(proof.FAIL, proof.scan_transport_payload(unsafe)["overall"])


if __name__ == "__main__":
    unittest.main()
