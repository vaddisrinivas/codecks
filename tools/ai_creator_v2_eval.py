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
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "app/src/test/resources/ai/ai_creator_v2_eval_corpus.tsv"
REPORT = ROOT / "docs/ai/AI_CREATOR_V2_EVAL_REPORT.md"
BYPASS_CORPUS = ROOT / "app/src/test/resources/automation/generated_output_bypass_corpus.tsv"
JSON_REPORT = ROOT / "docs/ai/AI_CREATOR_V2_OFFLINE_EVAL_REPORT.json"
EXPECTED = {"Action": 40, "Deck": 40, "Automation": 40}
REPORT_SCHEMA_VERSION = 1


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
    if len({case_id for case_id, _ in rows}) != len(rows):
        raise SystemExit(f"{path}: duplicate bypass case id")
    if len(rows) < 10:
        raise SystemExit(f"{path}: expected at least 10 generated-output bypass cases")
    return rows


def report_contract(
    counts: dict[str, int],
    corpus_hash: str,
    bypass_rows: list[tuple[str, str]],
) -> dict[str, object]:
    return {
        "schemaVersion": REPORT_SCHEMA_VERSION,
        "mode": "offline_static",
        "providerCalls": False,
        "corpus": {
            "sha256": corpus_hash,
            "total": sum(counts.values()),
            "counts": {kind: counts[kind] for kind in sorted(counts)},
        },
        "generatedOutputBypassCorpus": {
            "sha256": canonical_hash(bypass_rows),
            "total": len(bypass_rows),
        },
        "requiredUnitGates": [
            "io.codecks.domain.ai.AiCreatorV2EvalCorpusTest",
            "io.codecks.core.actions.AiGeneratedContentPlannerTest",
            "io.codecks.domain.automation.AutomationExecutionPlanTest",
        ],
        "claims": {
            "corpusManifestValid": True,
            "providerQualityEvaluated": False,
            "generatedOutputPolicyEvaluatedByUnitGate": True,
        },
    }


def write_report(contract: dict[str, object], report_path: Path, json_path: Path) -> None:
    corpus = contract["corpus"]
    bypass = contract["generatedOutputBypassCorpus"]
    assert isinstance(corpus, dict)
    assert isinstance(bypass, dict)
    counts = corpus["counts"]
    assert isinstance(counts, dict)
    lines = [
        "# AI Creator V2 Eval Report",
        "",
        f"Offline report schema: {contract['schemaVersion']}",
        f"Corpus SHA-256: `{corpus['sha256']}`",
        f"Generated-output bypass SHA-256: `{bypass['sha256']}`",
        "",
        "## Corpus",
        "",
        f"- Total prompts: {corpus['total']}",
        f"- Action prompts: {counts['Action']}",
        f"- Deck prompts: {counts['Deck']}",
        f"- Automation prompts: {counts['Automation']}",
        f"- Generated-output bypass cases: {bypass['total']}",
        "",
        "## Proven Local Gates",
        "",
        "- Corpus has required 40/40/40 prompt split.",
        "- Unit tests verify strict V2 schema shape.",
        "- Unit tests verify parser success, refusal/needs-input handling, bounded repair, oversized deck rejection, missing-template rejection, dangerous-confirmation metadata, and adversarial command/URL rejection.",
        "- Unit tests verify generated artifacts cannot be saved before dry run evidence.",
        "- Unit tests require one deterministic assertion per normalized executable automation action.",
        "- Unit tests reject the checked-in generated-output bypass corpus.",
        "- Secret surface scan is required separately by release verification.",
        "- Live-provider scoring is available through the opt-in AiCreatorV2LiveEvalTest and writes docs/ai/AI_CREATOR_V2_LIVE_EVAL_REPORT.md.",
        "",
        "## Pending Live Gates",
        "",
        "- Run corpus against OpenAI, Anthropic, Gemini, and supported gateway models.",
        "- Measure first-pass semantic validity.",
        "- Measure validity after one bounded repair.",
        "- Confirm zero generated actions bypass review or deterministic policy checks.",
        "- Save provider metadata only; never store API keys or raw auth headers.",
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
        "--check-report",
        action="store_true",
        help="fail if checked-in offline reports differ from deterministic output",
    )
    args = parser.parse_args()

    rows = read_corpus(CORPUS)
    counts = validate_counts(rows)
    bypass_rows = read_bypass_corpus(BYPASS_CORPUS)
    contract = report_contract(counts, canonical_hash(rows), bypass_rows)
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
        f"bypass={len(bypass_rows)} sha256={contract['corpus']['sha256']}"
    )


if __name__ == "__main__":
    main()
