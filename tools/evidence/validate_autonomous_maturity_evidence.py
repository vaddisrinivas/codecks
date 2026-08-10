#!/usr/bin/env python3
"""Fail-closed validator for autonomous-maturity baseline and source inventory."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

from generate_autonomous_maturity_source_inventory import METHOD, classify, owner


ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "tasks/test-evidence/autonomous-maturity-m00-baseline.json"
INVENTORY = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-baseline-v1.schema.json"
INVENTORY_SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-source-inventory-v1.schema.json"
HEX_64 = re.compile(r"^[0-9a-f]{64}$")
RFC3339 = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$")
SAFE_PATH = re.compile(r"^(?!/)(?!.*(?:^|/)\.\.(?:/|$))[A-Za-z0-9_.+@/-]+$")
PRIVATE_PATH = re.compile(
    "(?:/" + "Users/|/" + "home/|[A-Za-z]:\\\\" + "Users\\\\)"
)
CHECK_KEYS = {
    "id", "status", "command", "started_at_utc", "finished_at_utc",
    "exit_code", "tool", "environment", "output_sha256",
}


def fail(message: str) -> None:
    raise ValueError(message)


def timestamp(value: object, field: str) -> datetime:
    if not isinstance(value, str) or not RFC3339.fullmatch(value):
        fail(f"{field}: expected timestamp string")
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
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
        if len(value) < rule.get("minLength", 0):
            fail(f"{field}: string is too short")
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
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in rule and value < rule["minimum"]:
            fail(f"{field}: value below minimum")
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
    schema = load(INVENTORY_SCHEMA)
    validate_schema_node(data, schema, schema, "inventory")
    if data.get("schema") != "codecks.autonomous-maturity.source-inventory.v1":
        fail("inventory: unsupported schema")
    if data["method"] != METHOD:
        fail("inventory.method: does not match generator method")
    files = data.get("files")
    if not isinstance(files, list):
        fail("inventory.files: expected list")
    paths = [entry["path"] for entry in files]
    expected = tracked_sources()
    if paths != expected:
        fail("inventory.files: does not exactly match sorted tracked source files")
    category_lines: Counter[str] = Counter()
    source_set_lines: Counter[str] = Counter()
    language_lines: Counter[str] = Counter()
    for entry in files:
        path = entry["path"]
        if not SAFE_PATH.fullmatch(path) or Path(path).is_absolute():
            fail(f"inventory unsafe path: {path}")
        raw = (ROOT / path).read_bytes()
        digest = hashlib.sha256(raw).hexdigest()
        lines = len(raw.decode("utf-8").splitlines())
        expected_category, expected_source_set = classify(path)
        expected_language = {
            ".kt": "Kotlin", ".java": "Java", ".swift": "Swift"
        }[Path(path).suffix]
        if entry["sha256"] != digest:
            fail(f"inventory sha256 mismatch: {path}")
        if entry["physical_lines"] != lines:
            fail(f"inventory line mismatch: {path}")
        if entry["category"] != expected_category:
            fail(f"inventory category mismatch: {path}")
        if entry["source_set"] != expected_source_set:
            fail(f"inventory source_set mismatch: {path}")
        if entry["language"] != expected_language:
            fail(f"inventory language mismatch: {path}")
        if entry["feature_owner"] != owner(path):
            fail(f"inventory feature_owner mismatch: {path}")
        category_lines[expected_category] += lines
        source_set_lines[expected_source_set] += lines
        language_lines[expected_language] += lines
    summary = data.get("summary", {})
    expected_summary = {
        "file_count": len(files),
        "physical_lines": sum(item["physical_lines"] for item in files),
        "by_category": dict(sorted(category_lines.items())),
        "by_source_set": dict(sorted(source_set_lines.items())),
        "by_language": dict(sorted(language_lines.items())),
    }
    if summary != expected_summary:
        fail("inventory summary mismatch")


def validate_baseline(data: dict, inventory: dict, inventory_raw: bytes) -> None:
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

    check_ids: set[str] = set()
    for index, check in enumerate(data.get("fresh_checks", [])):
        if set(check) != CHECK_KEYS:
            fail(f"fresh_checks[{index}]: unknown or missing field")
        if check["status"] not in {"PASS", "FAIL"}:
            fail(f"fresh_checks[{index}]: invalid executed status")
        if check["id"] in check_ids:
            fail(f"fresh_checks[{index}]: duplicate id {check['id']}")
        check_ids.add(check["id"])
        started = timestamp(check["started_at_utc"], f"fresh_checks[{index}].started_at_utc")
        finished = timestamp(check["finished_at_utc"], f"fresh_checks[{index}].finished_at_utc")
        if finished < started:
            fail(f"fresh_checks[{index}]: finished before started")
        if not isinstance(check["exit_code"], int):
            fail(f"fresh_checks[{index}]: exit_code must be integer")
        if check["status"] != ("PASS" if check["exit_code"] == 0 else "FAIL"):
            fail(f"fresh_checks[{index}]: status/exit_code mismatch")
        if not HEX_64.fullmatch(check["output_sha256"]):
            fail(f"fresh_checks[{index}]: invalid output_sha256")
        if PRIVATE_PATH.search(check["command"]):
            fail(f"fresh_checks[{index}]: private path in command")
    if any(check["status"] != "PASS" for check in data["fresh_checks"]):
        fail("receipt_validation.status cannot be PASS while a fresh check is FAIL")

    release = data["release_artifact"]
    android = data["android"]
    for field in ("version_code", "version_name", "min_sdk", "target_sdk", "compile_sdk"):
        if release[field] != android[field]:
            fail(f"release_artifact.{field} does not match android.{field}")
    if release["package"] != android["base_application_id"]:
        fail("release_artifact.package does not match android.base_application_id")
    if release["signature_verification"] == "PASS" and not release["signature_scheme_v2"]:
        fail("release signature PASS requires signature_scheme_v2")
    if android["release_minification"] or android["release_resource_shrinking"]:
        fail("release shrinking must remain disabled")

    for index, lane in enumerate(data.get("not_run", [])):
        if set(lane) != {"lane", "status", "reason"} or lane.get("status") != "NOT_RUN":
            fail(f"not_run[{index}]: invalid closed shape")

    serialized = json.dumps(data, sort_keys=True)
    if PRIVATE_PATH.search(serialized):
        fail("baseline contains a private absolute path")
    source_inventory = data.get("source_inventory", {})
    if source_inventory.get("path") != "tasks/test-evidence/autonomous-maturity-source-inventory.json":
        fail("source_inventory.path mismatch")
    if source_inventory.get("sha256") != hashlib.sha256(inventory_raw).hexdigest():
        fail("source_inventory.sha256 mismatch")
    if source_inventory.get("summary") != inventory.get("summary"):
        fail("source_inventory.summary mismatch")


def validate_evidence(baseline: dict, inventory: dict, inventory_raw: bytes) -> None:
    validate_inventory(inventory)
    validate_baseline(baseline, inventory, inventory_raw)


def main() -> int:
    try:
        inventory = load(INVENTORY)
        baseline = load(BASELINE)
        if not isinstance(inventory, dict) or not isinstance(baseline, dict):
            fail("root JSON values must be objects")
        validate_evidence(baseline, inventory, INVENTORY.read_bytes())
    except (OSError, json.JSONDecodeError, ValueError, KeyError, UnicodeDecodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print("PASS: autonomous maturity evidence is structurally valid and current")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
