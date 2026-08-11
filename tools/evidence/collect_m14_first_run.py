#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import platform
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

SOURCE_FILES = [
    "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt",
    "app/src/test/java/io/codecks/ui/connection/FirstRunRepairBenchmarkTest.kt",
    "app/src/androidTestPlayInternal/java/io/codecks/ui/settings/M14FirstRunRepairInstrumentedTest.kt",
    "scripts/run_m14_first_run_benchmark.sh",
    "scripts/run_m14_managed_ui.sh",
    "tools/evidence/collect_m14_first_run.py",
    "tools/evidence/combine_m14_managed_attempts.py",
    "tools/evidence/validate_m14_first_run.py",
    "tools/evidence/test_m14_first_run.py",
]
UNIT_CLASS = "io.codecks.ui.connection.FirstRunRepairBenchmarkTest"
UNIT_METHODS = {
    "oneHundredDistinctCleanProfilesMeetM14Gate",
    "corpusHasTenMaterialVariantsPerScenario",
    "receiptContainsNoSecretOrRawEndpointMaterial",
}
MANAGED_CLASS = "io.codecks.ui.settings.M14FirstRunRepairInstrumentedTest"
MANAGED_METHODS = {
    "authFailureOpensAndFocusesCredentialEditor",
    "sleepingMacRetryInvokesTestCallback",
    "changedIdentityRepairInvokesResetTrustCallback",
    "directResetTrustRequiresConfirmation",
}
FAILED_ATTEMPT_PATH = "tasks/test-evidence/m14/runtime/TEST-pixel6Api35-M14FirstRunRepairInstrumentedTest.failed-attempt.xml"
RETRY_PATH = "tasks/test-evidence/m14/runtime/TEST-pixel6Api35-M14FirstRunRepairInstrumentedTest.retry.xml"
COMMANDS = [
    ":app:testOssReleaseUnitTest --tests io.codecks.ui.connection.FirstRunRepairBenchmarkTest",
    ":app:compilePlayInternalReleaseAndroidTestKotlin",
    ":app:pixel6Api35PlayInternalReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.codecks.ui.settings.M14FirstRunRepairInstrumentedTest",
    ":app:pixel6Api35PlayInternalReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.codecks.ui.settings.M14FirstRunRepairInstrumentedTest#directResetTrustRequiresConfirmation",
    "python3 tools/evidence/combine_m14_managed_attempts.py --failed <failed-attempt> --retry <filtered-retry> --output <combined-result>",
    "python3 tools/evidence/validate_m14_first_run.py tasks/test-evidence/autonomous-maturity-m14-first-run.json",
    "python3 -m unittest tools/evidence/test_m14_first_run.py",
]


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical(value) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()


def receipt_digest(data: dict) -> str:
    body = dict(data)
    body.pop("receiptDigest", None)
    return sha256_bytes(canonical(body))


def exact_suite(path: Path, suite_class: str, methods: set[str], properties: dict[str, str] | None = None) -> dict:
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
    if len(suites) != 1 or (root.tag == "testsuites" and len(list(root)) != 1):
        raise ValueError("exactly one test suite is required")
    suite = suites[0]
    if suite.attrib.get("name") != suite_class:
        raise ValueError("foreign test suite")
    for node in (root, suite):
        if int(node.attrib.get("tests", "-1")) != len(methods) or any(
            int(node.attrib.get(name, "-1")) != 0 for name in ("failures", "errors", "skipped")
        ):
            raise ValueError("test suite did not pass exactly")
    cases = list(suite.findall("testcase"))
    identities = [(case.attrib.get("classname"), case.attrib.get("name")) for case in cases]
    if len(identities) != len(set(identities)) or set(identities) != {(suite_class, method) for method in methods}:
        raise ValueError("duplicate, missing, or foreign test case")
    actual_properties = {
        node.attrib.get("name"): node.attrib.get("value")
        for node in suite.findall("./properties/property")
    }
    if properties is not None and actual_properties != properties:
        raise ValueError(f"test properties mismatch: {actual_properties}")
    return {"suiteClass": suite_class, "methods": sorted(methods), "passed": len(methods)}


def apk_application_id(apkanalyzer: Path, apk: Path) -> str:
    return subprocess.check_output(
        [str(apkanalyzer), "manifest", "application-id", str(apk)], text=True
    ).strip()


def execution_environment() -> dict:
    return {
        "androidSdkApi": 35,
        "architecture": platform.machine(),
        "fakeClock": True,
        "hostOs": platform.system(),
        "network": "NOT_USED",
        "physicalDevice": "NOT_USED",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--junit", type=Path, required=True)
    parser.add_argument("--compose-junit", type=Path, required=True)
    parser.add_argument("--managed-failed-attempt", type=Path, required=True)
    parser.add_argument("--managed-retry", type=Path, required=True)
    parser.add_argument("--target-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    data = json.loads(args.raw.read_text())
    expected_top = {
        "schemaVersion", "milestone", "profile", "total", "successes", "successRatePercent",
        "silentDeadEnds", "gate", "moderatedHumanPairing", "profiles", "journeys",
    }
    if set(data) != expected_top:
        raise ValueError("raw input has missing or unknown top-level fields")
    unit = exact_suite(args.junit, UNIT_CLASS, UNIT_METHODS)
    managed = exact_suite(
        args.compose_junit,
        MANAGED_CLASS,
        MANAGED_METHODS,
        {"device": "pixel6Api35", "flavor": "playInternal", "project": ":app"},
    )
    apkanalyzer = Path(os.environ["ANDROID_HOME"]) / "cmdline-tools/latest/bin/apkanalyzer"
    target_id = apk_application_id(apkanalyzer, args.target_apk)
    test_id = apk_application_id(apkanalyzer, args.test_apk)
    if target_id != "app.codecks.internal" or test_id != "app.codecks.internal.test":
        raise ValueError(f"artifact identity mismatch: {target_id}, {test_id}")
    source_digests = {path: sha256_bytes((root / path).read_bytes()) for path in SOURCE_FILES}
    commands = COMMANDS
    environment = execution_environment()
    base_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    data["bindings"] = {
        "baseCommit": base_commit,
        "sourceDigests": source_digests,
        "sourceTreeDigest": sha256_bytes(canonical(source_digests)),
        "corpusDigest": sha256_bytes(canonical(data["profiles"])),
        "canonicalInputDigest": sha256_bytes(canonical({"profiles": data["profiles"], "journeys": data["journeys"]})),
        "unit": unit | {
            "resultPath": "tasks/test-evidence/m14/runtime/TEST-FirstRunRepairBenchmarkTest.xml",
            "resultDigest": sha256_bytes(args.junit.read_bytes()),
        },
        "managed": managed | {
            "device": "pixel6Api35",
            "flavor": "playInternalRelease",
            "project": ":app",
            "resultPath": "tasks/test-evidence/m14/runtime/TEST-pixel6Api35-M14FirstRunRepairInstrumentedTest.xml",
            "resultDigest": sha256_bytes(args.compose_junit.read_bytes()),
            "attempts": [
                {"kind": "FAILED_HARNESS_ATTEMPT", "path": FAILED_ATTEMPT_PATH,
                 "digest": sha256_bytes(args.managed_failed_attempt.read_bytes())},
                {"kind": "FILTERED_HARNESS_RETRY", "path": RETRY_PATH,
                 "digest": sha256_bytes(args.managed_retry.read_bytes())},
            ],
            "targetArtifact": {
                "path": str(args.target_apk), "applicationId": target_id,
                "digest": sha256_bytes(args.target_apk.read_bytes()),
            },
            "testArtifact": {
                "path": str(args.test_apk), "applicationId": test_id,
                "digest": sha256_bytes(args.test_apk.read_bytes()),
            },
        },
        "commands": commands,
        "commandDigest": sha256_bytes(canonical(commands)),
        "environment": environment,
        "environmentDigest": sha256_bytes(canonical(environment)),
    }
    data["receiptDigest"] = receipt_digest(data)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, sort_keys=True, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
