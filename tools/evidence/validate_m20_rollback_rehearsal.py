#!/usr/bin/env python3
from __future__ import annotations

from datetime import timedelta
import json
from pathlib import Path
import re
import subprocess

from collect_m20_rollback_rehearsal import (
    EXPECTED_METHODS,
    EXPECTED_OUTCOMES,
    FOLLOW_UP_ACTIONS,
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
    parse_timestamp,
    safe_path,
    sha256,
)
from validate_autonomous_maturity_evidence import validate_schema_node

RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m20-rollback-rehearsal-v1.schema.json"
PROHIBITED_KEYS = re.compile(
    r"(?:credential|password|passcode|token.?value|secret|api.?key|private.?key|hostname|username|"
    r"clipboard|prompt|response|raw.?output|stdout|stderr|command|device.?serial|account.?id|purchase.?id)",
    re.I,
)
PROHIBITED_VALUES = (
    re.compile(r"-----BEGIN [^-\r\n]*PRIVATE KEY-----", re.I),
    re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{8,}", re.I),
    re.compile(r"\bsk-[A-Za-z0-9_-]{8,}", re.I),
    re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.I),
    re.compile(r"(?<![A-Za-z0-9])(?:\d{1,3}\.){3}\d{1,3}(?![A-Za-z0-9])"),
    re.compile(r"M20_(?:SECRET|CONTENT|IDENTITY)_CANARY", re.I),
)


def reject_sensitive(value: object, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if PROHIBITED_KEYS.search(key):
                raise ValueError(f"M20 prohibited key: {'.'.join(path + (key,))}")
            reject_sensitive(child, path + (key,))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            reject_sensitive(child, path + (str(index),))
    elif isinstance(value, str):
        for pattern in PROHIBITED_VALUES:
            if pattern.search(value):
                raise ValueError(f"M20 prohibited value: {'.'.join(path)}")


def validate(path: Path = RECEIPT) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_schema_node(data, schema, schema, "m20")
    expected_keys = {
        "schema", "milestone", "status", "scope", "sourceCommit", "sources", "unitResult",
        "drill", "lanes", "safety", "summary", "validatorCases", "limitations", "receiptDigest",
    }
    if set(data) != expected_keys:
        raise ValueError("M20 receipt keys are not closed")
    if (data["schema"], data["milestone"], data["status"], data["scope"]) != (
        SCHEMA_ID, "M20", "PASS", "CPU_FAKE_SOURCE_BOUND",
    ):
        raise ValueError("M20 identity/status mismatch")
    commit = data["sourceCommit"]
    object_type = subprocess.run(
        ["git", "cat-file", "-t", commit], cwd=ROOT, capture_output=True, text=True,
    )
    if object_type.returncode != 0:
        raise ValueError("M20 source commit object is missing")
    if object_type.stdout.strip() != "commit":
        raise ValueError("M20 source commit object is not commit")
    if subprocess.run(["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=ROOT).returncode != 0:
        raise ValueError("M20 source commit is not an ancestor")
    if [item["path"] for item in data["sources"]] != list(SOURCE_PATHS):
        raise ValueError("M20 source path set/order mismatch")
    for item in data["sources"]:
        if sha256(safe_path(item["path"])) != item["sha256"]:
            raise ValueError(f"M20 source digest mismatch: {item['path']}")
    result = data["unitResult"]
    if result["className"] != TEST_CLASS or set(result["methods"]) != EXPECTED_METHODS:
        raise ValueError("M20 unit identity/method mismatch")
    result_path = safe_path(result["path"])
    if sha256(result_path) != result["sha256"]:
        raise ValueError("M20 unit result digest mismatch")
    methods, outcomes, started_at, recovery_millis = parse_result(result_path)
    if methods != result["methods"]:
        raise ValueError("M20 unit result claims mismatch")
    drill = data["drill"]
    claimed_outcomes = {item["scenario"]: item["result"] for item in drill["outcomes"]}
    if len(claimed_outcomes) != len(drill["outcomes"]) or claimed_outcomes != EXPECTED_OUTCOMES:
        raise ValueError("M20 drill exact outcome mismatch")
    if claimed_outcomes != outcomes:
        raise ValueError("M20 drill outcome/result mismatch")
    if drill["startedAtUtc"] != started_at or drill["recoveryMillis"] != recovery_millis:
        raise ValueError("M20 drill timing/result mismatch")
    expected_completed = (parse_timestamp(started_at) + timedelta(milliseconds=recovery_millis))
    if parse_timestamp(drill["completedAtUtc"]) != expected_completed:
        raise ValueError("M20 drill completion timestamp mismatch")
    if (drill["scenarioCount"], drill["failureInjectionCount"], drill["controlCount"]) != (
        len(EXPECTED_OUTCOMES), len(EXPECTED_OUTCOMES) - 2, 2,
    ):
        raise ValueError("M20 scenario/failure/control count mismatch")
    if drill["followUpActions"] != list(FOLLOW_UP_ACTIONS):
        raise ValueError("M20 follow-up action mismatch")
    expected_lanes = list(PASS_LANES) + list(NOT_RUN_LANES)
    if [lane["id"] for lane in data["lanes"]] != expected_lanes:
        raise ValueError("M20 lane identity/order mismatch")
    for lane in data["lanes"]:
        expected = "PASS" if lane["id"] in PASS_LANES else "NOT_RUN"
        evidence = "CPU_UNIT" if expected == "PASS" else "EXTERNAL"
        if (lane["status"], lane["evidence"]) != (expected, evidence):
            raise ValueError(f"M20 lane claim mismatch: {lane['id']}")
    if data["safety"] != {
        "protectedPackageTouched": False,
        "olderApkInstalled": False,
        "physicalDeviceTouched": False,
        "productionSigningMaterialAccessed": False,
        "destructiveLiveMigrationRun": False,
    }:
        raise ValueError("M20 safety boundary changed")
    if data["summary"] != {"pass": len(PASS_LANES), "fail": 0, "notRun": len(NOT_RUN_LANES)}:
        raise ValueError("M20 summary mismatch")
    if data["validatorCases"] != {
        "positive": VALIDATOR_POSITIVE_CASES,
        "negative": VALIDATOR_NEGATIVE_CASES,
    }:
        raise ValueError("M20 validator case counts mismatch")
    if len(data["limitations"]) != 4 or len(set(data["limitations"])) != 4:
        raise ValueError("M20 limitations mismatch")
    if canonical_digest(data) != data["receiptDigest"]:
        raise ValueError("M20 receipt digest mismatch")
    reject_sensitive(data)


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}")
        return 1
    print(f"PASS: M20 source-bound CPU rehearsal; {len(NOT_RUN_LANES)} external lanes remain NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
