#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ID = "codecks.autonomous-maturity.m18-automation-proof.v1"
TEST_CLASS = "io.codecks.domain.automation.M18AutomationAssuranceTest"
EXPECTED_METHODS = {
    "importedAutomationLosesEveryExecutionProofAndCannotDispatch",
    "revisionPermissionAndHostChangesHaveDistinctFailClosedOutcomes",
    "generatedQuotingAndShellMetacharacterCorpusFailsClosed",
    "actionCountAndUtf8CommandSizeAreBoundedBeforeExecution",
    "exactIdempotencyReplayReturnsOriginalReceiptButSourceChangeConflicts",
    "partialFailureRetryTargetsOnlyRetryableComponentAndIsSingleUse",
    "cancellationPropagatesWithoutSuccessOrReplayReceipt",
    "undoIsReceiptBoundAndSingleUse",
    "partialLiveTestAndFailedCleanupCannotProduceEnablementProof",
    "receiptAndPlanHashesChangeWithTheirBoundSources",
}
SOURCE_PATHS = (
    "app/src/main/java/io/codecks/core/actions/ActionContracts.kt",
    "app/src/main/java/io/codecks/core/actions/CommandRevision.kt",
    "app/src/main/java/io/codecks/core/actions/RawCommandPolicy.kt",
    "app/src/main/java/io/codecks/domain/CommandTrust.kt",
    "app/src/main/java/io/codecks/domain/assurance/ActionAssurance.kt",
    "app/src/main/java/io/codecks/domain/assurance/AssuranceSourceAdapters.kt",
    "app/src/main/java/io/codecks/domain/automation/AutomationCapability.kt",
    "app/src/main/java/io/codecks/domain/automation/AutomationExecutionPlan.kt",
    "app/src/main/java/io/codecks/domain/automation/AutomationLiveTestEngine.kt",
    "app/src/main/java/io/codecks/domain/automation/AutomationRecipe.kt",
    "app/src/main/java/io/codecks/domain/automation/AutomationRevisionGate.kt",
    "app/src/main/java/io/codecks/domain/automation/AutomationWorkerOutcome.kt",
    "app/src/test/java/io/codecks/domain/automation/M18AutomationAssuranceTest.kt",
    "tools/evidence/collect_m18_automation_proof.py",
    "tools/evidence/validate_m18_automation_proof.py",
    "tools/evidence/test_m18_automation_proof.py",
    "tools/evidence/schemas/autonomous-maturity-m18-automation-proof-v1.schema.json",
    "scripts/run_m18_automation_proof.sh",
)
PASS_LANES = (
    "cpu.import_policy",
    "cpu.preflight_revision_permission_host",
    "cpu.adversarial_shell",
    "cpu.execution_bounds",
    "cpu.idempotency_source_binding",
    "cpu.partial_failure_retry",
    "cpu.cancellation",
    "cpu.undo",
    "cpu.cleanup_recovery",
    "cpu.receipt_source_binding",
)
NOT_RUN_LANES = (
    "current_mac.permission_preflight",
    "current_mac.host_change",
    "current_mac.tool_unavailable",
    "physical.end_to_end_automation",
)


def safe_path(value: str) -> Path:
    candidate = Path(value)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise ValueError(f"unsafe repository path: {value}")
    resolved = (ROOT / candidate).resolve(strict=False)
    if not resolved.is_relative_to(ROOT.resolve()):
        raise ValueError(f"path escapes repository: {value}")
    relative = resolved.relative_to(ROOT.resolve())
    for index in range(1, len(relative.parts) + 1):
        if ROOT.joinpath(*relative.parts[:index]).is_symlink():
            raise ValueError(f"symlink forbidden: {value}")
    return resolved


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_cpu_result(path: Path) -> tuple[str, list[str]]:
    root = ET.fromstring(path.read_bytes())
    if root.tag != "testsuite" or root.attrib.get("name") != TEST_CLASS:
        raise ValueError("M18 CPU XML suite identity mismatch")
    cases = root.findall("testcase")
    names = [node.attrib.get("name", "") for node in cases]
    if len(names) != len(set(names)) or set(names) != EXPECTED_METHODS:
        raise ValueError("M18 CPU XML method set is not exact")
    if any(node.attrib.get("classname") != TEST_CLASS for node in cases):
        raise ValueError("M18 CPU XML testcase classname mismatch")
    counts = {
        "tests": len(cases),
        "failures": sum(bool(node.findall("failure")) for node in cases),
        "errors": sum(bool(node.findall("error")) for node in cases),
        "skipped": sum(bool(node.findall("skipped")) for node in cases),
    }
    for key, expected in counts.items():
        if int(root.attrib.get(key, "-1")) != expected:
            raise ValueError(f"M18 CPU XML {key} count mismatch")
    if counts != {"tests": 10, "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError("M18 CPU XML is not a clean 10/10 result")
    if any((root.find(tag) is not None and (root.find(tag).text or "").strip()) for tag in ("system-out", "system-err")):
        raise ValueError("M18 CPU XML must not retain process output")
    timestamp = root.attrib.get("timestamp", "")
    datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    return timestamp, sorted(names)


def sanitized_result(raw_path: Path, output_path: Path) -> tuple[str, list[str]]:
    timestamp, methods = parse_cpu_result(raw_path)
    suite = ET.Element("testsuite", {
        "name": TEST_CLASS, "tests": "10", "failures": "0", "errors": "0", "skipped": "0", "timestamp": timestamp,
    })
    for method in methods:
        ET.SubElement(suite, "testcase", {"name": method, "classname": TEST_CLASS})
    output_path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(suite).write(output_path, encoding="utf-8", xml_declaration=True)
    return timestamp, methods


def collect(raw_result: str, sanitized_output: str) -> dict:
    result_path = safe_path(sanitized_output)
    timestamp, methods = sanitized_result(safe_path(raw_result), result_path)
    source_commit = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.strip()
    lanes = [
        {"id": lane, "status": "PASS", "evidence": "CPU_UNIT", "code": "exact_test_passed"}
        for lane in PASS_LANES
    ] + [
        {"id": lane, "status": "NOT_RUN", "evidence": "RUNTIME_OR_EXTERNAL", "code": "held_or_unavailable"}
        for lane in NOT_RUN_LANES
    ]
    return {
        "schema": SCHEMA_ID,
        "milestone": "M18",
        "status": "PASS",
        "scope": "CPU_SOURCE_BOUND",
        "generatedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "sourceCommit": source_commit,
        "sources": [{"path": path, "sha256": sha256(safe_path(path))} for path in SOURCE_PATHS],
        "unitResult": {
            "path": sanitized_output,
            "sha256": sha256(result_path),
            "timestamp": timestamp,
            "className": TEST_CLASS,
            "methods": methods,
            "tests": 10,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
        },
        "lanes": lanes,
        "privacy": {
            "commandContentRecorded": False,
            "processOutputRecorded": False,
            "hostIdentityRecorded": False,
            "credentialRecorded": False,
        },
        "summary": {"pass": len(PASS_LANES), "fail": 0, "notRun": len(NOT_RUN_LANES)},
        "limitations": [
            "CPU tests prove deterministic policy and receipt wiring, not live Mac authorization behavior.",
            "No emulator was required because M18 has no Android UI or lifecycle behavior.",
            "Live permission loss, host replacement, and tool removal remain current-Mac NOT_RUN lanes.",
            "Physical end-to-end automation dispatch remains NOT_RUN and no protected app was touched.",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--result", required=True)
    parser.add_argument("--sanitized-result", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        payload = collect(args.result, args.sanitized_result)
        output = safe_path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError, subprocess.CalledProcessError, ET.ParseError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print(f"PASS: M18 source-bound CPU receipt sealed; {len(NOT_RUN_LANES)} runtime lanes NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
