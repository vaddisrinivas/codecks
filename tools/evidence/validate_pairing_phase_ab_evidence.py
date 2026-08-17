#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess

from collect_pairing_phase_ab_evidence import (
    COMMANDS, EXECUTIONS, NOT_RUN_LANES, PASS_LANES, PHASE_COMMITS, ROOT, SCHEMA_ID, SOURCE_PATHS,
    assert_bound_sources, canonical_digest, committed_blob, execution, safe_path, sha256,
)
from validate_autonomous_maturity_evidence import validate_schema_node

RECEIPT = ROOT / "tasks/test-evidence/pairing-phase-ab-source.json"
SCHEMA = ROOT / "tools/evidence/schemas/pairing-phase-ab-source-evidence-v1.schema.json"


def validate(path: Path = RECEIPT) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_schema_node(data, schema, schema, "pairing-phase-ab")
    if set(data) != {"schema", "scope", "status", "sourceCommit", "phaseCommits", "sources", "executions", "commands", "lanes", "boundaries", "limitations", "receiptDigest"}:
        raise ValueError("pairing receipt keys are not closed")
    if (data["schema"], data["scope"], data["status"]) != (
        SCHEMA_ID, "EXACT_PHASE_B_SOURCE_AND_CPU_UNIT", "PASS_WITH_NOT_RUN_BOUNDARIES",
    ):
        raise ValueError("pairing receipt identity mismatch")
    if data["phaseCommits"] != PHASE_COMMITS:
        raise ValueError("phase commit binding mismatch")
    for commit in [data["sourceCommit"], *data["phaseCommits"].values()]:
        if subprocess.run(["git", "cat-file", "-e", f"{commit}^{{commit}}"], cwd=ROOT).returncode:
            raise ValueError("bound commit is missing")
        if subprocess.run(["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=ROOT).returncode:
            raise ValueError("bound commit is not an ancestor")
    if [item["path"] for item in data["sources"]] != list(SOURCE_PATHS):
        raise ValueError("source path/order mismatch")
    assert_bound_sources()
    for item in data["sources"]:
        if item["commit"] != PHASE_COMMITS["phaseB"]:
            raise ValueError(f"source commit mismatch: {item['path']}")
        if sha256(safe_path(item["path"])) != item["sha256"]:
            raise ValueError(f"source digest mismatch: {item['path']}")
        if sha256_bytes(committed_blob(item["commit"], item["path"])) != item["sha256"]:
            raise ValueError(f"source blob mismatch: {item['path']}")
    if [item["id"] for item in data["executions"]] != [item[0] for item in EXECUTIONS]:
        raise ValueError("execution identity/order mismatch")
    expected_executions = [dict({"id": identity}, **execution(relative, class_name)) for identity, relative, class_name in EXECUTIONS]
    if data["executions"] != expected_executions:
        raise ValueError("execution receipt mismatch")
    if data["commands"] != list(COMMANDS):
        raise ValueError("execution command mismatch")
    expected_lanes = list(PASS_LANES) + list(NOT_RUN_LANES)
    if [lane["id"] for lane in data["lanes"]] != expected_lanes:
        raise ValueError("lane identity/order mismatch")
    for lane in data["lanes"]:
        expected = ("PASS", "EXACT_PHASE_B_BLOB_AND_CPU_UNIT") if lane["id"] in PASS_LANES else ("NOT_RUN", "EXTERNAL_OR_ARTIFACT")
        if (lane["status"], lane["evidence"]) != expected:
            raise ValueError(f"lane claim mismatch: {lane['id']}")
    if data["boundaries"] != {
        "signedArtifactBuilt": False, "signingIdentityAccessed": False,
        "keychainEntitlementProven": False, "runtimePairingProven": False,
        "physicalPhoneTouched": False, "tcpPayloadEncrypted": False,
    }:
        raise ValueError("proof boundary changed")
    if len(data["limitations"]) != 4 or len(set(data["limitations"])) != 4:
        raise ValueError("limitations mismatch")
    if canonical_digest(data) != data["receiptDigest"]:
        raise ValueError("receipt digest mismatch")


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}")
        return 1
    print(f"PASS: Phase A/B current-source proof; {len(NOT_RUN_LANES)} artifact/runtime lanes remain NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
