#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import os
import subprocess
import xml.etree.ElementTree as ET
from managed_execution_binding import collect_binding

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ID = "codecks.autonomous-maturity.m15-clipboard-battery.v1"
TEST_CLASS = "io.codecks.ui.clipboard.M15ClipboardCharacterizationTest"
MANAGED_TEST_CLASS = "io.codecks.ui.clipboard.M15ClipboardPrivacyInstrumentedTest"
EXPECTED_METHODS = {
    "visibleUnlockedSessionIsTheOnlyAutomaticReadAuthority",
    "batterySaverStopsAutomaticPollingWithoutChangingSessionAuthority",
    "duplicateAndSelfEchoDoNotCreateSyncLoop",
    "simultaneousDifferentEditsProduceConflictInsteadOfLastWriterWin",
    "largePayloadIsRepresentedByHashNotClipboardContent",
    "lastSyncReceiptPersistsOnlyClosedMetadata",
    "ordinarySynchronizedTextRequestsHiddenSystemPreview",
    "retryBackoffIsBounded",
    "processRecreationDoesNotRestoreClipboardReadAuthority",
    "clipboardImplementationHasNoWorkerWakeLockOrBackgroundService",
}
EXPECTED_MANAGED_METHODS = {
    "ordinarySynchronizedClipCarriesSensitivePreviewFlag",
    "synchronizedClipDescriptionUsesGenericLabel",
}
EXPECTED_MANAGED_PROPERTIES = {"device": "pixel6Api35", "flavor": "playInternal", "project": ":app"}
SOURCE_PATHS = (
    "app/src/main/java/io/codecks/AppCompositionRoot.kt",
    "app/src/main/java/io/codecks/data/clipboard/ClipboardLastSyncStore.kt",
    "app/src/main/java/io/codecks/domain/clipboard/ClipboardBatteryPolicy.kt",
    "app/src/main/java/io/codecks/domain/clipboard/ClipboardReceipt.kt",
    "app/src/main/java/io/codecks/domain/clipboard/ClipboardSessionState.kt",
    "app/src/main/java/io/codecks/domain/clipboard/ClipboardSyncEngine.kt",
    "app/src/main/java/io/codecks/ui/clipboard/ClipboardBatterySettings.kt",
    "app/src/main/java/io/codecks/ui/clipboard/ClipboardClipFactory.kt",
    "app/src/main/java/io/codecks/ui/clipboard/ClipboardScreen.kt",
    "app/src/main/java/io/codecks/ui/clipboard/ClipboardStateAndPolicies.kt",
    "app/src/main/java/io/codecks/ui/clipboard/ClipboardViewModel.kt",
    "app/src/test/java/io/codecks/ui/clipboard/M15ClipboardCharacterizationTest.kt",
    "app/src/test/java/io/codecks/ui/clipboard/ClipboardStatusPolicyTest.kt",
    "app/src/androidTest/java/io/codecks/ui/clipboard/M15ClipboardPrivacyInstrumentedTest.kt",
    "tools/evidence/collect_m15_clipboard_battery.py",
    "tools/evidence/validate_m15_clipboard_battery.py",
    "tools/evidence/test_m15_clipboard_battery.py",
    "tools/evidence/schemas/autonomous-maturity-m15-clipboard-battery-v1.schema.json",
    "tools/evidence/managed_execution_binding.py",
)
MANAGED_RESULT = "tasks/test-evidence/m15/runtime/TEST-M15ClipboardPrivacyInstrumentedTest.xml"
TARGET_APK = "tasks/test-evidence/m15/runtime/app-playInternal-release.apk"
TEST_APK = "tasks/test-evidence/m15/runtime/app-playInternal-release-androidTest.apk"
PASS_LANES = (
    "cpu.visible_session_boundary",
    "cpu.battery_saver_policy",
    "cpu.duplicate_self_echo",
    "cpu.conflict_ordering",
    "cpu.large_payload_privacy",
    "cpu.last_sync_receipt_privacy",
    "cpu.system_preview_policy",
    "cpu.bounded_backoff",
    "cpu.process_recreation",
    "cpu.no_background_worker_wakelock",
)
NOT_RUN_LANES = (
    "emulator.foreground_background",
    "emulator.screen_off_on_lock",
    "emulator.battery_restrictions",
    "current_mac.sleep_wake_reconnect",
    "physical.samsung_clipboard_toast",
    "physical.battery_characterization",
)
MANAGED_PASS_LANE = "emulator.sensitive_clip_flag"


def safe_path(value: str) -> Path:
    candidate = Path(value)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise ValueError(f"unsafe repository path: {value}")
    resolved = (ROOT / candidate).resolve(strict=False)
    if not resolved.is_relative_to(ROOT.resolve()):
        raise ValueError(f"path escapes repository: {value}")
    relative = resolved.relative_to(ROOT.resolve())
    for index in range(1, len(relative.parts) + 1):
        if ROOT.joinpath(*relative.parts[:index]).is_symlink():
            raise ValueError(f"symlink forbidden: {value}")
    return resolved


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def apk_application_id(apk: Path) -> str:
    analyzer = Path(os.environ["ANDROID_HOME"]) / "cmdline-tools/latest/bin/apkanalyzer"
    return subprocess.run(
        [str(analyzer), "manifest", "application-id", str(apk)],
        check=True, capture_output=True, text=True,
    ).stdout.strip()


def sanitized_companion(suite: str, tests: int) -> bytes:
    return (
        "schema_version: 1\nartifact_kind: \"SANITIZED_METADATA_ONLY\"\n"
        f"device: \"pixel6Api35\"\nsuite: \"{suite}\"\ntests: {tests}\nfailures: 0\n"
    ).encode()


def parse_cpu_result(path: Path) -> tuple[str, list[str]]:
    root = ET.fromstring(path.read_bytes())
    if root.tag != "testsuite" or root.attrib.get("name") != TEST_CLASS:
        raise ValueError("M15 CPU XML suite identity mismatch")
    cases = root.findall("testcase")
    names = [node.attrib.get("name", "") for node in cases]
    if len(names) != len(set(names)) or set(names) != EXPECTED_METHODS:
        raise ValueError("M15 CPU XML method set is not exact")
    if any(node.attrib.get("classname") != TEST_CLASS for node in cases):
        raise ValueError("M15 CPU XML testcase classname mismatch")
    counts = {
        "tests": len(cases),
        "failures": sum(bool(node.findall("failure")) for node in cases),
        "errors": sum(bool(node.findall("error")) for node in cases),
        "skipped": sum(bool(node.findall("skipped")) for node in cases),
    }
    for key, expected in counts.items():
        if int(root.attrib.get(key, "-1")) != expected:
            raise ValueError(f"M15 CPU XML {key} count mismatch")
    if counts != {"tests": 10, "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError("M15 CPU XML is not a clean 10/10 result")
    if any((root.find(tag) is not None and (root.find(tag).text or "").strip()) for tag in ("system-out", "system-err")):
        raise ValueError("M15 CPU XML must not retain process output")
    timestamp = root.attrib.get("timestamp", "")
    datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    return timestamp, sorted(names)


def sanitized_result(raw_path: Path, output_path: Path) -> tuple[str, list[str]]:
    timestamp, methods = parse_cpu_result(raw_path)
    suite = ET.Element("testsuite", {
        "name": TEST_CLASS,
        "tests": "10",
        "failures": "0",
        "errors": "0",
        "skipped": "0",
        "timestamp": timestamp,
    })
    for method in methods:
        ET.SubElement(suite, "testcase", {"name": method, "classname": TEST_CLASS})
    output_path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(suite).write(output_path, encoding="utf-8", xml_declaration=True)
    return timestamp, methods


def parse_managed_result(path: Path) -> tuple[str, list[str]]:
    root = ET.fromstring(path.read_bytes())
    suites = list(root.findall("testsuite")) if root.tag == "testsuites" else [root]
    if len(suites) != 1:
        raise ValueError("M15 managed XML must contain exactly one suite")
    suite = suites[0]
    if suite.attrib.get("name") != MANAGED_TEST_CLASS:
        raise ValueError("M15 managed XML suite identity mismatch")
    properties = {
        node.attrib.get("name", ""): node.attrib.get("value", "")
        for node in suite.findall("./properties/property")
    }
    if properties != EXPECTED_MANAGED_PROPERTIES:
        raise ValueError("M15 managed XML device/flavor/project mismatch")
    cases = suite.findall("testcase")
    names = [node.attrib.get("name", "") for node in cases]
    if len(names) != len(set(names)) or set(names) != EXPECTED_MANAGED_METHODS:
        raise ValueError("M15 managed XML method set is not exact")
    if any(node.attrib.get("classname") != MANAGED_TEST_CLASS for node in cases):
        raise ValueError("M15 managed XML testcase classname mismatch")
    counts = {
        "tests": len(cases),
        "failures": sum(bool(node.findall("failure")) for node in cases),
        "errors": sum(bool(node.findall("error")) for node in cases),
        "skipped": sum(bool(node.findall("skipped")) for node in cases),
    }
    if counts != {"tests": 2, "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError("M15 managed XML is not a clean 2/2 result")
    for element in (root, suite):
        for key, expected in counts.items():
            if int(element.attrib.get(key, "-1")) != expected:
                raise ValueError(f"M15 managed XML {element.tag} {key} count mismatch")
    timestamp = suite.attrib.get("timestamp", "")
    datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    return timestamp, sorted(names)


def sanitized_managed_result(raw_path: Path, output_path: Path) -> tuple[str, list[str]]:
    timestamp, methods = parse_managed_result(raw_path)
    root = ET.Element("testsuites", {
        "tests": "2", "failures": "0", "errors": "0", "skipped": "0", "timestamp": timestamp,
    })
    suite = ET.SubElement(root, "testsuite", {
        "name": MANAGED_TEST_CLASS, "tests": "2", "failures": "0", "errors": "0", "skipped": "0", "timestamp": timestamp,
    })
    properties = ET.SubElement(suite, "properties")
    for name, value in EXPECTED_MANAGED_PROPERTIES.items():
        ET.SubElement(properties, "property", {"name": name, "value": value})
    for method in methods:
        ET.SubElement(suite, "testcase", {"name": method, "classname": MANAGED_TEST_CLASS})
    output_path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(output_path, encoding="utf-8", xml_declaration=True)
    return timestamp, methods


def collect(
    raw_result: str,
    sanitized_output: str,
    raw_managed_result: str,
    sanitized_managed_output: str,
    target_apk: str,
    test_apk: str,
) -> dict:
    raw_path = safe_path(raw_result)
    result_path = safe_path(sanitized_output)
    timestamp, methods = sanitized_result(raw_path, result_path)
    managed_path = safe_path(sanitized_managed_output)
    managed_timestamp, managed_methods = sanitized_managed_result(safe_path(raw_managed_result), managed_path)
    textproto = managed_path.parent / "test-result.textproto"
    textproto.write_bytes(sanitized_companion(MANAGED_TEST_CLASS, len(EXPECTED_MANAGED_METHODS)))
    target_path = safe_path(target_apk)
    test_path = safe_path(test_apk)
    target_id = apk_application_id(target_path)
    test_id = apk_application_id(test_path)
    if (target_id, test_id) != ("app.codecks.internal", "app.codecks.internal.test"):
        raise ValueError(f"M15 artifact identity mismatch: {target_id}, {test_id}")
    source_commit = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.strip()
    sources = [{"path": path, "sha256": sha256(safe_path(path))} for path in SOURCE_PATHS]
    lanes = [
        {"id": lane, "status": "PASS", "evidence": "CPU_UNIT", "code": "exact_test_passed"}
        for lane in PASS_LANES
    ] + [{
        "id": MANAGED_PASS_LANE,
        "status": "PASS",
        "evidence": "MANAGED_EMULATOR_PROXY",
        "code": "exact_test_passed",
    }] + [
        {"id": lane, "status": "NOT_RUN", "evidence": "RUNTIME_OR_EXTERNAL", "code": "held_or_unavailable"}
        for lane in NOT_RUN_LANES
    ]
    return {
        "schema": SCHEMA_ID,
        "milestone": "M15",
        "status": "PASS",
        "scope": "CPU_AND_MANAGED_SOURCE_ARTIFACT_BOUND",
        "generatedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "sourceCommit": source_commit,
        "sources": sources,
        "unitResult": {
            "path": sanitized_output,
            "sha256": sha256(result_path),
            "timestamp": timestamp,
            "className": TEST_CLASS,
            "methods": methods,
            "tests": 10,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
        },
        "managedResult": {
            "artifactKind": "SANITIZED_JUNIT_METADATA",
            "executedBinaryBinding": "APK_DIGESTS_BOUND",
            "path": sanitized_managed_output,
            "sha256": sha256(managed_path),
            "timestamp": managed_timestamp,
            "className": MANAGED_TEST_CLASS,
            "methods": managed_methods,
            "device": "pixel6Api35",
            "flavor": "playInternal",
            "tests": 2,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
            "targetArtifact": {
                "path": target_apk, "applicationId": target_id, "sha256": sha256(target_path),
            },
            "testArtifact": {
                "path": test_apk, "applicationId": test_id, "sha256": sha256(test_path),
            },
        },
        "managedExecution": collect_binding(
            ROOT, source_paths=SOURCE_PATHS, class_name=MANAGED_TEST_CLASS,
            methods=EXPECTED_MANAGED_METHODS, result_path=sanitized_managed_output,
            target_apk=target_apk, test_apk=test_apk,
        ),
        "managedCompanionPrivacy": {
            "kind": "SANITIZED_METADATA_ONLY", "sanitized": True,
            "path": textproto.relative_to(ROOT).as_posix(), "sha256": sha256(textproto),
        },
        "lanes": lanes,
        "privacy": {
            "clipboardContentRecorded": False,
            "processOutputRecorded": False,
            "hostIdentityRecorded": False,
            "credentialRecorded": False,
        },
        "summary": {"pass": len(PASS_LANES) + 1, "fail": 0, "notRun": len(NOT_RUN_LANES)},
        "limitations": [
            "CPU tests prove deterministic policy and source wiring, not Android lifecycle behavior.",
            "Managed API-35 metadata and exact APK digests bind the sensitive-flag proxy; this is not physical-device proof.",
            "Foreground/background, screen lock, battery restriction, Samsung toast, and physical battery lanes remain NOT_RUN.",
            "Mac sleep/wake and reconnect were not changed or exercised in this implementation phase.",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--result", required=True)
    parser.add_argument("--sanitized-result", required=True)
    parser.add_argument("--managed-result", required=True)
    parser.add_argument("--sanitized-managed-result", required=True)
    parser.add_argument("--target-apk", required=True)
    parser.add_argument("--test-apk", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        payload = collect(
            args.result, args.sanitized_result, args.managed_result, args.sanitized_managed_result,
            args.target_apk, args.test_apk,
        )
        output = safe_path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError, subprocess.CalledProcessError, ET.ParseError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print(f"PASS: M15 CPU implementation receipt sealed; runtime lanes {len(NOT_RUN_LANES)} NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
