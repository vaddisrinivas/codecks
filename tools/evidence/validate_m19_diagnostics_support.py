#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess

from collect_m19_diagnostics_support import (
    EXPECTED_METHODS,
    NOT_RUN_LANES,
    PASS_LANES,
    ROOT,
    SCHEMA_ID,
    SOURCE_PATHS,
    TEST_CLASS,
    VALIDATOR_NEGATIVE_CASES,
    VALIDATOR_POSITIVE_CASES,
    canonical_digest,
    parse_result,
    safe_path,
    sha256,
)
from validate_autonomous_maturity_evidence import validate_schema_node

RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m19-diagnostics-support.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m19-diagnostics-support-v1.schema.json"
PROHIBITED_KEYS = re.compile(
    r"(?:credential|password|passcode|token|secret|api.?key|private.?key|hostname|host.?identity|username|"
    r"fingerprint|clipboard|prompt|response|raw.?output|stdout|stderr|command|device.?serial|account.?id|purchase.?id)",
    re.I,
)
ALLOWED_PRIVACY_KEYS = {
    "rawContentRecorded", "endpointIdentityRecorded", "personalIdentifierRecorded", "commercialIdentifierRecorded",
}
PROHIBITED_VALUES = (
    re.compile(r"-----BEGIN [^-\r\n]*PRIVATE KEY-----", re.I),
    re.compile(r"\bssh-(?:rsa|ed25519|dss|ecdsa)\s+[A-Za-z0-9+/=]{8,}", re.I),
    re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{8,}", re.I),
    re.compile(r"\bsk-[A-Za-z0-9_-]{8,}", re.I),
    re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.I),
    re.compile(r"(?<![A-Za-z0-9])(?:\d{1,3}\.){3}\d{1,3}(?![A-Za-z0-9])"),
    re.compile(
        r"\b(?:password|passwd|token|secret|credential|api[_-]?key|private[_-]?key|hostname|host|username|user|"
        r"fingerprint|clipboard|prompt|response|account[_-]?id|purchase[_-]?id|device[_-]?serial)\s*[:=]\s*[^\s,;]+",
        re.I,
    ),
    re.compile(r"M19_(?:SECRET|CONTENT|IDENTITY)_CANARY", re.I),
)
PRODUCTION_SUPPORT_LITERAL = re.compile(r'"[^"\n]*CX-')
CANONICAL_SUPPORT_REGISTRY = ROOT / "app/src/main/java/io/codecks/ui/connection/UnifiedConnectionPresentation.kt"


def reject_raw_production_support_literals() -> None:
    production_root = ROOT / "app/src/main/java"
    offenders = [
        path.relative_to(ROOT).as_posix()
        for path in production_root.rglob("*.kt")
        if path != CANONICAL_SUPPORT_REGISTRY and PRODUCTION_SUPPORT_LITERAL.search(path.read_text(encoding="utf-8"))
    ]
    if offenders:
        raise ValueError(f"M19 raw production support-code literal: {offenders}")


def reject_sensitive(value: object, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if PROHIBITED_KEYS.search(key) and key not in ALLOWED_PRIVACY_KEYS:
                raise ValueError(f"M19 prohibited key: {'.'.join(path + (key,))}")
            reject_sensitive(child, path + (key,))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            reject_sensitive(child, path + (str(index),))
    elif isinstance(value, str):
        for pattern in PROHIBITED_VALUES:
            if pattern.search(value):
                raise ValueError(f"M19 prohibited value: {'.'.join(path)}")


def validate(path: Path = RECEIPT) -> None:
    reject_raw_production_support_literals()
    data = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_schema_node(data, schema, schema, "m19")
    expected_keys = {
        "schema", "milestone", "status", "scope", "sourceCommit", "sources", "unitResult", "lanes",
        "privacy", "summary", "limitations", "receiptDigest",
        "validatorCases",
    }
    if set(data) != expected_keys:
        raise ValueError("M19 receipt keys are not closed")
    if (data["schema"], data["milestone"], data["status"], data["scope"]) != (
        SCHEMA_ID, "M19", "PASS", "CPU_SOURCE_BOUND",
    ):
        raise ValueError("M19 identity/status mismatch")
    commit = data["sourceCommit"]
    object_type = subprocess.run(
        ["git", "cat-file", "-t", commit], cwd=ROOT, capture_output=True, text=True,
    )
    if object_type.returncode != 0:
        raise ValueError("M19 source commit object is missing")
    if object_type.stdout.strip() != "commit":
        raise ValueError("M19 source commit object is not commit")
    if subprocess.run(["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=ROOT).returncode != 0:
        raise ValueError("M19 source commit is not an ancestor")
    if [item["path"] for item in data["sources"]] != list(SOURCE_PATHS):
        raise ValueError("M19 source path set/order mismatch")
    for item in data["sources"]:
        if sha256(safe_path(item["path"])) != item["sha256"]:
            raise ValueError(f"M19 source digest mismatch: {item['path']}")
    result = data["unitResult"]
    if result["className"] != TEST_CLASS or set(result["methods"]) != EXPECTED_METHODS:
        raise ValueError("M19 unit identity/method mismatch")
    result_path = safe_path(result["path"])
    if sha256(result_path) != result["sha256"]:
        raise ValueError("M19 unit result digest mismatch")
    if parse_result(result_path) != result["methods"]:
        raise ValueError("M19 unit result claims mismatch")
    expected_lanes = list(PASS_LANES) + list(NOT_RUN_LANES)
    if [lane["id"] for lane in data["lanes"]] != expected_lanes:
        raise ValueError("M19 lane identity/order mismatch")
    for lane in data["lanes"]:
        expected = "PASS" if lane["id"] in PASS_LANES else "NOT_RUN"
        evidence = "CPU_UNIT" if expected == "PASS" else "EXTERNAL"
        if (lane["status"], lane["evidence"]) != (expected, evidence):
            raise ValueError(f"M19 lane claim mismatch: {lane['id']}")
    if data["privacy"] != {
        "rawContentRecorded": False,
        "endpointIdentityRecorded": False,
        "personalIdentifierRecorded": False,
        "commercialIdentifierRecorded": False,
    }:
        raise ValueError("M19 privacy boundary changed")
    if data["summary"] != {"pass": 5, "fail": 0, "notRun": 3}:
        raise ValueError("M19 summary mismatch")
    if data["validatorCases"] != {
        "positive": VALIDATOR_POSITIVE_CASES,
        "negative": VALIDATOR_NEGATIVE_CASES,
    }:
        raise ValueError("M19 validator case counts mismatch")
    if len(data["limitations"]) != 3 or len(set(data["limitations"])) != 3:
        raise ValueError("M19 limitations mismatch")
    if canonical_digest(data) != data["receiptDigest"]:
        raise ValueError("M19 receipt digest mismatch")
    reject_sensitive(data)


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}")
        return 1
    print(f"PASS: M19 source-bound CPU receipt; {len(NOT_RUN_LANES)} external lanes remain NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
