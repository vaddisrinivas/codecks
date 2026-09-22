#!/usr/bin/env python3
"""Collect current-source proof for native helper Phase A and pairing Phase B."""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from argparse import ArgumentParser

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ID = "codecks.pairing.phase-ab.source-evidence.v1"
PHASE_COMMITS = {
    "phaseA": "33d27125f13465dc05ed10983f3523447eae08c9",
    "phaseB": "08c9ae50ee0a46ad1682b03c4989130ae78f2bad",
}
SOURCE_PATHS = (
    "app/src/androidTest/java/io/codecks/ui/app/MainActivityStartupInstrumentedTest.kt",
    "app/src/main/java/io/codecks/AppCompositionRoot.kt",
    "app/src/main/java/io/codecks/AppFeatureBinders.kt",
    "app/src/main/java/io/codecks/AppHelperRuntime.kt",
    "app/src/main/java/io/codecks/MainActivity.kt",
    "app/src/main/java/io/codecks/data/reactive/helper/PairingV2Crypto.kt",
    "app/src/main/java/io/codecks/data/reactive/helper/ReactiveHelperPairingImporter.kt",
    "app/src/main/java/io/codecks/data/reactive/helper/ReactiveHelperPairingV2Client.kt",
    "app/src/main/java/io/codecks/ui/settings/CodecksHelperUiState.kt",
    "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt",
    "app/src/main/java/io/codecks/ui/settings/SettingsScreen.kt",
    "app/src/test/java/io/codecks/data/reactive/helper/ReactiveHelperPairingImporterTest.kt",
    "app/src/test/java/io/codecks/data/reactive/helper/ReactiveHelperPairingV2ClientTest.kt",
    "macHelper/Sources/CodecksMacHelper/HelperSystemStatus.swift",
    "macHelper/Sources/CodecksMacHelper/PairingCredentialStore.swift",
    "macHelper/Sources/CodecksMacHelper/PairingStore.swift",
    "macHelper/Sources/CodecksMacHelper/PairingV2.swift",
    "macHelper/Sources/CodecksMacHelper/PairingV2Controller.swift",
    "macHelper/Sources/CodecksMacHelper/ReactiveFramedTransportService.swift",
    "macHelper/Sources/CodecksMacHelper/ReactiveMacHelperRuntime.swift",
    "macHelper/Sources/CodecksMacHelper/ReactiveSessionCoordinator.swift",
    "macHelper/Sources/CodecksMacHelper/ReactiveTcpHelperServer.swift",
    "macHelper/Sources/CodecksMacHelperApp/HelperAppModel.swift",
    "macHelper/Sources/CodecksMacHelperApp/HelperViews.swift",
    "macHelper/Sources/CodecksMacHelperApp/PairingQRCode.swift",
    "macHelper/Sources/CodecksMacHelperCLI/main.swift",
    "macHelper/Tests/CodecksMacHelperTests/PairingV2ControllerTests.swift",
    "macHelper/Tests/CodecksMacHelperTests/PairingV2Tests.swift",
    "macHelper/Tests/CodecksMacHelperTests/ReactiveMacHelperTests.swift",
    "macHelper/scripts/build-local-app.sh",
    "macHelper/scripts/install-launchd.sh",
    "macHelper/scripts/sign-local-app.sh",
    "macHelper/scripts/test-signing-contract.sh",
    "shared/src/commonMain/kotlin/io/codecks/shared/protocol/PairingV2Protocol.kt",
    "shared/src/commonTest/kotlin/io/codecks/shared/protocol/PairingV2ProtocolTest.kt",
)
EXECUTIONS = (
    ("swift.mac_helper", "tasks/test-evidence/pairing-phase-ab-swift.log", None),
    ("android.importer", "tasks/test-evidence/pairing-phase-ab-android-importer.xml", "io.codecks.data.reactive.helper.ReactiveHelperPairingImporterTest"),
    ("android.client", "tasks/test-evidence/pairing-phase-ab-android-client.xml", "io.codecks.data.reactive.helper.ReactiveHelperPairingV2ClientTest"),
    ("android.identity", "tasks/test-evidence/pairing-phase-ab-android-identity.xml", "io.codecks.data.reactive.helper.ReactiveHelperIdentityCodecTest"),
    ("shared.protocol", "tasks/test-evidence/pairing-phase-ab-shared.xml", "io.codecks.shared.protocol.PairingV2ProtocolTest"),
)
GRADLE_CLASSES = tuple(class_name for _, _, class_name in EXECUTIONS if class_name is not None)
COMMANDS = (
    "swift test --package-path macHelper",
    "./gradlew --no-daemon --rerun-tasks :app:testOssReleaseUnitTest --tests io.codecks.data.reactive.helper.ReactiveHelperPairingImporterTest --tests io.codecks.data.reactive.helper.ReactiveHelperPairingV2ClientTest --tests io.codecks.data.reactive.helper.ReactiveHelperIdentityCodecTest :shared:jvmTest --tests io.codecks.shared.protocol.PairingV2ProtocolTest",
)
PASS_LANES = (
    "source.native_mac_helper_app",
    "source.one_use_qr_offer",
    "source.per_device_pairing_auth",
    "source.matching_code_confirmation",
    "source.android_deep_link_import",
    "source.legacy_export_requires_unsafe_flag",
    "source.keychain_fail_closed_without_entitlement",
    "source.transport_discloses_no_encryption",
)
NOT_RUN_LANES = (
    "artifact.unsigned_local_app_build",
    "artifact.signed_helper_app",
    "runtime.keychain_entitlement_probe",
    "runtime.mac_to_android_pairing",
    "physical.phone_pairing",
    "human.first_run_pairing",
)


def safe_path(relative: str) -> Path:
    candidate = Path(relative)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise ValueError(f"unsafe path: {relative}")
    resolved = (ROOT / candidate).resolve(strict=True)
    if not resolved.is_relative_to(ROOT.resolve()):
        raise ValueError(f"path escapes repository: {relative}")
    if any((ROOT.joinpath(*candidate.parts[:i])).is_symlink() for i in range(1, len(candidate.parts) + 1)):
        raise ValueError(f"symlink forbidden: {relative}")
    return resolved


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_digest(data: dict) -> str:
    payload = dict(data)
    payload.pop("receiptDigest", None)
    return hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def current_commit() -> str:
    return subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.strip()


def committed_blob(commit: str, relative: str) -> bytes:
    return subprocess.run(
        ["git", "show", f"{commit}:{relative}"], cwd=ROOT, check=True, capture_output=True,
    ).stdout


def authoritative_phase_b_paths() -> tuple[str, ...]:
    paths = subprocess.run(
        ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", PHASE_COMMITS["phaseB"]],
        cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.splitlines()
    # The Phase-B commit changed one explanatory README plus 35 executable,
    # test, and packaging files. Documentation is outside this source proof.
    return tuple(path for path in paths if path != "macHelper/README.md")


def assert_bound_sources() -> None:
    if authoritative_phase_b_paths() != SOURCE_PATHS:
        raise ValueError("Phase B executable/test/script manifest is incomplete or reordered")
    for relative in SOURCE_PATHS:
        if safe_path(relative).read_bytes() != committed_blob(PHASE_COMMITS["phaseB"], relative):
            raise ValueError(f"current source differs from exact Phase B blob: {relative}")


def execution(relative: str, class_name: str | None) -> dict:
    path = safe_path(relative)
    if path.suffix == ".log":
        lines = path.read_text().splitlines()
        cases = [line.removeprefix("case=") for line in lines if line.startswith("case=")]
        if lines[:4] != ["schemaVersion=1", f"sourceCommit={PHASE_COMMITS['phaseB']}", f"command={COMMANDS[0]}", "tests=53 failures=0 errors=0 skipped=0"] or len(cases) != 53 or len(set(cases)) != 53:
            raise ValueError("Swift execution log does not prove 53/53 PASS")
        return {"path": relative, "sha256": sha256(path), "tests": 53, "failures": 0, "errors": 0, "skipped": 0}
    root = ET.fromstring(path.read_bytes())
    suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
    tests = sum(int(suite.attrib.get("tests", "0")) for suite in suites)
    failures = sum(int(suite.attrib.get("failures", "0")) for suite in suites)
    errors = sum(int(suite.attrib.get("errors", "0")) for suite in suites)
    skipped = sum(int(suite.attrib.get("skipped", "0")) for suite in suites)
    if tests < 1 or failures or errors or skipped:
        raise ValueError(f"execution is not a clean PASS: {relative}")
    if class_name is not None and not any(case.attrib.get("classname") == class_name for suite in suites for case in suite.findall("testcase")):
        raise ValueError(f"execution class mismatch: {relative}")
    return {"path": relative, "sha256": sha256(path), "tests": tests, "failures": failures, "errors": errors, "skipped": skipped}


def collect() -> dict:
    assert_bound_sources()
    data = {
        "schema": SCHEMA_ID,
        "scope": "EXACT_PHASE_B_SOURCE_AND_CPU_UNIT",
        "status": "PASS_WITH_NOT_RUN_BOUNDARIES",
        "sourceCommit": current_commit(),
        "phaseCommits": PHASE_COMMITS,
        "sources": [
            {"path": relative, "commit": PHASE_COMMITS["phaseB"], "sha256": sha256(safe_path(relative))}
            for relative in SOURCE_PATHS
        ],
        "executions": [dict({"id": identity}, **execution(relative, class_name)) for identity, relative, class_name in EXECUTIONS],
        "commands": list(COMMANDS),
        "lanes": [
            {"id": lane, "status": "PASS", "evidence": "EXACT_PHASE_B_BLOB_AND_CPU_UNIT"} for lane in PASS_LANES
        ] + [
            {"id": lane, "status": "NOT_RUN", "evidence": "EXTERNAL_OR_ARTIFACT"}
            for lane in NOT_RUN_LANES
        ],
        "boundaries": {
            "signedArtifactBuilt": False,
            "signingIdentityAccessed": False,
            "keychainEntitlementProven": False,
            "runtimePairingProven": False,
            "physicalPhoneTouched": False,
            "tcpPayloadEncrypted": False,
        },
        "limitations": [
            "Source proof does not prove an unsigned or signed application artifact.",
            "Keychain storage requires a correctly signed application entitlement and remains NOT_RUN.",
            "Pairing HMAC authenticates integrity; the local TCP payload is not encrypted.",
            "Real Mac, Android phone, and moderated first-run pairing remain NOT_RUN.",
        ],
    }
    data["receiptDigest"] = canonical_digest(data)
    return data


def run_gates() -> None:
    swift = subprocess.run(
        ["swift", "test", "--package-path", "macHelper"], cwd=ROOT, check=False,
        capture_output=True, text=True,
    )
    swift_output = swift.stdout + swift.stderr
    if swift.returncode or "Executed 53 tests, with 0 failures" not in swift_output:
        raise ValueError("Swift Phase A/B gate failed")
    cases = re.findall(r"Test Case '([^']+)' passed", swift_output)
    if len(cases) != 53 or len(set(cases)) != 53:
        raise ValueError("Swift Phase A/B gate did not report 53 unique cases")
    canonical_swift = "\n".join([
        "schemaVersion=1", f"sourceCommit={PHASE_COMMITS['phaseB']}",
        f"command={COMMANDS[0]}", "tests=53 failures=0 errors=0 skipped=0",
        *[f"case={case}" for case in sorted(cases)], "",
    ])
    safe_path("tasks/test-evidence").joinpath("pairing-phase-ab-swift.log").write_text(canonical_swift)
    command = [str(ROOT / "gradlew"), "--no-daemon", "--rerun-tasks", ":app:testOssReleaseUnitTest"]
    for class_name in GRADLE_CLASSES[:-1]:
        command += ["--tests", class_name]
    command += [":shared:jvmTest", "--tests", GRADLE_CLASSES[-1]]
    if subprocess.run(command, cwd=ROOT, check=False).returncode:
        raise ValueError("Android/shared Phase A/B gates failed")
    for _, target, class_name in EXECUTIONS[1:]:
        module = "shared/build/test-results/jvmTest" if class_name.startswith("io.codecks.shared") else "app/build/test-results/testOssReleaseUnitTest"
        source = ROOT / module / f"TEST-{class_name}.xml"
        safe_path("tasks/test-evidence").joinpath(Path(target).name).write_bytes(source.read_bytes())


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument("--run-gates", action="store_true")
    args = parser.parse_args()
    if args.run_gates:
        run_gates()
    output = ROOT / "tasks/test-evidence/pairing-phase-ab-source.json"
    output.write_text(json.dumps(collect(), sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
