#!/usr/bin/env python3
"""Strict source/APK/JUnit/device binding for API-35 PlayInternal managed evidence."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

EXPECTED_PROPERTIES = {"device": "pixel6Api35", "flavor": "playInternal", "project": ":app"}
MAX_XML_BYTES = 4 * 1024 * 1024


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def safe_path(root: Path, relative: str) -> Path:
    value = Path(relative)
    if value.is_absolute() or ".." in value.parts:
        raise ValueError(f"unsafe path: {relative}")
    resolved = (root / value).resolve(strict=False)
    if not resolved.is_relative_to(root.resolve()):
        raise ValueError(f"path escapes root: {relative}")
    cursor = root.resolve()
    for part in value.parts:
        cursor /= part
        if cursor.is_symlink():
            raise ValueError(f"symlink forbidden: {relative}")
    return resolved


def apk_id(apk: Path) -> str:
    analyzer = Path(os.environ["ANDROID_HOME"]) / "cmdline-tools/latest/bin/apkanalyzer"
    return subprocess.run(
        [str(analyzer), "manifest", "application-id", str(apk)],
        check=True, capture_output=True, text=True,
    ).stdout.strip()


def parse_exact_result(path: Path, class_name: str, methods: set[str]) -> dict:
    raw = path.read_bytes()
    if len(raw) > MAX_XML_BYTES or b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
        raise ValueError("managed XML oversized or unsafe")
    root = ET.fromstring(raw)
    suites = list(root.findall("testsuite")) if root.tag == "testsuites" else [root]
    if len(suites) != 1:
        raise ValueError("managed XML must contain exactly one suite")
    suite = suites[0]
    if suite.attrib.get("name") != class_name:
        raise ValueError("managed suite identity mismatch")
    properties = {
        node.attrib.get("name", ""): node.attrib.get("value", "")
        for node in suite.findall("./properties/property")
    }
    if properties != EXPECTED_PROPERTIES:
        raise ValueError(f"managed device properties mismatch: {properties}")
    cases = suite.findall("testcase")
    identities = [(case.attrib.get("classname", ""), case.attrib.get("name", "")) for case in cases]
    expected = {(class_name, method) for method in methods}
    if len(identities) != len(set(identities)) or set(identities) != expected:
        raise ValueError("managed testcase set is not exact")
    counts = {
        "tests": len(cases),
        "failures": sum(bool(case.findall("failure")) for case in cases),
        "errors": sum(bool(case.findall("error")) for case in cases),
        "skipped": sum(bool(case.findall("skipped")) for case in cases),
    }
    if counts != {"tests": len(methods), "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError("managed result is not an exact clean pass")
    for node in (root, suite):
        for key, expected_count in counts.items():
            if int(node.attrib.get(key, "-1")) != expected_count:
                raise ValueError(f"managed XML {node.tag} {key} count mismatch")
    return {"className": class_name, "methods": sorted(methods), **counts, "properties": properties}


def collect_binding(
    root: Path,
    *,
    source_paths: tuple[str, ...],
    class_name: str,
    methods: set[str],
    result_path: str,
    target_apk: str,
    test_apk: str,
    source_commit: str | None = None,
) -> dict:
    root = root.resolve()
    result = safe_path(root, result_path)
    target = safe_path(root, target_apk)
    test = safe_path(root, test_apk)
    device_info = result.parent / "device-info.pb"
    textproto = result.parent / "test-result.textproto"
    for path in (result, target, test, device_info, textproto):
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"managed evidence missing: {path}")
    target_id, test_id = apk_id(target), apk_id(test)
    if (target_id, test_id) != ("app.codecks.internal", "app.codecks.internal.test"):
        raise ValueError(f"managed artifact identity mismatch: {target_id}, {test_id}")
    bound_paths = tuple(sorted(source_paths))
    sources = [{"path": path, "sha256": sha256(safe_path(root, path))} for path in bound_paths]
    parsed = parse_exact_result(result, class_name, methods)
    commit = source_commit or subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=root, check=True, capture_output=True, text=True,
    ).stdout.strip()
    diff = subprocess.run(
        ["git", "diff", "--binary", commit, "--", *bound_paths],
        cwd=root, check=True, capture_output=True,
    ).stdout
    binding = {
        "sourceCommit": commit,
        "sources": sources,
        "sourceTreeSha256": hashlib.sha256(
            json.dumps(sources, sort_keys=True, separators=(",", ":")).encode()
        ).hexdigest(),
        "sourceDiffSha256": hashlib.sha256(diff).hexdigest(),
        "result": {"path": result_path, "sha256": sha256(result), **parsed},
        "device": {
            "name": "pixel6Api35", "api": 35, "source": "AOSP_MANAGED_EMULATOR",
            "properties": EXPECTED_PROPERTIES,
            "deviceInfoPath": device_info.relative_to(root).as_posix(),
            "deviceInfoSha256": sha256(device_info),
            "textprotoPath": textproto.relative_to(root).as_posix(),
            "textprotoSha256": sha256(textproto),
        },
        "targetApk": {"path": target_apk, "applicationId": target_id, "sha256": sha256(target)},
        "testApk": {"path": test_apk, "applicationId": test_id, "sha256": sha256(test)},
    }
    return binding


def validate_binding(
    root: Path,
    binding: dict,
    *,
    source_paths: tuple[str, ...],
    class_name: str,
    methods: set[str],
) -> None:
    commit = binding.get("sourceCommit", "")
    object_type = subprocess.run(
        ["git", "cat-file", "-t", commit], cwd=root, capture_output=True, text=True,
    )
    if object_type.returncode != 0 or object_type.stdout.strip() != "commit":
        raise ValueError("managed sourceCommit is not a commit object")
    ancestor = subprocess.run(["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=root)
    if ancestor.returncode != 0:
        raise ValueError("managed sourceCommit is not an ancestor of HEAD")
    expected = collect_binding(
        root,
        source_paths=source_paths,
        class_name=class_name,
        methods=methods,
        result_path=binding["result"]["path"],
        target_apk=binding["targetApk"]["path"],
        test_apk=binding["testApk"]["path"],
        source_commit=commit,
    )
    if binding != expected:
        raise ValueError("managed execution binding mismatch")
