#!/usr/bin/env python3
"""Generate the current-source M09 census without mutating the M00 baseline."""

from __future__ import annotations

import hashlib
import json
from argparse import ArgumentParser
from collections import Counter
from pathlib import Path

from generate_autonomous_maturity_source_inventory import (
    SOURCE_SUFFIXES,
    atomic_write,
    classify,
    read_source,
    tracked_sources,
)

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "tasks/test-evidence/autonomous-maturity-m09-current-source-census.json"
BASELINE = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"
SOURCE_COMMIT = "08c9ae50ee0a46ad1682b03c4989130ae78f2bad"
BASELINE_COMMIT = "d1f1788f03fe59bb0dceb5822e9f5b19194090fd"
TARGET = 50_000
METHOD = {
    "scope": "tracked *.kt, *.java, and *.swift files from git ls-files",
    "lineDefinition": "Python str.splitlines physical lines; blank and comment lines included",
    "classification": "path/source-set rules from the M00 inventory generator; M09 owner rules are versioned here",
}


def feature_owner(path: str) -> str:
    marker = "/java/io/codecks/"
    if marker in path:
        tail = path.split(marker, 1)[1].split("/")
        if len(tail) == 1:
            return "app_shell"
        if tail[1].endswith((".kt", ".java")):
            return tail[0]
        return "/".join(tail[:2])
    if path.startswith("shared/"):
        return "shared_protocol"
    if path.startswith("backend/"):
        return "backend_contracts"
    if path.startswith("macHelper/"):
        return "mac_helper"
    return "other"


def owner_reason(owner: str) -> str:
    readable = owner.replace("/", " / ").replace("_", " ")
    return f"Feature-owned source for the typed {readable} boundary; retained as current product or platform implementation."


def canonical_digest(data: dict) -> str:
    copy = dict(data)
    copy.pop("receiptDigest", None)
    return hashlib.sha256(json.dumps(copy, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def generate(root: Path = ROOT) -> dict:
    entries: list[dict] = []
    category_lines: Counter[str] = Counter()
    source_set_lines: Counter[str] = Counter()
    language_lines: Counter[str] = Counter()
    owner_lines: Counter[str] = Counter()
    for relative in tracked_sources(root):
        raw = read_source(root, relative)
        lines = len(raw.decode("utf-8").splitlines())
        category, source_set = classify(relative)
        owner = feature_owner(relative)
        entry = {
            "path": relative,
            "category": category,
            "sourceSet": source_set,
            "featureOwner": owner,
            "language": SOURCE_SUFFIXES[Path(relative).suffix],
            "physicalLines": lines,
            "sha256": hashlib.sha256(raw).hexdigest(),
        }
        entries.append(entry)
        category_lines[category] += lines
        source_set_lines[source_set] += lines
        language_lines[entry["language"]] += lines
        if category == "public_production":
            owner_lines[owner] += lines

    baseline_raw = BASELINE.read_bytes()
    baseline = json.loads(baseline_raw)
    production = category_lines["public_production"]
    production_files = [item for item in entries if item["category"] == "public_production"]
    largest = max(production_files, key=lambda item: (item["physicalLines"], item["path"]))
    owners = dict(sorted(owner_lines.items()))
    data = {
        "schema": "codecks.autonomous-maturity.m09-current-source-census.v1",
        "sourceCommit": SOURCE_COMMIT,
        "baseline": {
            "commit": BASELINE_COMMIT,
            "inventorySha256": hashlib.sha256(baseline_raw).hexdigest(),
            "summary": baseline["summary"],
        },
        "method": METHOD,
        "targetPublicProductionLines": TARGET,
        "summary": {
            "fileCount": len(entries),
            "physicalLines": sum(item["physicalLines"] for item in entries),
            "byCategory": dict(sorted(category_lines.items())),
            "bySourceSet": dict(sorted(source_set_lines.items())),
            "byLanguage": dict(sorted(language_lines.items())),
        },
        "publicProduction": {
            "physicalLines": production,
            "excessLines": max(0, production - TARGET),
            "featureOwnerLines": owners,
            "featureOwnerReasons": {owner: owner_reason(owner) for owner in owners},
            "allLinesAssigned": sum(owners.values()) == production,
            "largestFile": {"path": largest["path"], "physicalLines": largest["physicalLines"]},
            "filesOver1000Lines": sorted(item["path"] for item in production_files if item["physicalLines"] > 1000),
        },
        "boundaries": {
            "codeCutForTarget": False,
            "startupMeasured": False,
            "apkMeasured": False,
            "deviceMeasured": False,
            "releaseAdmission": False,
        },
        "files": entries,
    }
    data["receiptDigest"] = canonical_digest(data)
    return data


def main() -> None:
    parser = ArgumentParser()
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    payload = (json.dumps(generate(), indent=2) + "\n").encode()
    if args.check:
        if args.output.read_bytes() != payload:
            raise ValueError(f"M09 current-source census is stale: {args.output}")
        print("PASS: M09 current-source census matches exact source and owner totals")
    else:
        atomic_write(ROOT, args.output.absolute(), payload)


if __name__ == "__main__":
    main()
