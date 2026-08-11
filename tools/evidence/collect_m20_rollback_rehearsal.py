#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ID = "codecks.autonomous-maturity.m20-rollback-rehearsal.v1"
TEST_CLASS = "io.codecks.data.persistence.M20RollbackIncidentRehearsalTest"
EXPECTED_METHODS = {
    "badReleaseRollbackMeansWithdrawalAndForwardFix",
    "corruptAndFutureDataFailClosed",
    "failedUpgradePreservesOlderData",
    "incidentPolicyHasClosedActionableDecisionForEveryIncident",
    "interruptedTransactionsRecoverExactGeneration",
    "keyLossNeverFallsBackToCiphertext",
    "partialUpdateSimulationRejectsEveryIncompleteOrUnverifiedState",
}
EXPECTED_OUTCOMES = {
    "bad_release": "WITHDRAW_AND_FORWARD_FIX",
    "checksum_failure": "REJECT_UNVERIFIED_CANDIDATE",
    "committed_update": "VERIFIED_UPDATE_COMMITTED",
    "compromised_token": "TOKEN_REVOKE_ROTATE",
    "corrupt_data": "REFUSED_MUTATION_PRESERVED_RAW",
    "equal_version": "REJECT_DOWNGRADE",
    "failed_upgrade": "REFUSED_MUTATION_PRESERVED_V1",
    "future_data": "OLDER_READER_REFUSED_PRESERVED_RAW",
    "incident_matrix": "CLOSED_NO_DOWNGRADE",
    "key_loss": "REPAIR_REQUIRED_CIPHERTEXT_PRESERVED",
    "older_version": "REJECT_DOWNGRADE",
    "partial_download": "DISCARD_PARTIAL_DOWNLOAD",
    "partial_install": "ABANDON_PARTIAL_INSTALL_SESSION",
    "signer_failure": "REJECT_UNVERIFIED_CANDIDATE",
    "source_failure": "REJECT_UNVERIFIED_CANDIDATE",
    "ssh_host_key": "SSH_IDENTITY_REVERIFY",
    "transaction_after_backups": "RESTORED_OLD",
    "transaction_after_committed_journal": "KEPT_NEW",
    "transaction_after_first_backup_delete": "RESTORED_OLD",
    "transaction_after_first_install": "RESTORED_OLD",
    "transaction_after_first_journal": "RESTORED_OLD",
    "transaction_after_first_stage_delete": "RESTORED_OLD",
    "transaction_after_journal_removal": "KEPT_NEW",
    "transaction_after_prepared_journal": "RESTORED_OLD",
    "transaction_after_second_backup_delete": "RESTORED_OLD",
    "transaction_after_second_install": "RESTORED_OLD",
    "transaction_after_second_journal": "KEPT_NEW",
    "transaction_after_second_stage_delete": "RESTORED_OLD",
    "transaction_after_staging": "RESTORED_OLD",
}
SOURCE_PATHS = (
    "app/src/main/java/io/codecks/data/persistence/BoundedPersistence.kt",
    "app/src/main/java/io/codecks/domain/update/ReleaseRecoveryPolicy.kt",
    "app/src/test/java/io/codecks/data/persistence/M20RollbackIncidentRehearsalTest.kt",
    "docs/release/ROLLBACK_AND_INCIDENT_RUNBOOK.md",
    "scripts/run_m20_rollback_rehearsal.sh",
    "tools/evidence/collect_m20_rollback_rehearsal.py",
    "tools/evidence/test_m20_rollback_rehearsal.py",
    "tools/evidence/validate_m20_rollback_rehearsal.py",
    "tools/evidence/schemas/autonomous-maturity-m20-rollback-rehearsal-v1.schema.json",
)
PASS_LANES = (
    "cpu.corrupt_future_refusal",
    "cpu.interrupted_transaction_recovery",
    "cpu.failed_upgrade_refusal",
    "cpu.key_loss_refusal",
    "cpu.partial_update_simulation",
    "cpu.rollback_decision_policy",
    "cpu.incident_runbook",
)
NOT_RUN_LANES = (
    "ci.disposable_signing_continuity",
    "external.real_signing_key_custody",
    "physical.protected_app_update_recovery",
    "live.release_withdrawal",
    "human.incident_escalation",
)
FOLLOW_UP_ACTIONS = (
    "withdraw_bad_artifact",
    "preserve_redacted_evidence",
    "verify_source_checksum_signer",
    "ship_greater_version_code_forward_fix",
    "escalate_unrecoverable_or_security_incident",
)
OUTCOME_PATTERN = re.compile(r"^M20_OUTCOME\|([a-z0-9_]+)\|([A-Z0-9_]+)$")
VALIDATOR_POSITIVE_CASES = 1
VALIDATOR_NEGATIVE_CASES = 19


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


def parse_timestamp(value: str) -> datetime:
    if not value.endswith("Z"):
        raise ValueError("M20 timestamp must be UTC")
    parsed = datetime.fromisoformat(value.removesuffix("Z") + "+00:00")
    if parsed.tzinfo != timezone.utc:
        raise ValueError("M20 timestamp must be UTC")
    return parsed


def parse_result(path: Path) -> tuple[list[str], dict[str, str], str, int]:
    root = ET.fromstring(path.read_bytes())
    if root.tag != "testsuite" or root.attrib.get("name") != TEST_CLASS:
        raise ValueError("M20 suite identity mismatch")
    cases = root.findall("testcase")
    methods = [case.attrib.get("name", "") for case in cases]
    if len(methods) != len(set(methods)) or set(methods) != EXPECTED_METHODS:
        raise ValueError("M20 method set mismatch")
    if any(case.attrib.get("classname") != TEST_CLASS for case in cases):
        raise ValueError("M20 testcase class mismatch")
    counts = {
        "tests": len(cases),
        "failures": sum(bool(case.findall("failure")) for case in cases),
        "errors": sum(bool(case.findall("error")) for case in cases),
        "skipped": sum(bool(case.findall("skipped")) for case in cases),
    }
    if counts != {"tests": 7, "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError("M20 result is not clean 7/7")
    for key, expected in counts.items():
        if int(root.attrib.get(key, "-1")) != expected:
            raise ValueError(f"M20 XML {key} mismatch")
    outcomes: dict[str, str] = {}
    for output in root.findall("system-out"):
        for line in (output.text or "").splitlines():
            match = OUTCOME_PATTERN.fullmatch(line.strip())
            if match:
                scenario, result = match.groups()
                if scenario in outcomes:
                    raise ValueError("M20 duplicate outcome")
                outcomes[scenario] = result
    if outcomes != EXPECTED_OUTCOMES:
        raise ValueError("M20 exact outcome set mismatch")
    timestamp = root.attrib.get("timestamp", "")
    parse_timestamp(timestamp)
    recovery_millis = max(0, round(float(root.attrib.get("time", "-1")) * 1000))
    return sorted(methods), dict(sorted(outcomes.items())), timestamp, recovery_millis


def sanitize_result(raw: Path, output: Path) -> tuple[list[str], dict[str, str], str, int]:
    methods, outcomes, timestamp, recovery_millis = parse_result(raw)
    suite = ET.Element("testsuite", {
        "name": TEST_CLASS,
        "tests": "7",
        "failures": "0",
        "errors": "0",
        "skipped": "0",
        "timestamp": timestamp,
        "time": f"{recovery_millis / 1000:.3f}",
    })
    for method in methods:
        ET.SubElement(suite, "testcase", {"name": method, "classname": TEST_CLASS})
    system_out = ET.SubElement(suite, "system-out")
    system_out.text = "\n".join(
        f"M20_OUTCOME|{scenario}|{result}" for scenario, result in outcomes.items()
    ) + "\n"
    output.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(suite).write(output, encoding="utf-8", xml_declaration=True)
    return methods, outcomes, timestamp, recovery_millis


def canonical_digest(data: dict) -> str:
    payload = dict(data)
    payload.pop("receiptDigest", None)
    return hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def collect(raw_result: str, sanitized_output: str) -> dict:
    result_path = safe_path(sanitized_output)
    methods, outcomes, started_at, recovery_millis = sanitize_result(safe_path(raw_result), result_path)
    started = parse_timestamp(started_at)
    completed_at = (started + timedelta(milliseconds=recovery_millis)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
    source_commit = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.strip()
    data = {
        "schema": SCHEMA_ID,
        "milestone": "M20",
        "status": "PASS",
        "scope": "CPU_FAKE_SOURCE_BOUND",
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
        "drill": {
            "startedAtUtc": started_at,
            "completedAtUtc": completed_at,
            "recoveryMillis": recovery_millis,
            "scenarioCount": len(outcomes),
            "failureInjectionCount": len(outcomes) - 2,
            "controlCount": 2,
            "outcomes": [{"scenario": key, "result": value} for key, value in outcomes.items()],
            "followUpActions": list(FOLLOW_UP_ACTIONS),
        },
        "lanes": [
            {"id": lane, "status": "PASS", "evidence": "CPU_UNIT"} for lane in PASS_LANES
        ] + [
            {"id": lane, "status": "NOT_RUN", "evidence": "EXTERNAL"} for lane in NOT_RUN_LANES
        ],
        "safety": {
            "protectedPackageTouched": False,
            "olderApkInstalled": False,
            "physicalDeviceTouched": False,
            "productionSigningMaterialAccessed": False,
            "destructiveLiveMigrationRun": False,
        },
        "summary": {"pass": len(PASS_LANES), "fail": 0, "notRun": len(NOT_RUN_LANES)},
        "validatorCases": {"positive": VALIDATOR_POSITIVE_CASES, "negative": VALIDATOR_NEGATIVE_CASES},
        "limitations": [
            "CPU file and state models do not prove Android package-installer behavior.",
            "CI signing continuity and offline custody of real signing keys remain external.",
            "No live release was withdrawn and no protected package was installed or downgraded.",
            "Human incident escalation and recovery comprehension remain external.",
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
