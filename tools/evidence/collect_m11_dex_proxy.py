#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

from managed_execution_binding import collect_binding

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = {
    "managedWindow1280x720", "managedWindow1920x1080",
    "freeformWindow1280x720", "freeformWindow1920x1080",
    "secondaryDisplay1280x720", "secondaryDisplay1920x1080",
}
CLASSNAME = "io.codecks.internalquality.M11DexProxyInstrumentedTest"
EXPECTED_PROPERTIES = {"device": "pixel6Api35", "flavor": "playInternal", "project": ":app"}
CHECKS = [
    "managed_window", "actual_deck_action", "actual_trackpad_mouse_down_up",
    "product_support_dialog", "bounded_dialog_content", "product_app_shell_navigation",
    "secondary_display_presentation_proxy", "rotation", "keyboard", "focus",
    "window_restore", "cold_activity_restart_proxy",
]
SOURCE_PATHS = (
    "app/src/playInternal/java/io/codecks/internalquality/M11DexProxyActivity.kt",
    "app/src/androidTestPlayInternal/java/io/codecks/internalquality/M11DexProxyInstrumentedTest.kt",
    "scripts/run_m11_dex_proxy.sh",
    "tools/evidence/collect_m11_dex_proxy.py",
    "tools/evidence/managed_execution_binding.py",
    "tools/evidence/validate_m11_dex_proxy.py",
    "tools/evidence/test_m11_dex_proxy.py",
    "tools/evidence/schemas/autonomous-maturity-m11-dex-proxy-v1.schema.json",
)
TARGET_APK = "tasks/test-evidence/m11/runtime/app-playInternal-release.apk"
TEST_APK = "tasks/test-evidence/m11/runtime/app-playInternal-release-androidTest.apk"


def safe_path(value: str) -> Path:
    path = Path(value)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"unsafe repository path: {value}")
    resolved = ROOT / path
    if not resolved.resolve(strict=False).is_relative_to(ROOT.resolve()):
        raise ValueError(f"path escapes repository: {value}")
    relative = resolved.relative_to(ROOT)
    if any((ROOT.joinpath(*relative.parts[:index])).is_symlink() for index in range(1, len(relative.parts) + 1)):
        raise ValueError(f"symlink forbidden: {value}")
    return resolved


def parse_result(raw: bytes) -> dict:
    root = ET.fromstring(raw)
    suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
    if len(suites) != 1:
        raise ValueError("M11 XML must contain exactly one suite")
    suite = suites[0]
    if suite.attrib.get("name") != CLASSNAME:
        raise ValueError("M11 XML suite classname mismatch")
    property_nodes = suite.findall("./properties/property")
    properties = {node.attrib.get("name", ""): node.attrib.get("value", "") for node in property_nodes}
    if len(properties) != len(property_nodes) or properties != EXPECTED_PROPERTIES:
        raise ValueError(f"M11 XML device/flavor/project properties mismatch: {properties}")
    cases = suite.findall("testcase")
    names = [case.attrib.get("name", "") for case in cases]
    if len(names) != len(set(names)):
        raise ValueError("M11 XML contains duplicate testcase names")
    if any(case.attrib.get("classname") != CLASSNAME for case in cases):
        raise ValueError("M11 XML testcase classname mismatch")
    methods = set(names)
    failures = sum(bool(case.findall("failure")) for case in cases)
    errors = sum(bool(case.findall("error")) for case in cases)
    skipped = sum(bool(case.findall("skipped")) for case in cases)
    counts = {"tests": len(cases), "failures": failures, "errors": errors, "skipped": skipped}
    for element in (root, suite):
        for key, expected in counts.items():
            try:
                actual = int(element.attrib[key])
            except (KeyError, ValueError) as exc:
                raise ValueError(f"M11 XML {element.tag} has invalid {key} count") from exc
            if actual != expected:
                raise ValueError(f"M11 XML {element.tag} {key} count mismatch")
    passed = {
        case.attrib.get("name", "") for case in cases
        if not case.findall("failure") and not case.findall("error") and not case.findall("skipped")
    }
    timestamp = suite.attrib.get("timestamp", "")
    if root.tag == "testsuites" and root.attrib.get("timestamp") != timestamp:
        raise ValueError("M11 XML root/suite timestamps mismatch")
    try:
        datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("M11 XML suite timestamp is invalid") from exc
    return {**counts, "passed": passed, "failed": methods - passed, "methods": methods, "timestamp": timestamp}


def collect(result_paths: list[str]) -> dict:
    artifacts = []
    observed: set[str] = set()
    previous_timestamp: datetime | None = None
    for result_path in result_paths:
        result = safe_path(result_path)
        raw = result.read_bytes()
        parsed = parse_result(raw)
        methods, passed, failed = parsed["methods"], parsed["passed"], parsed["failed"]
        current_timestamp = datetime.fromisoformat(parsed["timestamp"].replace("Z", "+00:00"))
        if previous_timestamp is not None and current_timestamp <= previous_timestamp:
            raise ValueError("M11 result artifacts are not in strict chronological order")
        previous_timestamp = current_timestamp
        if not methods or not methods <= EXPECTED:
            raise ValueError(f"M11 result contains empty or unexpected methods: {sorted(methods)}")
        overlap = observed & passed
        if overlap:
            raise ValueError(f"M11 results duplicate passing methods: {sorted(overlap)}")
        late_failure = observed & failed
        if late_failure:
            raise ValueError(f"M11 result regresses methods after PASS: {sorted(late_failure)}")
        observed |= passed
        artifacts.append({
            "path": result_path,
            "sha256": hashlib.sha256(raw).hexdigest(),
            "timestamp": parsed["timestamp"],
            "passedMethods": sorted(passed),
            "failedMethods": sorted(failed),
            **{key: parsed[key] for key in ("tests", "failures", "errors", "skipped")},
        })
    if observed != EXPECTED:
        raise ValueError(f"M11 result union is not exact: methods={sorted(observed)}")
    if len(artifacts) == 1:
        if artifacts[0]["failedMethods"]:
            raise ValueError("single M11 artifact must be a clean 6/6 run")
    elif len(artifacts) == 2:
        initial, corrected = artifacts
        if not initial["failedMethods"] or corrected["failedMethods"] or set(corrected["passedMethods"]) != set(initial["failedMethods"]):
            raise ValueError("M11 rerun must exactly correct the earlier failed methods")
    else:
        raise ValueError("M11 receipt supports one clean run or one bounded corrective rerun")
    receipt = {
        "schema": "codecks.autonomous-maturity.m11-dex-proxy.v1",
        "milestone": "M11",
        "status": "PASS",
        "scope": "EMULATOR_PROXY_ONLY",
        "artifacts": artifacts,
        "profiles": [
            {"resolution": resolution, "status": "PASS", "checks": CHECKS}
            for resolution in ("1280x720", "1920x1080")
        ],
        "external": [{
            "id": "samsung_dex_physical",
            "status": "NOT_RUN",
            "reason": "No physical Samsung device or vendor DeX environment was used; emulator proxies cannot prove Samsung window-manager behavior.",
        }],
        "limitations": [
            "The production support dialog is bounded at both display sizes; Android and Samsung task freeform mode remain NOT_RUN.",
            "Overlay-display Presentation proves a secondary-display context proxy, not task-host movement on Samsung DeX.",
            "Activity recreation and cold activity restart are process/window restoration proxies, not low-memory process death.",
            "Real mouse DOWN/UP and keyboard events are emulator-injected through production input surfaces in an internal-only host.",
        ],
    }
    if len(result_paths) != 1:
        raise ValueError("current M11 receipt requires one exact clean run")
    receipt["managedExecution"] = collect_binding(
        ROOT,
        source_paths=SOURCE_PATHS,
        class_name=CLASSNAME,
        methods=EXPECTED,
        result_path=result_paths[0],
        target_apk=TARGET_APK,
        test_apk=TEST_APK,
    )
    return receipt


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--result", required=True, action="append")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        payload = collect(args.result)
        output = safe_path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError, ET.ParseError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print("PASS: collected exact 6-test emulator-only M11 receipt; Samsung DeX physical NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
