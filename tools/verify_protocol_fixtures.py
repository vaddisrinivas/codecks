#!/usr/bin/env python3
"""Validate Reactive protocol fixtures without external dependencies."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "protocol" / "fixtures"
SCHEMAS = ROOT / "protocol" / "schemas"
TOKEN_MAX = 128
MAX_BODY_BYTES = 64 * 1024
MAX_TRANSFER_BYTES = 50 * 1024 * 1024
SHA256 = re.compile(r"^[a-fA-F0-9]{64}$")


class FixtureError(Exception):
    pass


def token(name: str, value: object) -> None:
    if not isinstance(value, str) or not value.strip():
        raise FixtureError(f"{name} blank")
    if len(value.encode("utf-8")) > TOKEN_MAX:
        raise FixtureError(f"{name} too large")


def require_keys(path: Path, data: dict, keys: list[str]) -> None:
    missing = [key for key in keys if key not in data]
    if missing:
        raise FixtureError(f"{path.name} missing {', '.join(missing)}")


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise FixtureError(f"{path.name} must be object")
    return data


def check_schema_files() -> None:
    for path in sorted(SCHEMAS.glob("*.json")):
        data = load_json(path)
        require_keys(path, data, ["$schema", "$id", "title"])
        if "type" not in data and "oneOf" not in data:
            raise FixtureError(f"{path.name} missing type or oneOf")


def check_common_reactive(path: Path, data: dict) -> None:
    if path.name.startswith("reactive-") or path.parent.name == "hostile":
        if data.get("schema") != "reactive.v1":
            raise FixtureError(f"{path.name} invalid protocol schema")


def check_helper_identity(data: dict) -> None:
    identity = data.get("helperIdentity")
    if identity is None:
        return
    if not isinstance(identity, dict):
        raise FixtureError("helperIdentity must be object")
    require_keys(Path("helperIdentity"), identity, ["helperId", "publicKeyFingerprint", "issuedAtMillis", "trustState"])
    token("helperId", identity["helperId"])
    if not isinstance(identity["publicKeyFingerprint"], str) or not 32 <= len(identity["publicKeyFingerprint"]) <= 128:
        raise FixtureError("publicKeyFingerprint length invalid")
    if identity["trustState"] == "revoked":
        raise FixtureError("helper identity revoked")


def check_request(data: dict) -> None:
    require_keys(Path("request"), data, ["sessionId", "sequence", "requestId", "deadlineMillis", "bodyJson", "authTag"])
    token("sessionId", data["sessionId"])
    token("requestId", data["requestId"])
    if not isinstance(data["sequence"], int) or data["sequence"] <= 0:
        raise FixtureError("sequence invalid")
    if not isinstance(data["deadlineMillis"], int) or data["deadlineMillis"] <= 1:
        raise FixtureError("deadline expired")
    body = data["bodyJson"]
    if not isinstance(body, str) or not body.strip() or len(body.encode("utf-8")) > MAX_BODY_BYTES:
        raise FixtureError("body invalid")
    token("authTag", data["authTag"])


def check_capabilities(data: dict) -> None:
    require_keys(Path("capabilities"), data, ["helperId", "protocolMajor", "values", "providers"])
    token("helperId", data["helperId"])
    if data["protocolMajor"] != 1:
        raise FixtureError("protocolMajor invalid")
    for provider in data["providers"]:
        token("providerId", provider.get("providerId"))
        confidence = provider.get("confidenceFloor")
        if not isinstance(confidence, (int, float)) or not 0 <= confidence <= 1:
            raise FixtureError("confidenceFloor invalid")


def check_state_snapshot(data: dict) -> None:
    require_keys(Path("state"), data, ["macId", "snapshotRevision", "capturedAtMillis", "freshnessMillis", "provenance"])
    token("macId", data["macId"])
    if data["snapshotRevision"] < 0 or data["freshnessMillis"] < 0:
        raise FixtureError("state revision/freshness invalid")
    for display in data.get("displays", []):
        brightness = display.get("brightnessPercent")
        if brightness is not None and not 0 <= brightness <= 100:
            raise FixtureError("brightness invalid")


def check_state_snapshot_at(data: dict, now: int) -> None:
    check_state_snapshot(data)
    captured = data["capturedAtMillis"]
    freshness = data["freshnessMillis"]
    if now - captured > freshness and not data.get("stale", False):
        raise FixtureError("stale state unmarked")


def check_state_delta(data: dict) -> None:
    require_keys(Path("delta"), data, ["macId", "baseRevision", "newRevision", "changes"])
    if data["newRevision"] <= data["baseRevision"]:
        raise FixtureError("delta revision must advance")
    if not data["changes"]:
        raise FixtureError("delta empty")
    for change in data["changes"]:
        path = change.get("path")
        if not isinstance(path, str) or not path.startswith("/"):
            raise FixtureError("delta path invalid")


def check_provider_candidate(data: dict) -> None:
    require_keys(Path("candidate"), data, ["candidateId", "providerId", "actionId", "confidence", "explanation", "policy", "conflicts"])
    if not 0 <= data["confidence"] <= 1:
        raise FixtureError("confidence invalid")
    if data["conflicts"] and data["policy"] == "allow":
        raise FixtureError("conflict cannot allow")


def check_execute(data: dict) -> None:
    require_keys(
        Path("execute"),
        data,
        [
            "type",
            "actionId",
            "actionRevision",
            "operationId",
            "idempotencyKey",
            "timeoutMillis",
            "cancellationToken",
            "arguments",
        ],
    )
    if data["type"] != "execute":
        raise FixtureError("execute type invalid")
    token("operationId", data["operationId"])
    token("idempotencyKey", data["idempotencyKey"])
    timeout = data["timeoutMillis"]
    if not isinstance(timeout, int) or not 1 <= timeout <= 5 * 60 * 1000:
        raise FixtureError("execute timeout invalid")
    token("cancellationToken", data["cancellationToken"])


def check_receipt(data: dict) -> None:
    require_keys(
        Path("receipt"),
        data,
        ["receiptId", "operationId", "idempotencyKey", "actionId", "actionRevision", "status", "startedAtMillis", "completedAtMillis"],
    )
    token("receiptId", data["receiptId"])
    token("operationId", data["operationId"])
    token("idempotencyKey", data["idempotencyKey"])
    if data["completedAtMillis"] < data["startedAtMillis"]:
        raise FixtureError("receipt completed before started")
    failures = data.get("partialFailures", [])
    status = data["status"]
    if status == "partial_failure" and not failures:
        raise FixtureError("partial receipt missing errors")
    if status == "timeout" and not any(item.get("code") == "timeout" for item in failures):
        raise FixtureError("timeout receipt missing timeout error")
    if status == "cancelled" and not any(item.get("code") == "cancelled" for item in failures):
        raise FixtureError("cancelled receipt missing cancelled error")


def check_undo(data: dict) -> None:
    require_keys(Path("undo"), data, ["undoToken", "status", "completedAtMillis"])
    token("undoToken", data["undoToken"])
    if data["status"] == "failed" and not data.get("message"):
        raise FixtureError("failed undo missing message")


def check_transfer(data: dict) -> None:
    require_keys(Path("transfer"), data, ["transferId", "fileName", "byteCount", "sha256", "sourcePath", "destinationHint"])
    token("transferId", data["transferId"])
    if "/" in data["fileName"] or ".." in data["fileName"]:
        raise FixtureError("transfer fileName invalid")
    if not isinstance(data["byteCount"], int) or not 1 <= data["byteCount"] <= MAX_TRANSFER_BYTES:
        raise FixtureError("transfer byteCount invalid")
    if not isinstance(data["sha256"], str) or not SHA256.match(data["sha256"]):
        raise FixtureError("transfer sha256 invalid")
    if not isinstance(data["sourcePath"], str) or not data["sourcePath"].startswith("/") or ".." in data["sourcePath"]:
        raise FixtureError("transfer sourcePath invalid")


VALID_CHECKS = {
    "reactive-challenge.json": check_helper_identity,
    "reactive-request.json": check_request,
    "reactive-capabilities.json": check_capabilities,
    "reactive-state-snapshot.json": check_state_snapshot,
    "reactive-state-delta.json": check_state_delta,
    "reactive-provider-candidate.json": check_provider_candidate,
    "reactive-execute.json": check_execute,
    "reactive-partial-receipt.json": check_receipt,
    "reactive-timeout-receipt.json": check_receipt,
    "reactive-cancelled-receipt.json": check_receipt,
    "reactive-undo-result.json": check_undo,
    "reactive-transfer-metadata.json": check_transfer,
}


def check_valid_fixture(path: Path) -> None:
    data = load_json(path)
    check_common_reactive(path, data)
    check = VALID_CHECKS.get(path.name)
    if check:
        check(data)


def check_hostile_fixture(path: Path) -> None:
    data = load_json(path)
    if "expectedError" not in data:
        raise FixtureError(f"{path.name} missing expectedError")
    expected = str(data["expectedError"])
    failure = ""
    try:
        if expected == "protocol":
            check_common_reactive(path, data)
        elif expected == "auth":
            check_request(data)
            if data.get("authTag") != "fixture-auth-tag":
                raise FixtureError("auth tag mismatch")
        elif expected == "replay":
            seen: set[tuple[int, str]] = set()
            for item in data.get("accepted", []):
                candidate = dict(item)
                candidate["schema"] = data.get("schema")
                candidate["sessionId"] = data.get("sessionId")
                check_request(candidate)
                sequence = item.get("sequence")
                request_id = item.get("requestId")
                key = (sequence, request_id)
                if key in seen:
                    raise FixtureError("replay duplicate accepted request")
                seen.add(key)
        elif "sequence" in data:
            check_request(data)
        elif "helperIdentity" in data:
            check_helper_identity(data)
        elif "transferId" in data:
            check_transfer(data)
        elif "candidateId" in data:
            check_provider_candidate(data)
        elif "snapshotRevision" in data:
            check_state_snapshot_at(data, now=100)
        elif "type" in data and data["type"] == "execute":
            check_execute(data)
        elif "receiptId" in data:
            check_receipt(data)
        elif "undoToken" in data:
            check_undo(data)
        else:
            raise FixtureError("unknown hostile shape")
    except FixtureError as exc:
        failure = str(exc)
    if not failure:
        raise FixtureError(f"{path.name} unexpectedly valid")
    if expected not in failure:
        raise FixtureError(f"{path.name} expected {expected!r}, got {failure!r}")


def main() -> int:
    errors: list[str] = []
    try:
        check_schema_files()
    except FixtureError as exc:
        errors.append(str(exc))
    for path in sorted(FIXTURES.glob("*.json")):
        try:
            check_valid_fixture(path)
        except (FixtureError, json.JSONDecodeError) as exc:
            errors.append(f"{path.relative_to(ROOT)}: {exc}")
    for path in sorted((FIXTURES / "hostile").glob("*.json")):
        try:
            check_hostile_fixture(path)
        except (FixtureError, json.JSONDecodeError) as exc:
            errors.append(f"{path.relative_to(ROOT)}: {exc}")
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print("protocol fixtures ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
