#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess

from collect_m18_automation_proof import (
    EXPECTED_METHODS,
    NOT_RUN_LANES,
    PASS_LANES,
    ROOT,
    SCHEMA_ID,
    SOURCE_PATHS,
    TEST_CLASS,
    parse_cpu_result,
    safe_path,
    sha256,
)
from validate_autonomous_maturity_evidence import validate_schema_node

RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m18-automation-proof.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m18-automation-proof-v1.schema.json"
PROHIBITED_KEYS = re.compile(r"(?:raw.?command|process.?output|host.?identity|credential|secret|password)", re.I)
PROHIBITED_VALUES = (
    re.compile(r"-----BEGIN [^-\r\n]*PRIVATE KEY-----", re.I),
    re.compile(r"\bssh-(?:rsa|ed25519|dss|ecdsa)\s+[A-Za-z0-9+/=]{8,}", re.I),
    re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{8,}", re.I),
    re.compile(r"\bsk-[A-Za-z0-9_-]{8,}", re.I),
    re.compile(r"\b(?:password|credential|secret|api[_-]?key|private[_-]?key)\s*[:=]\s*[^\s,;]+", re.I),
    re.compile(r"M18_SECRET_CANARY", re.I),
)


def validate(path: Path = RECEIPT) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_schema_node(data, schema, schema, "m18")
    expected_keys = {
        "schema", "milestone", "status", "scope", "generatedAtUtc", "sourceCommit",
        "sources", "unitResult", "lanes", "privacy", "summary", "limitations",
    }
    if set(data) != expected_keys:
        raise ValueError("M18 receipt keys are not closed")
    if (data["schema"], data["milestone"], data["status"], data["scope"]) != (
        SCHEMA_ID, "M18", "PASS", "CPU_SOURCE_BOUND",
    ):
        raise ValueError("M18 receipt identity/status mismatch")
    source_commit = data["sourceCommit"]
    commit_object = subprocess.run(
        ["git", "cat-file", "-t", source_commit], cwd=ROOT, capture_output=True, text=True,
    )
    if commit_object.returncode != 0:
        raise ValueError("M18 source commit object is missing")
    if commit_object.stdout.strip() != "commit":
        raise ValueError("M18 source commit object type is not commit")
    if subprocess.run(
        ["git", "merge-base", "--is-ancestor", source_commit, "HEAD"], cwd=ROOT, capture_output=True,
    ).returncode != 0:
        raise ValueError("M18 source commit is not an ancestor of current HEAD")
    if [item["path"] for item in data["sources"]] != list(SOURCE_PATHS):
        raise ValueError("M18 source path set/order is not exact")
    for item in data["sources"]:
        if sha256(safe_path(item["path"])) != item["sha256"]:
            raise ValueError(f"M18 source digest mismatch: {item['path']}")
    result = data["unitResult"]
    if result["className"] != TEST_CLASS or set(result["methods"]) != EXPECTED_METHODS:
        raise ValueError("M18 unit result identity/methods mismatch")
    result_path = safe_path(result["path"])
    if sha256(result_path) != result["sha256"]:
        raise ValueError("M18 unit result digest mismatch")
    timestamp, methods = parse_cpu_result(result_path)
    if timestamp != result["timestamp"] or methods != result["methods"]:
        raise ValueError("M18 unit result claims do not match XML")
    expected_lanes = list(PASS_LANES) + list(NOT_RUN_LANES)
    if [lane["id"] for lane in data["lanes"]] != expected_lanes:
        raise ValueError("M18 lane identity/order is not exact")
    for lane in data["lanes"]:
        expected = "PASS" if lane["id"] in PASS_LANES else "NOT_RUN"
        if lane["status"] != expected:
            raise ValueError(f"M18 lane status mismatch: {lane['id']}")
    if data["privacy"] != {
        "commandContentRecorded": False,
        "processOutputRecorded": False,
        "hostIdentityRecorded": False,
        "credentialRecorded": False,
    }:
        raise ValueError("M18 privacy boundary changed")
    if data["summary"] != {"pass": len(PASS_LANES), "fail": 0, "notRun": len(NOT_RUN_LANES)}:
        raise ValueError("M18 summary mismatch")
    if len(data["limitations"]) != 4 or len(set(data["limitations"])) != 4:
        raise ValueError("M18 limitations missing or duplicated")
    _reject_sensitive_payload(data)


def _reject_sensitive_payload(value: object, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if PROHIBITED_KEYS.search(key) and key not in {
                "processOutputRecorded", "hostIdentityRecorded", "credentialRecorded",
            }:
                raise ValueError(f"M18 prohibited receipt key: {'.'.join(path + (key,))}")
            _reject_sensitive_payload(child, path + (key,))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _reject_sensitive_payload(child, path + (str(index),))
    elif isinstance(value, str):
        for pattern in PROHIBITED_VALUES:
            if pattern.search(value):
                raise ValueError(f"M18 prohibited receipt value: {'.'.join(path)}")


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print(f"PASS: M18 source-bound CPU receipt; {len(NOT_RUN_LANES)} runtime/external lanes remain NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
