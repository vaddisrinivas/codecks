#!/usr/bin/env python3
"""Create or verify a fail-closed M10 managed-device receipt."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

SCHEMA = "codecks.m10-managed-receipt.v1"
PACKAGE = "app.codecks.debug"
TEST_CLASS = "io.codecks.maturity.M10ManagedMatrixInstrumentedTest"
TESTS = {
    "installArtifactAndIdentityStayInTheIsolatedDebugLane",
    "startupRotationRecreationAndLargeWindowBasicsDoNotCrash",
    "persistedInstallMarkerAndLegacyTargetMigrationSurviveRecreation",
    "localeAndDarkLightThemeChangesRemainCrashFree",
    "offlinePermissionAndBackgroundRestrictionTogglesAreRecoverable",
}
SOURCE_PATHS = (
    "app/build.gradle.kts",
    "app/src/androidTestDebug/java/io/codecks/maturity/M10ManagedMatrixInstrumentedTest.kt",
    "scripts/run_m10_managed_profile.sh",
    "tools/evidence/m10_managed_receipt.py",
)


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def require_file(path: Path, label: str) -> Path:
    if not path.is_file() or path.is_symlink() or path.stat().st_size <= 0:
        raise ValueError(f"{label} missing or unsafe: {path}")
    return path.resolve()


def relative_inside(path: Path, root: Path, label: str) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError as error:
        raise ValueError(f"{label} escapes its allowed root") from error


def source_binding(root: Path) -> dict:
    files = {name: digest(require_file(root / name, f"source {name}")) for name in SOURCE_PATHS}
    combined = hashlib.sha256()
    for name, value in files.items():
        combined.update(f"{name}\0{value}\n".encode())
    commit = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=root, check=True, capture_output=True, text=True
    ).stdout.strip()
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("source commit is not canonical")
    return {"commit": commit, "files": files, "digest": combined.hexdigest()}


def result_binding(result_xml: Path, device_name: str) -> dict:
    root = ET.parse(require_file(result_xml, "managed result XML")).getroot()
    tests = int(root.attrib.get("tests", "-1"))
    failures = int(root.attrib.get("failures", "-1"))
    errors = int(root.attrib.get("errors", "-1"))
    skipped = int(root.attrib.get("skipped", "-1"))
    suites = list(root.findall("testsuite"))
    if tests != len(TESTS) or failures or errors or skipped or len(suites) != 1:
        raise ValueError("managed result is not an exact clean five-test pass")
    suite = suites[0]
    properties = {item.attrib.get("name"): item.attrib.get("value") for item in suite.findall("./properties/property")}
    if properties.get("device") != device_name or properties.get("flavor") != "oss" or properties.get("project") != ":app":
        raise ValueError("managed result device/flavor/project binding mismatch")
    cases = {(item.attrib.get("classname"), item.attrib.get("name")) for item in suite.findall("testcase")}
    if cases != {(TEST_CLASS, name) for name in TESTS}:
        raise ValueError("managed result test identity mismatch")
    return {"sha256": digest(result_xml), "tests": tests, "failures": failures, "errors": errors, "skipped": skipped}


def apk_binding(path: Path, root: Path, label: str) -> dict:
    path = require_file(path, label)
    with path.open("rb") as handle:
        if handle.read(2) != b"PK":
            raise ValueError(f"{label} is not an APK zip")
    return {"path": relative_inside(path, root, label), "sha256": digest(path), "bytes": path.stat().st_size}


def create(args: argparse.Namespace) -> dict:
    root, sdk = args.root.resolve(), args.sdk.resolve()
    if args.api not in range(31, 37) or args.shape not in {"compact", "standard", "tablet"}:
        raise ValueError("matrix coordinate outside M10")
    profile = {"compact": "Compact", "standard": "Standard", "tablet": "Tablet"}[args.shape]
    device_name = f"m10{profile}Api{args.api}"
    if args.task != f":app:{device_name}OssDebugAndroidTest":
        raise ValueError("managed task binding mismatch")
    result_dir = args.result_dir.resolve()
    if result_dir.name != device_name:
        raise ValueError("managed result directory mismatch")
    result_xmls = list(result_dir.glob("TEST-*.xml"))
    if len(result_xmls) != 1:
        raise ValueError("expected exactly one managed result XML")
    result_xml = result_xmls[0]
    device_info = require_file(result_dir / "device-info.pb", "device info")
    textproto = require_file(result_dir / "test-result.textproto", "test result proto")
    text = textproto.read_text(encoding="utf-8", errors="strict")
    if "scheduled_test_case_count: 5" not in text or "test_status: PASSED" not in text or "test_status: FAILED" in text:
        raise ValueError("managed proto is not a clean five-test pass")
    image_package = require_file(args.image_package, "system image package")
    return {
        "schema": SCHEMA,
        "status": "PASS",
        "evidence": "AUTONOMOUS_PROXY",
        "package": PACKAGE,
        "coordinate": {"api": args.api, "shape": args.shape, "task": args.task},
        "source": source_binding(root),
        "artifacts": {
            "targetApk": apk_binding(args.target_apk, root, "target APK"),
            "testApk": apk_binding(args.test_apk, root, "test APK"),
        },
        "device": {
            "managedName": device_name,
            "systemImageSource": "aosp",
            "packagePath": relative_inside(image_package, sdk, "system image package"),
            "packageXmlSha256": digest(image_package),
            "deviceInfoPath": relative_inside(device_info, root, "device info"),
            "deviceInfoSha256": digest(device_info),
        },
        "result": {
            "xmlPath": relative_inside(result_xml, root, "result XML"),
            **result_binding(result_xml, device_name),
            "textprotoPath": relative_inside(textproto, root, "test result proto"),
            "textprotoSha256": digest(textproto),
        },
    }


def validate(receipt: dict, root: Path, sdk: Path) -> None:
    if set(receipt) != {"schema", "status", "evidence", "package", "coordinate", "source", "artifacts", "device", "result"}:
        raise ValueError("receipt top-level schema mismatch")
    if (receipt["schema"], receipt["status"], receipt["evidence"], receipt["package"]) != (SCHEMA, "PASS", "AUTONOMOUS_PROXY", PACKAGE):
        raise ValueError("receipt identity mismatch")
    coordinate = receipt["coordinate"]
    shape, api = coordinate.get("shape"), coordinate.get("api")
    profile = {"compact": "Compact", "standard": "Standard", "tablet": "Tablet"}.get(shape)
    if profile is None or api not in range(31, 37):
        raise ValueError("receipt coordinate invalid")
    device_name = f"m10{profile}Api{api}"
    if coordinate.get("task") != f":app:{device_name}OssDebugAndroidTest":
        raise ValueError("receipt task mismatch")
    if receipt["source"] != source_binding(root):
        raise ValueError("receipt source binding is stale")
    for key, label in (("targetApk", "target APK"), ("testApk", "test APK")):
        item = receipt["artifacts"].get(key, {})
        if item != apk_binding(root / item.get("path", ""), root, label):
            raise ValueError(f"receipt {label} binding is stale")
    device = receipt["device"]
    if device.get("managedName") != device_name or device.get("systemImageSource") != "aosp":
        raise ValueError("receipt managed device mismatch")
    image_path = device.get("packagePath")
    expected_image = re.fullmatch(
        rf"system-images/android-{api}/default/(arm64-v8a|x86_64)/package\.xml",
        image_path if isinstance(image_path, str) else "",
    )
    if expected_image is None:
        raise ValueError("receipt system image coordinate mismatch")
    image = require_file(sdk / image_path, "system image package")
    if digest(image) != device.get("packageXmlSha256"):
        raise ValueError("receipt system image binding is stale")
    device_info = require_file(root / device.get("deviceInfoPath", ""), "device info")
    if digest(device_info) != device.get("deviceInfoSha256"):
        raise ValueError("receipt device info binding is stale")
    result = receipt["result"]
    result_xml = require_file(root / result.get("xmlPath", ""), "result XML")
    if result_binding(result_xml, device_name) != {key: result.get(key) for key in ("sha256", "tests", "failures", "errors", "skipped")}:
        raise ValueError("receipt result XML binding is stale")
    textproto = require_file(root / result.get("textprotoPath", ""), "test result proto")
    if digest(textproto) != result.get("textprotoSha256"):
        raise ValueError("receipt result proto binding is stale")


def write_atomic(path: Path, receipt: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", dir=path.parent, delete=False, encoding="utf-8") as handle:
        json.dump(receipt, handle, indent=2, sort_keys=True)
        handle.write("\n")
        temporary = Path(handle.name)
    os.replace(temporary, path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--verify", type=Path)
    parser.add_argument("--api", type=int)
    parser.add_argument("--shape")
    parser.add_argument("--task")
    parser.add_argument("--result-dir", type=Path)
    parser.add_argument("--target-apk", type=Path)
    parser.add_argument("--test-apk", type=Path)
    parser.add_argument("--image-package", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.verify:
        receipt = json.loads(args.verify.read_text(encoding="utf-8"))
        validate(receipt, args.root.resolve(), args.sdk.resolve())
        print(f"M10_RECEIPT_PASS {args.verify}")
        return
    required = (args.api, args.shape, args.task, args.result_dir, args.target_apk, args.test_apk, args.image_package, args.output)
    if any(value is None for value in required):
        parser.error("generation requires all coordinate, artifact, image, result, and output arguments")
    receipt = create(args)
    validate(receipt, args.root.resolve(), args.sdk.resolve())
    write_atomic(args.output, receipt)
    print(f"M10_RECEIPT_PASS {args.output}")


if __name__ == "__main__":
    main()
