#!/usr/bin/env python3
"""Validate the M02 reachability receipt and its post-change source inventory."""

from __future__ import annotations

import hashlib
import json
import subprocess
from collections import Counter
from pathlib import Path

from validate_autonomous_maturity_evidence import (
    ROOT,
    INVENTORY_SCHEMA,
    METHOD,
    load,
    reject_sensitive_values,
    timestamp,
    validate_schema_node,
)


RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m02-dead-surfaces.json"
AFTER_INVENTORY = ROOT / "tasks/test-evidence/autonomous-maturity-m02-source-inventory.json"
BASELINE_INVENTORY = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m02-dead-surfaces-v1.schema.json"


def fail(message: str) -> None:
    raise ValueError(message)


def git_path_exists(commit: str, path: str) -> bool:
    return subprocess.run(
        ["git", "cat-file", "-e", f"{commit}:{path}"],
        cwd=ROOT,
        capture_output=True,
    ).returncode == 0


def indexed_changes(base_commit: str) -> list[str]:
    result = subprocess.run(
        ["git", "diff", "--cached", "--name-only", base_commit],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return sorted(result.stdout.splitlines())


def is_base_checkout(base_commit: str) -> bool:
    return subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip() == base_commit


def validate_recorded_inventory(data: dict) -> None:
    """Validate an immutable historical inventory without rebinding it to today's tree."""
    schema = load(INVENTORY_SCHEMA)
    validate_schema_node(data, schema, schema, "m02.inventory")
    if data["method"] != METHOD:
        fail("recorded inventory method mismatch")
    files = data["files"]
    paths = [entry["path"] for entry in files]
    if paths != sorted(paths) or len(paths) != len(set(paths)):
        fail("recorded inventory paths must be sorted and unique")
    by_category: Counter[str] = Counter()
    by_source_set: Counter[str] = Counter()
    by_language: Counter[str] = Counter()
    physical_lines = 0
    for entry in files:
        lines = entry["physical_lines"]
        physical_lines += lines
        by_category[entry["category"]] += lines
        by_source_set[entry["source_set"]] += lines
        by_language[entry["language"]] += lines
    expected = {
        "file_count": len(files),
        "physical_lines": physical_lines,
        "by_category": dict(sorted(by_category.items())),
        "by_source_set": dict(sorted(by_source_set.items())),
        "by_language": dict(sorted(by_language.items())),
    }
    if data["summary"] != expected:
        fail("recorded inventory summary mismatch")


def validate() -> None:
    receipt_raw = RECEIPT.read_bytes()
    after_raw = AFTER_INVENTORY.read_bytes()
    baseline_raw = BASELINE_INVENTORY.read_bytes()
    receipt = json.loads(receipt_raw)
    after = json.loads(after_raw)
    baseline = json.loads(baseline_raw)
    schema = load(SCHEMA)
    validate_schema_node(receipt, schema, schema, "m02")
    timestamp(receipt["captured_at_utc"], "m02.captured_at_utc")
    reject_sensitive_values(receipt, "m02")
    validate_recorded_inventory(after)

    inventory = receipt["inventory"]
    for label, expected_path, expected_raw, expected in (
        ("baseline", str(BASELINE_INVENTORY.relative_to(ROOT)), baseline_raw, baseline),
        ("after", str(AFTER_INVENTORY.relative_to(ROOT)), after_raw, after),
    ):
        reference = inventory[label]
        if reference["path"] != expected_path:
            fail(f"inventory.{label}.path mismatch")
        if reference["sha256"] != hashlib.sha256(expected_raw).hexdigest():
            fail(f"inventory.{label}.sha256 mismatch")
        if reference["summary"] != expected["summary"]:
            fail(f"inventory.{label}.summary mismatch")

    delta = after["summary"]["by_category"]["public_production"] - baseline["summary"]["by_category"]["public_production"]
    if inventory["public_production_line_delta"] != delta:
        fail("inventory public-production delta mismatch")
    if receipt["deleted_public_production_lines"] != sum(item["removed_lines"] for item in receipt["candidates"]):
        fail("candidate removed_lines do not sum to receipt total")
    verification_statuses = {item["status"] for item in receipt["verification"]}
    if "FAIL" in verification_statuses and receipt["status"] != "FAIL":
        fail("receipt status must be FAIL while a verification failed")
    if receipt["status"] == "PASS" and verification_statuses != {"PASS"}:
        fail("receipt cannot PASS with incomplete verification")

    base = receipt["base_commit"]
    rollback = receipt["rollback"]
    restore = sorted(rollback["restore_from_base"])
    remove = sorted(rollback["remove_added"])
    if any(not git_path_exists(base, path) for path in restore):
        fail("rollback restore_from_base contains path absent from base")
    if any(git_path_exists(base, path) for path in remove):
        fail("rollback remove_added contains path present at base")
    if is_base_checkout(base) and sorted(restore + remove) != indexed_changes(base):
        fail("rollback manifest does not exactly cover indexed M02 changes")
    for candidate in receipt["candidates"]:
        if candidate["rollback_group_id"] != rollback["group_id"]:
            fail("candidate rollback group mismatch")
        for path in candidate["removed_files"]:
            if Path(path).exists() or not git_path_exists(base, path):
                fail(f"removed file reachability mismatch: {path}")


def main() -> int:
    try:
        validate()
    except (OSError, json.JSONDecodeError, ValueError, KeyError, UnicodeDecodeError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print("PASS: M02 receipt, inventory delta, and rollback manifest are closed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
