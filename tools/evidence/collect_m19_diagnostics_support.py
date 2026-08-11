#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ID = "codecks.autonomous-maturity.m19-diagnostics-support.v1"
TEST_CLASS = "io.codecks.data.privacy.M19DiagnosticsSupportTest"
EXPECTED_METHODS = {
    "closedSchemaAcceptsOnlyKnownBoundedTypedPayload",
    "bundleIsBoundedAndContainsNoContentBearingFieldsOrCanaries",
    "receiptsAreDerivedFromTerminalTypedEventsAndBounded",
    "receiptsOmitUncapturedSupportCodesAndRejectInjectedCodes",
    "everyInjectedFailureHasActionableM06DiagnosisAndPublicDocumentation",
    "releaseSettingsExposeRedactedSupportExport",
    "productionSupportCodesComeOnlyFromCanonicalTypedRegistry",
}
SOURCE_PATHS = (
    "app/src/main/java/io/codecks/AppCompositionRoot.kt",
    "app/src/main/java/io/codecks/AppHelperRuntime.kt",
    "app/src/main/java/io/codecks/AppDestinationSupport.kt",
    "app/src/main/java/io/codecks/data/privacy/SupportBundleBuilder.kt",
    "app/src/main/java/io/codecks/data/privacy/SupportBundleSchemaValidator.kt",
    "app/src/main/java/io/codecks/domain/privacy/SupportBundle.kt",
    "app/src/main/java/io/codecks/ui/connection/UnifiedConnectionPresentation.kt",
    "app/src/main/java/io/codecks/ui/connection/ConnectionDiagnosticPresenter.kt",
    "app/src/main/java/io/codecks/ui/clipboard/ClipboardScreen.kt",
    "app/src/main/java/io/codecks/ui/keyboard/HidHostHeader.kt",
    "app/src/main/java/io/codecks/ui/keyboard/KeyboardViewModel.kt",
    "app/src/main/java/io/codecks/ui/settings/CodecksHelperUiState.kt",
    "app/src/main/java/io/codecks/ui/settings/SettingsScreen.kt",
    "app/src/main/java/io/codecks/ui/settings/SettingsThemeSections.kt",
    "app/src/main/java/io/codecks/ui/settings/SupportBundleDialog.kt",
    "app/src/main/java/io/codecks/ui/settings/SupportBundleViewModel.kt",
    "app/src/test/java/io/codecks/data/privacy/M19DiagnosticsSupportTest.kt",
    "app/src/test/java/io/codecks/ui/connection/ConnectionDiagnosticPresenterTest.kt",
    "app/src/test/java/io/codecks/ui/connection/FirstRunRepairBenchmarkTest.kt",
    "app/src/test/java/io/codecks/ui/connection/UnifiedConnectionPresentationTest.kt",
    "app/src/test/java/io/codecks/ui/connection/SupportFailureInjection.kt",
    "app/src/test/java/io/codecks/data/privacy/SupportBundleBuilderTest.kt",
    "app/src/test/java/io/codecks/ui/settings/SupportBundleViewModelTest.kt",
    "docs/support/TROUBLESHOOTING.md",
    "tools/evidence/collect_m19_diagnostics_support.py",
    "tools/evidence/test_m19_diagnostics_support.py",
    "tools/evidence/validate_m19_diagnostics_support.py",
    "tools/evidence/schemas/autonomous-maturity-m19-diagnostics-support-v1.schema.json",
)
PASS_LANES = (
    "cpu.closed_export_schema",
    "cpu.secret_canary_exclusion",
    "cpu.bounded_operation_receipts",
    "cpu.injected_failure_diagnosis",
    "cpu.production_support_surface",
)
NOT_RUN_LANES = (
    "managed.support_surface_ui",
    "physical.support_export_share",
    "human.clean_machine_diagnosis",
)
VALIDATOR_POSITIVE_CASES = 1
VALIDATOR_NEGATIVE_CASES = 17


def safe_path(value: str) -> Path:
    candidate = Path(value)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise ValueError(f"unsafe path: {value}")
    for index in range(1, len(candidate.parts) + 1):
        if ROOT.joinpath(*candidate.parts[:index]).is_symlink():
            raise ValueError(f"symlink forbidden: {value}")
    resolved = (ROOT / candidate).resolve(strict=False)
    if not resolved.is_relative_to(ROOT.resolve()):
        raise ValueError(f"path escapes repository: {value}")
    return resolved


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_result(path: Path) -> list[str]:
    root = ET.fromstring(path.read_bytes())
    if root.tag != "testsuite" or root.attrib.get("name") != TEST_CLASS:
        raise ValueError("M19 suite identity mismatch")
    cases = root.findall("testcase")
    methods = [case.attrib.get("name", "") for case in cases]
    if len(methods) != len(set(methods)) or set(methods) != EXPECTED_METHODS:
        raise ValueError("M19 method set mismatch")
    if any(case.attrib.get("classname") != TEST_CLASS for case in cases):
        raise ValueError("M19 testcase class mismatch")
    counts = {
        "tests": len(cases),
        "failures": sum(bool(case.findall("failure")) for case in cases),
        "errors": sum(bool(case.findall("error")) for case in cases),
        "skipped": sum(bool(case.findall("skipped")) for case in cases),
    }
    if counts != {"tests": 7, "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError("M19 result is not clean 7/7")
    for key, expected in counts.items():
        if int(root.attrib.get(key, "-1")) != expected:
            raise ValueError(f"M19 XML {key} mismatch")
    return sorted(methods)


def sanitize_result(raw: Path, output: Path) -> list[str]:
    methods = parse_result(raw)
    suite = ET.Element("testsuite", {
        "name": TEST_CLASS, "tests": "7", "failures": "0", "errors": "0", "skipped": "0",
    })
    for method in methods:
        ET.SubElement(suite, "testcase", {"name": method, "classname": TEST_CLASS})
    output.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(suite).write(output, encoding="utf-8", xml_declaration=True)
    return methods


def canonical_digest(data: dict) -> str:
    payload = dict(data)
    payload.pop("receiptDigest", None)
    return hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def collect(raw_result: str, sanitized_output: str) -> dict:
    result_path = safe_path(sanitized_output)
    methods = sanitize_result(safe_path(raw_result), result_path)
    source_commit = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.strip()
    data = {
        "schema": SCHEMA_ID,
        "milestone": "M19",
        "status": "PASS",
        "scope": "CPU_SOURCE_BOUND",
        "sourceCommit": source_commit,
        "sources": [{"path": path, "sha256": sha256(safe_path(path))} for path in SOURCE_PATHS],
        "unitResult": {
            "path": sanitized_output,
            "sha256": sha256(result_path),
            "className": TEST_CLASS,
            "methods": methods,
            "tests": 7,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
        },
        "lanes": [
            {"id": lane, "status": "PASS", "evidence": "CPU_UNIT"} for lane in PASS_LANES
        ] + [
            {"id": lane, "status": "NOT_RUN", "evidence": "EXTERNAL"} for lane in NOT_RUN_LANES
        ],
        "privacy": {
            "rawContentRecorded": False,
            "endpointIdentityRecorded": False,
            "personalIdentifierRecorded": False,
            "commercialIdentifierRecorded": False,
        },
        "summary": {"pass": 5, "fail": 0, "notRun": 3},
        "validatorCases": {"positive": VALIDATOR_POSITIVE_CASES, "negative": VALIDATOR_NEGATIVE_CASES},
        "limitations": [
            "CPU evidence does not prove Android share-picker or lifecycle behavior.",
            "Physical-device export and vendor clipboard UI behavior remain external.",
            "Human clean-machine troubleshooting comprehension remains external.",
        ],
    }
    data["receiptDigest"] = canonical_digest(data)
    return data


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-result", required=True)
    parser.add_argument("--sanitized-result", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    data = collect(args.raw_result, args.sanitized_result)
    output = safe_path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(data, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
