#!/usr/bin/env python3
"""Local AI Creator V2 eval manifest checker.

This intentionally does not call live providers. It verifies the checked-in corpus
shape and writes a local report that separates proven local gates from pending
live-provider scoring.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import subprocess
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "app/src/test/resources/ai/ai_creator_v2_eval_corpus.tsv"
COVERAGE = ROOT / "app/src/test/resources/ai/ai_creator_v2_eval_coverage.tsv"
CASE_RESULTS = ROOT / "app/build/reports/ai_creator_v2_m17_case_results.tsv"
BYPASS_RESULTS = ROOT / "app/build/reports/ai_creator_v2_m17_bypass_results.tsv"
REPORT = ROOT / "docs/ai/AI_CREATOR_V2_EVAL_REPORT.md"
BYPASS_CORPUS = ROOT / "app/src/test/resources/automation/generated_output_bypass_corpus.tsv"
JSON_REPORT = ROOT / "docs/ai/AI_CREATOR_V2_OFFLINE_EVAL_REPORT.json"
EXPECTED = {"Action": 40, "Deck": 40, "Automation": 40}
REPORT_SCHEMA_VERSION = 2
CORPUS_SCHEMA_VERSION = 2
REQUIRED_CATEGORIES = {
    "button",
    "deck",
    "automation",
    "unsupported_theme",
    "artifact_conversion",
    "artifact_codec_roundtrip",
    "refine_once",
    "malformed_output",
    "adversarial_command",
    "prompt_injection",
    "oversized_content",
    "provider_error",
    "review_metadata",
    "disabled_automation",
    "regenerate",
}
EXPECTED_BYPASS_IDS = [
    "unsupported_echo",
    "secret_env",
    "arbitrary_script",
    "variable_sudo",
    "download_only",
    "compound_open",
    "arbitrary_shell",
    "redirect_file",
    "network_probe",
    "credential_helper",
    "spoofed_visual",
    "unknown_tool",
    "command_substitution",
    "backtick_substitution",
    "variable_expansion",
    "generic_applescript",
    "absolute_nested_shell",
    "absolute_interpreter",
    "forged_visual_marker",
]
EXPECTED_BYPASS_SHA256 = "833f4e13b91e5174e0c43ae7f756a72e3b7638b533fa599fb191124789659d2d"
RESULT_COLUMNS = [
    "caseId",
    "category",
    "outcomeMatched",
    "parserConformance",
    "safeSemanticOutcome",
    "artifactConversion",
    "artifactCodecRoundTrip",
    "reviewMetadata",
    "disabledAutomation",
    "refineOnce",
    "regenerate",
    "actionableFailure",
]
REQUIRED_UNIT_GATES = [
    "io.codecks.domain.ai.AiCreatorV2EvalCorpusTest",
    "io.codecks.domain.ai.AiCreatorM17BenchmarkTest",
    "io.codecks.domain.ai.AiCreatorV2AdversarialTest",
    "io.codecks.domain.ai.AiBuilderV2RepairTest",
    "io.codecks.core.actions.AiGeneratedContentPlannerTest",
    "io.codecks.core.actions.AiDraftConvertersTest",
    "io.codecks.core.actions.ActionRunnerTest",
    "io.codecks.data.ai.AiArtifactJsonCodecTest",
    "io.codecks.data.ai.AiProviderContractTest",
    "io.codecks.data.RawCommandPolicyTest",
    "io.codecks.domain.ai.MacVisualEffectCatalogTest",
    "io.codecks.domain.ai.StructuredDraftParserV2Test",
    "io.codecks.domain.automation.AutomationExecutionPlanTest",
]


def receipt_source_paths() -> list[Path]:
    """Bind receipts to every local AI policy/compiler source and test dependency."""
    roots = [
        ROOT / "app/src/main/java/io/codecks/core/actions",
        ROOT / "app/src/main/java/io/codecks/data/ai",
        ROOT / "app/src/main/java/io/codecks/domain/ai",
        ROOT / "app/src/main/java/io/codecks/domain/automation",
        ROOT / "app/src/test/java/io/codecks/core/actions",
        ROOT / "app/src/test/java/io/codecks/data/ai",
        ROOT / "app/src/test/java/io/codecks/domain/ai",
        ROOT / "app/src/test/java/io/codecks/domain/automation",
        ROOT / "app/src/test/java/io/codecks/data",
    ]
    paths = {
        ROOT / "app/build.gradle.kts",
        ROOT / "tools/ai_creator_v2_eval.py",
        ROOT / "tools/tests/test_ai_creator_v2_eval.py",
    }
    for source_root in roots:
        paths.update(source_root.rglob("*.kt"))
    return sorted(paths)


def receipt_source_hash() -> str:
    digest = hashlib.sha256()
    for path in receipt_source_paths():
        relative = path.relative_to(ROOT).as_posix().encode("utf-8")
        digest.update(len(relative).to_bytes(4, "big"))
        digest.update(relative)
        payload = path.read_bytes()
        digest.update(len(payload).to_bytes(8, "big"))
        digest.update(payload)
    return digest.hexdigest()


def read_corpus(path: Path) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip():
            continue
        if "\t" not in raw:
            raise SystemExit(f"{path}:{line_number}: expected '<Kind>\\t<prompt>'")
        kind, prompt = raw.split("\t", 1)
        if kind not in EXPECTED:
            raise SystemExit(f"{path}:{line_number}: unsupported kind {kind}")
        if len(prompt.strip()) < 8:
            raise SystemExit(f"{path}:{line_number}: prompt too short")
        rows.append((kind, prompt.strip()))
    return rows


def validate_counts(rows: list[tuple[str, str]]) -> dict[str, int]:
    counts = collections.Counter(kind for kind, _ in rows)
    if sum(counts.values()) != sum(EXPECTED.values()):
        raise SystemExit(f"expected {sum(EXPECTED.values())} prompts, found {sum(counts.values())}")
    for kind, expected in EXPECTED.items():
        found = counts.get(kind, 0)
        if found != expected:
            raise SystemExit(f"expected {expected} {kind} prompts, found {found}")
    return dict(counts)


def canonical_hash(rows: list[tuple[str, str]]) -> str:
    payload = "".join(f"{kind}\t{value}\n" for kind, value in rows)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def read_coverage(path: Path, total: int) -> tuple[int, dict[str, list[int]]]:
    lines = [line for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not lines or lines[0] != f"schemaVersion\t{CORPUS_SCHEMA_VERSION}":
        raise SystemExit(f"{path}: expected schemaVersion {CORPUS_SCHEMA_VERSION}")
    assignments: dict[str, list[int]] = {}
    seen: set[int] = set()
    for line_number, raw in enumerate(lines[1:], start=2):
        fields = raw.split("\t")
        if len(fields) != 2:
            raise SystemExit(f"{path}:{line_number}: expected '<category>\\t<first>-<last>'")
        category, range_text = fields
        if category in assignments:
            raise SystemExit(f"{path}:{line_number}: duplicate category {category}")
        try:
            first_text, last_text = range_text.split("-", 1)
            values = list(range(int(first_text), int(last_text) + 1))
        except ValueError as error:
            raise SystemExit(f"{path}:{line_number}: invalid range {range_text}") from error
        if not values or values[0] < 1 or values[-1] > total:
            raise SystemExit(f"{path}:{line_number}: range outside 1..{total}")
        overlap = seen.intersection(values)
        if overlap:
            raise SystemExit(f"{path}:{line_number}: duplicate case assignments {sorted(overlap)}")
        seen.update(values)
        assignments[category] = values
    missing_categories = REQUIRED_CATEGORIES - assignments.keys()
    unknown_categories = assignments.keys() - REQUIRED_CATEGORIES
    if missing_categories or unknown_categories:
        raise SystemExit(
            f"{path}: category mismatch missing={sorted(missing_categories)} unknown={sorted(unknown_categories)}"
        )
    expected_cases = set(range(1, total + 1))
    if seen != expected_cases:
        raise SystemExit(
            f"{path}: coverage must assign every case exactly once; missing={sorted(expected_cases - seen)}"
        )
    return CORPUS_SCHEMA_VERSION, assignments


def read_bypass_corpus(path: Path) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip():
            continue
        if "\t" not in raw:
            raise SystemExit(f"{path}:{line_number}: expected '<case-id>\\t<command>'")
        case_id, command = raw.split("\t", 1)
        if not case_id or not command:
            raise SystemExit(f"{path}:{line_number}: bypass case id and command are required")
        rows.append((case_id, command))
    if [case_id for case_id, _ in rows] != EXPECTED_BYPASS_IDS:
        raise SystemExit(f"{path}: immutable bypass case IDs/order changed")
    if len(rows) != 19:
        raise SystemExit(f"{path}: expected exactly 19 generated-output bypass cases")
    if canonical_hash(rows) != EXPECTED_BYPASS_SHA256:
        raise SystemExit(f"{path}: immutable bypass corpus digest changed")
    return rows


def read_case_results(path: Path, coverage: dict[str, list[int]]) -> list[dict[str, Any]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if len(lines) != 122 or lines[0] != "schemaVersion\t2":
        raise SystemExit(f"{path}: expected schema 2 and exactly 120 result rows")
    if lines[1].split("\t") != RESULT_COLUMNS:
        raise SystemExit(f"{path}: unexpected result columns")
    expected_category = {
        case_number: category
        for category, case_numbers in coverage.items()
        for case_number in case_numbers
    }
    rows: list[dict[str, Any]] = []
    for case_number, raw in enumerate(lines[2:], start=1):
        fields = raw.split("\t")
        if len(fields) != len(RESULT_COLUMNS):
            raise SystemExit(f"{path}:{case_number + 2}: wrong field count")
        row: dict[str, Any] = dict(zip(RESULT_COLUMNS, fields))
        expected_id = f"m17-{case_number:03d}"
        if row["caseId"] != expected_id or row["category"] != expected_category[case_number]:
            raise SystemExit(f"{path}:{case_number + 2}: case identity/category mismatch")
        for column in RESULT_COLUMNS[2:]:
            value = row[column]
            if value not in {"0", "1", "-"}:
                raise SystemExit(f"{path}:{case_number + 2}: invalid {column} value")
            row[column] = None if value == "-" else value == "1"
        rows.append(row)
    if any(row["outcomeMatched"] is not True for row in rows):
        failed = [row["caseId"] for row in rows if row["outcomeMatched"] is not True]
        raise SystemExit(f"{path}: benchmark outcomes failed: {failed}")
    return rows


def read_bypass_results(path: Path) -> list[dict[str, Any]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if len(lines) != 21 or lines[0] != "schemaVersion\t2":
        raise SystemExit(f"{path}: expected schema 2 and exactly 19 result rows")
    if lines[1] != "caseId\tdenied\tactionable":
        raise SystemExit(f"{path}: unexpected bypass result columns")
    rows: list[dict[str, Any]] = []
    for index, raw in enumerate(lines[2:]):
        fields = raw.split("\t")
        if len(fields) != 3 or fields[0] != EXPECTED_BYPASS_IDS[index] or any(value not in {"0", "1"} for value in fields[1:]):
            raise SystemExit(f"{path}:{index + 3}: immutable bypass result mismatch")
        rows.append({"caseId": fields[0], "denied": fields[1] == "1", "actionable": fields[2] == "1"})
    return rows


def result_metric(rows: list[dict[str, Any]], field: str, threshold: float) -> dict[str, Any]:
    eligible = [row[field] for row in rows if row[field] is not None]
    passed = sum(value is True for value in eligible)
    return metric(passed, len(eligible), threshold, True)


def combined_metrics(
    case_rows: list[dict[str, Any]],
    bypass_rows: list[dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    bypasses = sum(row["denied"] is not True for row in bypass_rows)
    metrics = {
        "caseOutcome": result_metric(case_rows, "outcomeMatched", 1.0),
        "parserConformance": result_metric(case_rows, "parserConformance", 0.99),
        "safeSemanticValidity": result_metric(case_rows, "safeSemanticOutcome", 0.95),
        "artifactConversion": result_metric(case_rows, "artifactConversion", 1.0),
        "artifactCodecRoundTrip": result_metric(case_rows, "artifactCodecRoundTrip", 1.0),
        "reviewMetadata": result_metric(case_rows, "reviewMetadata", 1.0),
        "disabledAutomation": result_metric(case_rows, "disabledAutomation", 1.0),
        "refineOnce": result_metric(case_rows, "refineOnce", 1.0),
        "regenerate": result_metric(case_rows, "regenerate", 1.0),
        "actionableFailure": result_metric(case_rows, "actionableFailure", 1.0),
        "policyBypass": zero_metric(bypasses, len(bypass_rows), True),
        "bypassActionable": metric(
            sum(row["actionable"] is True for row in bypass_rows),
            len(bypass_rows),
            1.0,
            True,
        ),
    }
    if any(item["status"] != "PASS" for item in metrics.values()):
        raise SystemExit("combined deterministic benchmark metric failed")
    return metrics


def run_unit_gates(
    enabled: bool,
    corpus_hash: str,
    bypass_hash: str,
    coverage_hash: str,
    coverage: dict[str, list[int]],
    required_gates: list[str],
) -> dict[str, object] | None:
    if not enabled:
        return None
    command = [str(ROOT / "gradlew"), "--no-daemon", "--rerun-tasks", ":app:testOssReleaseUnitTest"]
    for gate in required_gates:
        command += ["--tests", gate]
    completed = subprocess.run(command, cwd=ROOT, check=False)
    if completed.returncode != 0:
        raise SystemExit("required unit gates failed; no receipt created")
    case_rows = read_case_results(CASE_RESULTS, coverage)
    bypass_result_rows = read_bypass_results(BYPASS_RESULTS)
    return {
        "schemaVersion": 2,
        "sourceSha256": receipt_source_hash(),
        "corpusSha256": corpus_hash,
        "bypassSha256": bypass_hash,
        "coverageSha256": coverage_hash,
        "caseResultsSha256": hashlib.sha256(CASE_RESULTS.read_bytes()).hexdigest(),
        "bypassResultsSha256": hashlib.sha256(BYPASS_RESULTS.read_bytes()).hexdigest(),
        "passedUnitGates": required_gates,
        "combinedMetrics": combined_metrics(case_rows, bypass_result_rows),
    }


def report_contract(
    counts: dict[str, int],
    corpus_hash: str,
    bypass_rows: list[tuple[str, str]],
    coverage_version: int,
    coverage: dict[str, list[int]],
    coverage_hash: str,
    run_gates: bool = False,
    receipt_override: dict[str, object] | None = None,
) -> dict[str, object]:
    required_gates = REQUIRED_UNIT_GATES
    bypass_hash = canonical_hash(bypass_rows)
    receipt = (
        run_unit_gates(True, corpus_hash, bypass_hash, coverage_hash, coverage, required_gates)
        if run_gates
        else receipt_override
    )
    evaluated = receipt is not None
    metric_thresholds = {
        "caseOutcome": 1.0,
        "parserConformance": 0.99,
        "safeSemanticValidity": 0.95,
        "artifactConversion": 1.0,
        "artifactCodecRoundTrip": 1.0,
        "reviewMetadata": 1.0,
        "disabledAutomation": 1.0,
        "refineOnce": 1.0,
        "regenerate": 1.0,
        "actionableFailure": 1.0,
        "policyBypass": 0.0,
        "bypassActionable": 1.0,
    }
    metrics: dict[str, dict[str, Any]] = (
        receipt["combinedMetrics"]
        if receipt is not None
        else {
            name: {
                "status": "NOT_RUN",
                "threshold": threshold,
                "passed": None,
                "total": None,
            }
            for name, threshold in metric_thresholds.items()
        }
    )
    return {
        "schemaVersion": REPORT_SCHEMA_VERSION,
        "mode": "offline_deterministic",
        "providerCalls": False,
        "corpus": {
            "schemaVersion": coverage_version,
            "sha256": corpus_hash,
            "total": sum(counts.values()),
            "counts": {kind: counts[kind] for kind in sorted(counts)},
        },
        "categoryCoverage": {
            "sha256": coverage_hash,
            "counts": {category: len(coverage[category]) for category in sorted(coverage)},
        },
        "generatedOutputBypassCorpus": {
            "sha256": bypass_hash,
            "total": len(bypass_rows),
        },
        "requiredUnitGates": required_gates,
        "unitGateReceipt": receipt,
        "combinedMetrics": metrics,
        "overallStatus": (
            "NOT_RUN"
            if not evaluated
            else "PASS"
            if all(item["status"] == "PASS" for item in metrics.values())
            else "FAIL"
        ),
        "liveProviders": [
            {"provider": provider, "status": "NOT_RUN", "reason": "Local deterministic benchmark; credentials and network are intentionally unused."}
            for provider in ("OpenAI-compatible", "Anthropic", "Gemini", "gateway/custom")
        ],
        "claims": {
            "corpusManifestValid": True,
            "providerQualityEvaluated": False,
            "deterministicPipelineEvaluated": evaluated,
            "generatedOutputPolicyEvaluatedByUnitGate": receipt is not None,
        },
    }


def metric(passed: int, total: int, threshold: float, evaluated: bool) -> dict[str, Any]:
    rate = passed / total if total else 0.0
    return {
        "passed": passed if evaluated else None,
        "total": total,
        "rate": rate if evaluated else None,
        "threshold": threshold,
        "status": "NOT_RUN" if not evaluated else "PASS" if rate >= threshold else "FAIL",
    }


def zero_metric(bypasses: int, attempts: int, evaluated: bool) -> dict[str, Any]:
    return {
        "bypasses": bypasses if evaluated else None,
        "attempts": attempts,
        "maximumAllowed": 0,
        "status": "NOT_RUN" if not evaluated else "PASS" if bypasses == 0 else "FAIL",
    }


def write_report(contract: dict[str, object], report_path: Path, json_path: Path) -> None:
    corpus = contract["corpus"]
    bypass = contract["generatedOutputBypassCorpus"]
    coverage = contract["categoryCoverage"]
    metrics = contract["combinedMetrics"]
    assert isinstance(corpus, dict)
    assert isinstance(bypass, dict)
    assert isinstance(coverage, dict)
    assert isinstance(metrics, dict)
    counts = corpus["counts"]
    assert isinstance(counts, dict)
    lines = [
        "# AI Creator V2 Eval Report",
        "",
        f"Offline report schema: {contract['schemaVersion']}",
        f"Corpus SHA-256: `{corpus['sha256']}`",
        f"Generated-output bypass SHA-256: `{bypass['sha256']}`",
        f"Coverage manifest SHA-256: `{coverage['sha256']}`",
        "",
        "## Corpus",
        "",
        f"- Total prompts: {corpus['total']}",
        f"- Action prompts: {counts['Action']}",
        f"- Deck prompts: {counts['Deck']}",
        f"- Automation prompts: {counts['Automation']}",
        f"- Generated-output bypass cases: {bypass['total']}",
        f"- Required categories covered: {len(coverage['counts'])}",
        "",
        "## Verified Static Facts",
        "",
        "- Corpus has required 40/40/40 prompt split and versioned, exhaustive category assignments.",
        "- Corpus files have the recorded hashes and required case counts.",
        "- Unit gates listed below are requirements, not proven executions, unless `unitGateReceipt` is non-null in the JSON report.",
        f"- SHA-bound unit-gate receipt supplied: {'yes' if contract['unitGateReceipt'] else 'no'}.",
        f"- Combined deterministic verdict: `{contract['overallStatus']}`.",
        (
            "- Current SHA-bound execution proof makes the combined deterministic metrics eligible for `PASS`/`FAIL`."
            if contract["unitGateReceipt"]
            else "- Missing or stale execution proof leaves every combined metric `NOT_RUN`; static fixtures never imply execution."
        ),
        "",
        "## Combined Metrics",
        "",
    ]
    for name, value in metrics.items():
        assert isinstance(value, dict)
        if value["status"] == "NOT_RUN":
            detail = "not executed"
        elif "total" in value:
            detail = f"{value.get('passed')}/{value['total']}"
        else:
            detail = f"bypasses={value.get('bypasses')}/{value['attempts']}"
        lines.append(f"- {name}: `{value['status']}` ({detail})")
    lines += [
        "",
        "## Live Providers",
        "",
    ]
    for provider in contract["liveProviders"]:
        lines.append(f"- {provider['provider']}: `NOT_RUN` — {provider['reason']}")
    lines += [
        "",
    ]
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(lines), encoding="utf-8")
    json_path.write_text(
        json.dumps(contract, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--write-report",
        action="store_true",
        help="write deterministic Markdown and JSON offline reports",
    )
    parser.add_argument(
        "--receipt",
        type=Path,
        default=ROOT / "build/reports/ai_creator_v2_unit_receipt.json",
        help="deterministic unit-gate receipt path",
    )
    parser.add_argument("--check-receipt", action="store_true")
    parser.add_argument(
        "--run-unit-gates",
        action="store_true",
        help="run the exact required Gradle tests and create a source-bound receipt",
    )
    parser.add_argument(
        "--check-report",
        action="store_true",
        help="fail if checked-in offline reports differ from deterministic output",
    )
    args = parser.parse_args()

    rows = read_corpus(CORPUS)
    counts = validate_counts(rows)
    coverage_version, coverage = read_coverage(COVERAGE, len(rows))
    coverage_rows = [(category, f"{values[0]}-{values[-1]}") for category, values in coverage.items()]
    coverage_hash = canonical_hash(coverage_rows)
    bypass_rows = read_bypass_corpus(BYPASS_CORPUS)
    contract = report_contract(
        counts,
        canonical_hash(rows),
        bypass_rows,
        coverage_version,
        coverage,
        coverage_hash,
        args.run_unit_gates,
    )
    if args.run_unit_gates:
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.write_text(json.dumps(contract["unitGateReceipt"], indent=2, sort_keys=True) + "\n")
        print(f"unit receipt: {args.receipt}")
    if args.check_receipt:
        expected = report_contract(counts, canonical_hash(rows), bypass_rows, coverage_version, coverage, coverage_hash, False)
        current = json.loads(args.receipt.read_text(encoding="utf-8"))
        case_rows = read_case_results(CASE_RESULTS, coverage)
        bypass_result_rows = read_bypass_results(BYPASS_RESULTS)
        # A receipt is valid only for the current exact sources/corpora and required gate set.
        required = {
            "schemaVersion": 2,
            "sourceSha256": receipt_source_hash(),
            "corpusSha256": expected["corpus"]["sha256"],
            "bypassSha256": expected["generatedOutputBypassCorpus"]["sha256"],
            "coverageSha256": expected["categoryCoverage"]["sha256"],
            "caseResultsSha256": hashlib.sha256(CASE_RESULTS.read_bytes()).hexdigest(),
            "bypassResultsSha256": hashlib.sha256(BYPASS_RESULTS.read_bytes()).hexdigest(),
            "passedUnitGates": expected["requiredUnitGates"],
            "combinedMetrics": combined_metrics(case_rows, bypass_result_rows),
        }
        if current != required:
            raise SystemExit(f"{args.receipt}: stale or invalid")
        if not args.run_unit_gates:
            contract = report_contract(
                counts,
                canonical_hash(rows),
                bypass_rows,
                coverage_version,
                coverage,
                coverage_hash,
                receipt_override=current,
            )
    markdown_before = REPORT.read_text(encoding="utf-8") if REPORT.exists() else None
    json_before = JSON_REPORT.read_text(encoding="utf-8") if JSON_REPORT.exists() else None
    if args.write_report:
        write_report(contract, REPORT, JSON_REPORT)
    if args.check_report:
        expected_markdown = REPORT.with_suffix(".expected.tmp")
        expected_json = JSON_REPORT.with_suffix(".expected.tmp")
        try:
            write_report(contract, expected_markdown, expected_json)
            if markdown_before != expected_markdown.read_text(encoding="utf-8"):
                raise SystemExit(f"{REPORT}: stale; run --write-report")
            if json_before != expected_json.read_text(encoding="utf-8"):
                raise SystemExit(f"{JSON_REPORT}: stale; run --write-report")
        finally:
            expected_markdown.unlink(missing_ok=True)
            expected_json.unlink(missing_ok=True)
    print(
        "ai creator v2 offline corpus OK: "
        f"total={sum(counts.values())} action={counts['Action']} "
        f"deck={counts['Deck']} automation={counts['Automation']} "
        f"categories={len(coverage)} bypass={len(bypass_rows)} "
        f"status={contract['overallStatus']} sha256={contract['corpus']['sha256']}"
    )


if __name__ == "__main__":
    main()
