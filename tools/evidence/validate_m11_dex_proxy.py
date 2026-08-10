#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from datetime import datetime
from pathlib import Path

from collect_m11_dex_proxy import CHECKS, EXPECTED, ROOT, parse_result, safe_path
from validate_autonomous_maturity_evidence import validate_schema_node

RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m11-dex-proxy.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m11-dex-proxy-v1.schema.json"


def validate(path: Path = RECEIPT) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_schema_node(data, schema, schema, "m11")
    if set(data) != {"schema", "milestone", "status", "scope", "artifacts", "profiles", "external", "limitations"}:
        raise ValueError("receipt keys are not closed")
    if (data["schema"], data["milestone"], data["status"], data["scope"]) != (
        "codecks.autonomous-maturity.m11-dex-proxy.v1", "M11", "PASS", "EMULATOR_PROXY_ONLY"
    ):
        raise ValueError("receipt identity/status mismatch")
    artifacts = data["artifacts"]
    if not 1 <= len(artifacts) <= 2:
        raise ValueError("receipt must use one full result or two disjoint rerun results")
    observed: set[str] = set()
    total = 0
    previous_timestamp: datetime | None = None
    for artifact in artifacts:
        if set(artifact) != {"path", "sha256", "timestamp", "passedMethods", "failedMethods", "tests", "failures", "errors", "skipped"}:
            raise ValueError("artifact keys are not closed")
        if len(artifact["passedMethods"]) != len(set(artifact["passedMethods"])) or len(artifact["failedMethods"]) != len(set(artifact["failedMethods"])):
            raise ValueError("artifact method lists contain duplicates")
        passed = set(artifact["passedMethods"])
        failed = set(artifact["failedMethods"])
        methods = passed | failed
        if not methods or not methods <= EXPECTED or passed & failed or observed & passed:
            raise ValueError("artifact methods are empty, unexpected, overlapping, or duplicated")
        if observed & failed:
            raise ValueError("artifact records a failure after an earlier PASS")
        raw = safe_path(artifact["path"]).read_bytes()
        if hashlib.sha256(raw).hexdigest() != artifact["sha256"]:
            raise ValueError("runtime artifact digest mismatch")
        parsed = parse_result(raw)
        parsed_counts = tuple(parsed[key] for key in ("tests", "failures", "errors", "skipped"))
        if (passed, failed, artifact["timestamp"]) != (parsed["passed"], parsed["failed"], parsed["timestamp"]) or parsed_counts != (
            artifact["tests"], artifact["failures"], artifact["errors"], artifact["skipped"]
        ):
            raise ValueError("artifact claims do not match parsed runtime result")
        current_timestamp = datetime.fromisoformat(artifact["timestamp"].replace("Z", "+00:00"))
        if previous_timestamp is not None and current_timestamp <= previous_timestamp:
            raise ValueError("artifacts are not in strict chronological order")
        previous_timestamp = current_timestamp
        observed |= passed
        total += len(passed)
    if observed != EXPECTED or total != 6:
        raise ValueError("receipt does not prove exact disjoint 6/6")
    if len(artifacts) == 1:
        if artifacts[0]["failedMethods"]:
            raise ValueError("single artifact must be a clean 6/6 run")
    else:
        initial, corrected = artifacts
        if not initial["failedMethods"] or corrected["failedMethods"] or set(corrected["passedMethods"]) != set(initial["failedMethods"]):
            raise ValueError("corrected rerun must exactly follow and remedy the initial failures")
    profiles = data["profiles"]
    if len(profiles) != 2 or {item["resolution"] for item in profiles} != {"1280x720", "1920x1080"}:
        raise ValueError("required resolutions missing")
    if any(
        item["status"] != "PASS" or set(item["checks"]) != set(CHECKS)
        or len(item["checks"]) != len(CHECKS) or len(item["checks"]) != len(set(item["checks"]))
        for item in profiles
    ):
        raise ValueError("profile checks incomplete")
    external = data["external"]
    if len(external) != 1 or external[0]["id"] != "samsung_dex_physical" or external[0]["status"] != "NOT_RUN":
        raise ValueError("Samsung DeX physical boundary must remain NOT_RUN")
    if len(data["limitations"]) < 3 or len(data["limitations"]) != len(set(data["limitations"])):
        raise ValueError("proxy limitations missing")


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print("PASS: M11 emulator proxy receipt is closed; Samsung DeX physical remains NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
