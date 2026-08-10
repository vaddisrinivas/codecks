#!/usr/bin/env python3
"""Fail-closed validator for autonomous-maturity baseline and source inventory."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "tasks/test-evidence/autonomous-maturity-m00-baseline.json"
INVENTORY = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-baseline-v1.schema.json"
HEX_64 = re.compile(r"^[0-9a-f]{64}$")
PRIVATE_PATH = re.compile(
    "(?:/" + "Users/|/" + "home/|[A-Za-z]:\\\\" + "Users\\\\)"
)
CHECK_KEYS = {
    "id", "status", "command", "started_at_utc", "finished_at_utc",
    "exit_code", "tool", "environment", "output_sha256",
}


def fail(message: str) -> None:
    raise ValueError(message)


def timestamp(value: object, field: str) -> None:
    if not isinstance(value, str):
        fail(f"{field}: expected timestamp string")
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError(f"{field}: invalid RFC3339 timestamp") from exc


def load(path: Path) -> object:
    return json.loads(path.read_text())


def schema_type_matches(value: object, expected: str) -> bool:
    return {
        "object": lambda: isinstance(value, dict),
        "array": lambda: isinstance(value, list),
        "string": lambda: isinstance(value, str),
        "integer": lambda: isinstance(value, int) and not isinstance(value, bool),
        "number": lambda: isinstance(value, (int, float)) and not isinstance(value, bool),
        "boolean": lambda: isinstance(value, bool),
        "null": lambda: value is None,
    }[expected]()


def validate_schema_node(value: object, rule: dict, root_schema: dict, field: str) -> None:
    if "$ref" in rule:
        reference = rule["$ref"]
        if not reference.startswith("#/"):
            fail(f"{field}: only local schema references are supported")
        target: object = root_schema
        for token in reference[2:].split("/"):
            target = target[token]
        validate_schema_node(value, target, root_schema, field)
        return
    expected = rule.get("type")
    if expected:
        choices = expected if isinstance(expected, list) else [expected]
        if not any(schema_type_matches(value, choice) for choice in choices):
            fail(f"{field}: expected schema type {choices}")
    if "const" in rule and value != rule["const"]:
        fail(f"{field}: expected constant {rule['const']!r}")
    if "enum" in rule and value not in rule["enum"]:
        fail(f"{field}: value outside enum")
    if isinstance(value, str):
        if "pattern" in rule and not re.fullmatch(rule["pattern"], value):
            fail(f"{field}: string does not match pattern")
        if rule.get("format") == "date-time":
            timestamp(value, field)
    if isinstance(value, list):
        if len(value) < rule.get("minItems", 0):
            fail(f"{field}: too few items")
        if "items" in rule:
            for index, item in enumerate(value):
                validate_schema_node(item, rule["items"], root_schema, f"{field}[{index}]")
    if isinstance(value, dict):
        required = set(rule.get("required", []))
        missing = required - set(value)
        if missing:
            fail(f"{field}: missing keys {sorted(missing)}")
        properties = rule.get("properties", {})
        additional = rule.get("additionalProperties", True)
        for key, item in value.items():
            if key in properties:
                validate_schema_node(item, properties[key], root_schema, f"{field}.{key}")
            elif additional is False:
                fail(f"{field}: unknown key {key}")
            elif isinstance(additional, dict):
                validate_schema_node(item, additional, root_schema, f"{field}.{key}")


def tracked_sources() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "*.kt", "*.java", "*.swift"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return sorted(result.stdout.splitlines())


def validate_inventory(data: dict) -> None:
    if data.get("schema") != "codecks.autonomous-maturity.source-inventory.v1":
        fail("inventory: unsupported schema")
    files = data.get("files")
    if not isinstance(files, list):
        fail("inventory.files: expected list")
    paths = [entry.get("path") for entry in files]
    expected = tracked_sources()
    if paths != expected:
        fail("inventory.files: does not exactly match sorted tracked source files")
    for entry in files:
        path = entry["path"]
        raw = (ROOT / path).read_bytes()
        if entry.get("sha256") != hashlib.sha256(raw).hexdigest():
            fail(f"inventory sha256 mismatch: {path}")
        if entry.get("physical_lines") != len(raw.decode("utf-8").splitlines()):
            fail(f"inventory line mismatch: {path}")
        for key in ("category", "source_set", "feature_owner", "language"):
            if not isinstance(entry.get(key), str) or not entry[key]:
                fail(f"inventory missing {key}: {path}")
    summary = data.get("summary", {})
    if summary.get("file_count") != len(files):
        fail("inventory summary file_count mismatch")
    if summary.get("physical_lines") != sum(item["physical_lines"] for item in files):
        fail("inventory summary physical_lines mismatch")


def validate_baseline(data: dict, inventory: dict) -> None:
    schema = load(SCHEMA)
    validate_schema_node(data, schema, schema, "baseline")
    if data.get("schema") != "codecks.autonomous-maturity.baseline.v1":
        fail("baseline: unsupported schema")
    timestamp(data.get("captured_at_utc"), "captured_at_utc")

    validation = data.get("receipt_validation", {})
    if set(validation) != {"status", "validator", "scope"}:
        fail("receipt_validation: unknown or missing field")
    if validation.get("status") != "PASS":
        fail("receipt_validation.status must be PASS for committed receipt")
    if validation.get("validator") != "tools/evidence/validate_autonomous_maturity_evidence.py":
        fail("receipt_validation.validator mismatch")

    maturity = data.get("maturity_assessment", {})
    if set(maturity) != {"status", "completed_milestones", "note"}:
        fail("maturity_assessment: unknown or missing field")
    if maturity.get("status") not in {"NOT_RUN", "AUTONOMOUS_PROXY", "EXTERNAL_EVIDENCE_REMAINS"}:
        fail("maturity_assessment cannot claim PASS")

    for index, check in enumerate(data.get("fresh_checks", [])):
        if set(check) != CHECK_KEYS:
            fail(f"fresh_checks[{index}]: unknown or missing field")
        if check["status"] not in {"PASS", "FAIL"}:
            fail(f"fresh_checks[{index}]: invalid executed status")
        timestamp(check["started_at_utc"], f"fresh_checks[{index}].started_at_utc")
        timestamp(check["finished_at_utc"], f"fresh_checks[{index}].finished_at_utc")
        if not isinstance(check["exit_code"], int):
            fail(f"fresh_checks[{index}]: exit_code must be integer")
        if check["status"] != ("PASS" if check["exit_code"] == 0 else "FAIL"):
            fail(f"fresh_checks[{index}]: status/exit_code mismatch")
        if not HEX_64.fullmatch(check["output_sha256"]):
            fail(f"fresh_checks[{index}]: invalid output_sha256")
        if PRIVATE_PATH.search(check["command"]):
            fail(f"fresh_checks[{index}]: private path in command")

    for index, lane in enumerate(data.get("not_run", [])):
        if set(lane) != {"lane", "status", "reason"} or lane.get("status") != "NOT_RUN":
            fail(f"not_run[{index}]: invalid closed shape")

    serialized = json.dumps(data, sort_keys=True)
    if PRIVATE_PATH.search(serialized):
        fail("baseline contains a private absolute path")
    source_inventory = data.get("source_inventory", {})
    inventory_raw = INVENTORY.read_bytes()
    if source_inventory.get("path") != "tasks/test-evidence/autonomous-maturity-source-inventory.json":
        fail("source_inventory.path mismatch")
    if source_inventory.get("sha256") != hashlib.sha256(inventory_raw).hexdigest():
        fail("source_inventory.sha256 mismatch")
    if source_inventory.get("summary") != inventory.get("summary"):
        fail("source_inventory.summary mismatch")


def main() -> int:
    try:
        inventory = load(INVENTORY)
        baseline = load(BASELINE)
        if not isinstance(inventory, dict) or not isinstance(baseline, dict):
            fail("root JSON values must be objects")
        validate_inventory(inventory)
        validate_baseline(baseline, inventory)
    except (OSError, json.JSONDecodeError, ValueError, KeyError, UnicodeDecodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print("PASS: autonomous maturity evidence is structurally valid and current")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
