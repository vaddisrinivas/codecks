#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess

from collect_m15_clipboard_battery import (
    EXPECTED_METHODS,
    EXPECTED_MANAGED_METHODS,
    MANAGED_PASS_LANE,
    MANAGED_TEST_CLASS,
    NOT_RUN_LANES,
    PASS_LANES,
    ROOT,
    SCHEMA_ID,
    SOURCE_PATHS,
    TEST_CLASS,
    parse_cpu_result,
    parse_managed_result,
    safe_path,
    sha256,
)
from validate_autonomous_maturity_evidence import validate_schema_node

RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m15-clipboard-battery.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m15-clipboard-battery-v1.schema.json"
PROHIBITED_KEYS = re.compile(r"(?:clipboard.?content|process.?output|hostname|username|host.?identity|credential|secret|token|password)", re.I)
PROHIBITED_VALUE_PATTERNS = (
    re.compile(r"-----BEGIN [^-\r\n]*PRIVATE KEY-----", re.I),
    re.compile(r"\bssh-(?:rsa|ed25519|dss|ecdsa)\s+[A-Za-z0-9+/=]{8,}", re.I),
    re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{8,}", re.I),
    re.compile(r"\bsk-[A-Za-z0-9_-]{8,}", re.I),
    re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.I),
    re.compile(r"(?<![A-Za-z0-9])(?:\d{1,3}\.){3}\d{1,3}(?![A-Za-z0-9])"),
    re.compile(
        r"\b(?:password|passwd|token|secret|credential|api[_-]?key|private[_-]?key|"
        r"hostname|host|username|user)\s*[:=]\s*[^\s,;]+",
        re.I,
    ),
    re.compile(r"M15_SECRET_CANARY", re.I),
)


def validate(path: Path = RECEIPT) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_schema_node(data, schema, schema, "m15")
    expected_keys = {
        "schema", "milestone", "status", "scope", "generatedAtUtc", "sourceCommit",
        "sources", "unitResult", "managedResult", "lanes", "privacy", "summary", "limitations",
    }
    if set(data) != expected_keys:
        raise ValueError("M15 receipt keys are not closed")
    if (data["schema"], data["milestone"], data["status"], data["scope"]) != (
        SCHEMA_ID, "M15", "PASS", "CPU_SOURCE_BOUND_WITH_UNBOUND_MANAGED_PROXY",
    ):
        raise ValueError("M15 receipt identity/status mismatch")
    head = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.strip()
    if data["sourceCommit"] != head:
        raise ValueError("M15 source commit does not match current HEAD")
    sources = data["sources"]
    if [item["path"] for item in sources] != list(SOURCE_PATHS):
        raise ValueError("M15 source path set/order is not exact")
    for item in sources:
        if sha256(safe_path(item["path"])) != item["sha256"]:
            raise ValueError(f"M15 source digest mismatch: {item['path']}")
    result = data["unitResult"]
    if result["className"] != TEST_CLASS or set(result["methods"]) != EXPECTED_METHODS:
        raise ValueError("M15 unit result identity/methods mismatch")
    result_path = safe_path(result["path"])
    if sha256(result_path) != result["sha256"]:
        raise ValueError("M15 unit result digest mismatch")
    timestamp, methods = parse_cpu_result(result_path)
    if timestamp != result["timestamp"] or methods != result["methods"]:
        raise ValueError("M15 unit result claims do not match XML")
    managed = data["managedResult"]
    if (managed["artifactKind"], managed["executedBinaryBinding"]) != (
        "SANITIZED_JUNIT_METADATA", "NOT_INCLUDED",
    ):
        raise ValueError("M15 managed evidence scope overclaims binary binding")
    if managed["className"] != MANAGED_TEST_CLASS or set(managed["methods"]) != EXPECTED_MANAGED_METHODS:
        raise ValueError("M15 managed result identity/methods mismatch")
    if (managed["device"], managed["flavor"]) != ("pixel6Api35", "playInternal"):
        raise ValueError("M15 managed result device/flavor mismatch")
    managed_path = safe_path(managed["path"])
    if sha256(managed_path) != managed["sha256"]:
        raise ValueError("M15 managed result digest mismatch")
    managed_timestamp, managed_methods = parse_managed_result(managed_path)
    if managed_timestamp != managed["timestamp"] or managed_methods != managed["methods"]:
        raise ValueError("M15 managed result claims do not match XML")
    expected_lanes = list(PASS_LANES) + [MANAGED_PASS_LANE] + list(NOT_RUN_LANES)
    if [lane["id"] for lane in data["lanes"]] != expected_lanes:
        raise ValueError("M15 lane identity/order is not exact")
    for lane in data["lanes"]:
        expected = "PASS" if lane["id"] in PASS_LANES or lane["id"] == MANAGED_PASS_LANE else "NOT_RUN"
        if lane["status"] != expected:
            raise ValueError(f"M15 lane status mismatch: {lane['id']}")
    if data["privacy"] != {
        "clipboardContentRecorded": False,
        "processOutputRecorded": False,
        "hostIdentityRecorded": False,
        "credentialRecorded": False,
    }:
        raise ValueError("M15 privacy boundary changed")
    if data["summary"] != {"pass": len(PASS_LANES) + 1, "fail": 0, "notRun": len(NOT_RUN_LANES)}:
        raise ValueError("M15 summary mismatch")
    if len(data["limitations"]) != 4 or len(set(data["limitations"])) != 4:
        raise ValueError("M15 limitations missing or duplicated")
    _reject_sensitive_payload(data)


def _reject_sensitive_payload(value: object, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if PROHIBITED_KEYS.search(key) and key not in {
                "clipboardContentRecorded", "processOutputRecorded", "hostIdentityRecorded", "credentialRecorded",
            }:
                raise ValueError(f"M15 prohibited receipt key: {'.'.join(path + (key,))}")
            _reject_sensitive_payload(child, path + (key,))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _reject_sensitive_payload(child, path + (str(index),))
    elif isinstance(value, str):
        for pattern in PROHIBITED_VALUE_PATTERNS:
            if pattern.search(value):
                raise ValueError(f"M15 prohibited receipt value: {'.'.join(path)}")


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print(f"PASS: M15 source-bound CPU + managed-emulator receipt; {len(NOT_RUN_LANES)} runtime/external lanes remain NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
