#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess

from generate_m09_current_source_census import BASELINE, OUTPUT, ROOT, SOURCE_COMMIT, canonical_digest, generate
from validate_autonomous_maturity_evidence import validate_schema_node

SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m09-current-source-census-v1.schema.json"


def validate(path: Path = OUTPUT) -> None:
    data = json.loads(path.read_text())
    schema = json.loads(SCHEMA.read_text())
    validate_schema_node(data, schema, schema, "m09-current-source-census")
    expected = generate()
    if data != expected:
        raise ValueError("M09 current-source census differs from deterministic repository derivation")
    if hashlib.sha256(BASELINE.read_bytes()).hexdigest() != data["baseline"]["inventorySha256"]:
        raise ValueError("M00 baseline inventory digest mismatch")
    if data["sourceCommit"] != SOURCE_COMMIT:
        raise ValueError("M09 source commit mismatch")
    source_paths = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", SOURCE_COMMIT], cwd=ROOT, check=True,
        capture_output=True, text=True,
    ).stdout.splitlines()
    source_path_set = set(source_paths)
    if [item["path"] for item in data["files"]] != sorted(item["path"] for item in data["files"]):
        raise ValueError("M09 source paths are not sorted")
    for item in data["files"]:
        if item["path"] not in source_path_set:
            raise ValueError(f"M09 source absent from bound commit: {item['path']}")
        blob = subprocess.run(
            ["git", "show", f"{SOURCE_COMMIT}:{item['path']}"], cwd=ROOT, check=True, capture_output=True,
        ).stdout
        if hashlib.sha256(blob).hexdigest() != item["sha256"]:
            raise ValueError(f"M09 source differs from bound commit: {item['path']}")
    production = data["publicProduction"]
    if production["excessLines"] != max(0, production["physicalLines"] - data["targetPublicProductionLines"]):
        raise ValueError("M09 excess is not derived")
    if set(production["featureOwnerLines"]) != set(production["featureOwnerReasons"]):
        raise ValueError("M09 owner reasons are incomplete")
    if sum(production["featureOwnerLines"].values()) != production["physicalLines"]:
        raise ValueError("M09 owner totals do not cover production")
    if canonical_digest(data) != data["receiptDigest"]:
        raise ValueError("M09 receipt digest mismatch")


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}")
        return 1
    print("PASS: M09 current census is source-commit-bound and every production line has an owner reason")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
