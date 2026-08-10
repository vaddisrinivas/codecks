#!/usr/bin/env python3
"""Generate a deterministic inventory of tracked Kotlin, Java, and Swift sources."""

from __future__ import annotations

import hashlib
import json
import subprocess
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"
SOURCE_SUFFIXES = {".kt": "Kotlin", ".java": "Java", ".swift": "Swift"}
METHOD = {
    "scope": "tracked *.kt, *.java, and *.swift files from git ls-files",
    "line_definition": "Python str.splitlines physical lines; blank and comment lines included",
    "generated_policy": "tracked build/generated sources are separate; untracked build outputs are excluded",
    "classification": "path/source-set rules in tools/evidence/generate_autonomous_maturity_source_inventory.py",
}


def tracked_sources() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "*.kt", "*.java", "*.swift"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return sorted(path for path in result.stdout.splitlines() if path)


def classify(path: str) -> tuple[str, str]:
    parts = Path(path).parts
    if path == "macHelper/Package.swift":
        return "build_definition", "mac_helper_package_manifest"
    if "build" in parts and "generated" in parts:
        return "generated", "generated"
    if path.startswith("macHelper/Tests/"):
        return "tests", "mac_helper_tests"
    if path.startswith("macHelper/Sources/"):
        return "companion", "mac_helper"
    if path.startswith("shared/src/"):
        source_set = parts[2]
        if "test" in source_set.lower():
            return "tests", f"shared_{source_set}"
        return "companion", f"shared_{source_set}"
    if path.startswith("backend/src/"):
        source_set = parts[2]
        if "test" in source_set.lower():
            return "tests", f"backend_{source_set}"
        return "companion", f"backend_{source_set}"
    if path.startswith("app/src/"):
        source_set = parts[2]
        lowered = source_set.lower()
        if "test" in lowered:
            return "tests", f"android_{source_set}"
        if source_set == "main":
            return "public_production", "android_main"
        if source_set == "oss":
            return "public_production", "android_oss"
        if source_set == "play":
            return "public_production", "android_play_production_dark"
        if source_set == "playInternal":
            return "internal_lab", "android_play_internal"
        if source_set == "debug":
            return "debug_only", "android_debug"
        return "other_source_set", f"android_{source_set}"
    return "other_source_set", "repository_other"


def owner(path: str) -> str:
    marker = "/java/io/codecks/"
    if marker in path:
        tail = path.split(marker, 1)[1].split("/")
        return "/".join(tail[:2]) if len(tail) > 1 else "root"
    if path.startswith("shared/"):
        return "shared_protocol"
    if path.startswith("backend/"):
        return "backend_contracts"
    if path.startswith("macHelper/"):
        return "mac_helper"
    return "other"


def main() -> None:
    entries = []
    category_lines: Counter[str] = Counter()
    source_set_lines: Counter[str] = Counter()
    language_lines: Counter[str] = Counter()

    for relative in tracked_sources():
        raw = (ROOT / relative).read_bytes()
        text = raw.decode("utf-8")
        lines = len(text.splitlines())
        category, source_set = classify(relative)
        language = SOURCE_SUFFIXES[Path(relative).suffix]
        entry = {
            "path": relative,
            "category": category,
            "source_set": source_set,
            "feature_owner": owner(relative),
            "language": language,
            "physical_lines": lines,
            "sha256": hashlib.sha256(raw).hexdigest(),
        }
        entries.append(entry)
        category_lines[category] += lines
        source_set_lines[source_set] += lines
        language_lines[language] += lines

    payload = {
        "schema": "codecks.autonomous-maturity.source-inventory.v1",
        "method": METHOD,
        "summary": {
            "file_count": len(entries),
            "physical_lines": sum(item["physical_lines"] for item in entries),
            "by_category": dict(sorted(category_lines.items())),
            "by_source_set": dict(sorted(source_set_lines.items())),
            "by_language": dict(sorted(language_lines.items())),
        },
        "files": entries,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(payload, indent=2, sort_keys=False) + "\n")


if __name__ == "__main__":
    main()
