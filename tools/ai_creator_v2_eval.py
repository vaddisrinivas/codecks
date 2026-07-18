#!/usr/bin/env python3
"""Local AI Creator V2 eval manifest checker.

This intentionally does not call live providers. It verifies the checked-in corpus
shape and writes a local report that separates proven local gates from pending
live-provider scoring.
"""

from __future__ import annotations

import argparse
import collections
import datetime as _datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "app/src/test/resources/ai/ai_creator_v2_eval_corpus.tsv"
REPORT = ROOT / "docs/ai/AI_CREATOR_V2_EVAL_REPORT.md"
EXPECTED = {"Action": 40, "Deck": 40, "Automation": 40}


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


def write_report(counts: dict[str, int], report_path: Path) -> None:
    today = _datetime.date.today().isoformat()
    lines = [
        "# AI Creator V2 Eval Report",
        "",
        f"Generated: {today}",
        "",
        "## Corpus",
        "",
        f"- Total prompts: {sum(counts.values())}",
        f"- Action prompts: {counts['Action']}",
        f"- Deck prompts: {counts['Deck']}",
        f"- Automation prompts: {counts['Automation']}",
        "",
        "## Proven Local Gates",
        "",
        "- Corpus has required 40/40/40 prompt split.",
        "- Unit tests verify strict V2 schema shape.",
        "- Unit tests verify parser success, refusal/needs-input handling, bounded repair, oversized deck rejection, missing-template rejection, dangerous-confirmation metadata, and adversarial command/URL rejection.",
        "- Unit tests verify generated artifacts cannot be saved before dry run evidence.",
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-report", action="store_true", help="write docs/ai/AI_CREATOR_V2_EVAL_REPORT.md")
    args = parser.parse_args()

    rows = read_corpus(CORPUS)
    counts = validate_counts(rows)
    if args.write_report:
        write_report(counts, REPORT)
    print(f"ai creator v2 corpus OK: total={sum(counts.values())} action={counts['Action']} deck={counts['Deck']} automation={counts['Automation']}")


if __name__ == "__main__":
    main()
