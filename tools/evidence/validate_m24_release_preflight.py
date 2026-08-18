#!/usr/bin/env python3
from __future__ import annotations

import json
import hashlib
from datetime import datetime, timezone
from pathlib import Path
import re
import subprocess

from collect_m24_release_preflight import (
    GATE_IDS, M20_INTEGRATION_COMMIT, M21_INTEGRATION_COMMIT, OPTIONAL_ABSENT_SOURCE,
    ROOT, SCHEMA_ID, SIGNING_NAMES,
    SOURCE_PATHS, adb_classification, git_bytes, implementation_provenance, safe_path,
    sha256, signing_presence,
)
from validate_autonomous_maturity_evidence import validate_schema_node

RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m24-preflight.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m24-preflight-v2.schema.json"
EXPECTED_ROOT_KEYS = {
    "schema", "milestone", "evidenceLevel", "verdict", "generatedAtUtc", "sourceCommit", "provenance",
    "sources", "adbClassification", "phone", "publishedBaseline", "m16", "m20Closure",
    "m21Closure", "m23", "signing", "candidate", "dependencies", "gates",
    "safeInPlaceUpdate", "privacy", "blockers", "limitations",
}
EXPECTED_GATES = [
    (GATE_IDS[0], "PASS", "exact_head_and_sources_bound"),
    (GATE_IDS[1], "PASS", "raw_adb_server_devices_l_only"),
    (GATE_IDS[2], "NOT_RUN", "physical_phone_count_zero"),
    (GATE_IDS[3], "NOT_RUN", "m16_runtime_receipt_absent"),
    (GATE_IDS[4], "NOT_RUN", "m16_168h_not_started"),
    (GATE_IDS[5], "PASS", "m21_local_complete"),
    (GATE_IDS[6], "NOT_RUN", "m23_not_a_candidate"),
    (GATE_IDS[7], "NOT_RUN", "candidate_absent"),
    (GATE_IDS[8], "PASS", "canonical_signing_inputs_unset"),
    (GATE_IDS[9], "NOT_RUN", "explicit_execution_not_authorized"),
]
EXPECTED_BLOCKERS = [
    "PHYSICAL_PHONE_NOT_CONNECTED", "M16_CAPACITY_NOT_RUN", "M16_168H_NOT_RUN",
    "M23_NOT_A_CANDIDATE", "CANDIDATE_NOT_BUILT", "PRODUCTION_SIGNING_INPUTS_UNSET",
]
EXPECTED_LIMITATIONS = [
    "Read-only ADB classification used one raw host:devices-l server request; no adb CLI, package query, pull, install, or instrumentation ran.",
    "M21 local distribution/support preparation is complete; physical update and external publication remain outside this preflight.",
    "M16 capacity execution and 168-hour evidence remain NOT_RUN; planned source capacity is not runtime proof.",
]
PROHIBITED_KEYS = re.compile(r"(?:^rawSerial$|password|secret|privateKeyValue|credentialValue|authToken)", re.I)
PROHIBITED_VALUES = (
    re.compile(r"-----BEGIN [^-\r\n]*PRIVATE KEY-----", re.I),
    re.compile(r"\b(?:password|credential|secret|api[_-]?key|private[_-]?key)\s*[:=]\s*[^\s,;]+", re.I),
    re.compile(r"M24_SECRET_CANARY", re.I),
    re.compile(r"(?:^|\s)(?:adb|pm)\s+(?:uninstall|clear)(?:\s|$)", re.I),
    re.compile(r"(?:^|\s)adb\s+install\b", re.I),
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_closure(value: dict, expected_status: str, integration_commit: str, implementation_commit: str) -> None:
    require(set(value) == {
        "status", "integrationCommit", "receiptPath", "receiptSha256", "receiptSchema",
        "collectorPath", "collectorSha256", "validatorPath", "validatorSha256",
    }, "closure keys changed")
    require(value["status"] == expected_status and value["integrationCommit"] == integration_commit,
            "closure status/integration changed")
    for path_key, sha_key in (
        ("receiptPath", "receiptSha256"), ("collectorPath", "collectorSha256"),
        ("validatorPath", "validatorSha256"),
    ):
        require(sha256(safe_path(value[path_key])) == value[sha_key], f"closure binding changed: {path_key}")
    require(subprocess.run(
        ["git", "merge-base", "--is-ancestor", integration_commit, implementation_commit],
        cwd=ROOT, capture_output=True,
    ).returncode == 0, "closure integration is not current-source ancestor")


def validate_receipt_commit(implementation_commit: str, root: Path = ROOT, receipt: Path = RECEIPT) -> None:
    receipt_relative = str(receipt.relative_to(root))
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, check=True, capture_output=True, text=True).stdout.strip()
    parent = subprocess.run(["git", "rev-parse", "HEAD^"], cwd=root, check=True, capture_output=True, text=True).stdout.strip()
    require(parent == implementation_commit, "M24 receipt commit parent is not the implementation commit")
    changed = subprocess.run(
        ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"],
        cwd=root, check=True, capture_output=True, text=True,
    ).stdout.splitlines()
    require(changed == [receipt_relative], "M24 receipt commit is not receipt-only")
    blob = subprocess.run(
        ["git", "show", f"{head}:{receipt_relative}"], cwd=root, check=True, capture_output=True,
    ).stdout
    require(blob == receipt.read_bytes(), "M24 receipt bytes differ from exact HEAD blob")
    require(not subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=all"], cwd=root,
        check=True, capture_output=True, text=True,
    ).stdout, "M24 final worktree has uncommitted drift")


def validate(path: Path = RECEIPT, live_read_only: bool = False, require_receipt_commit: bool = True) -> None:
    data = json.loads(path.read_text())
    schema = json.loads(SCHEMA.read_text())
    validate_schema_node(data, schema, schema, "m24-current")
    require(set(data) == EXPECTED_ROOT_KEYS, "M24 receipt keys are not closed")
    require((data["schema"], data["milestone"], data["evidenceLevel"], data["verdict"]) ==
            (SCHEMA_ID, "M24", "PREFLIGHT_ONLY", "NO_GO"), "M24 identity/verdict changed")
    implementation_commit = data["sourceCommit"]
    require(re.fullmatch(r"[0-9a-f]{40}", implementation_commit) is not None,
            "M24 sourceCommit is not an exact commit")
    generated_at = datetime.strptime(data["generatedAtUtc"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    now = datetime.now(timezone.utc)
    require(generated_at <= now, "M24 generatedAtUtc is in the future")
    require((now - generated_at).total_seconds() <= 900, "M24 generatedAtUtc is older than 15 minutes")
    require(data["provenance"] == implementation_provenance(implementation_commit),
            "M24 implementation provenance changed")
    if require_receipt_commit:
        validate_receipt_commit(implementation_commit)

    require([item["path"] for item in data["sources"]] == list(SOURCE_PATHS), "M24 source path order changed")
    for item in data["sources"]:
        require(set(item) == {"path", "status", "sha256"}, "M24 source keys changed")
        if item["path"] == OPTIONAL_ABSENT_SOURCE:
            require(item == {"path": OPTIONAL_ABSENT_SOURCE, "status": "ABSENT", "sha256": None}, "M16 absent receipt binding changed")
            require(not safe_path(OPTIONAL_ABSENT_SOURCE).exists(), "M16 receipt now exists; refresh required")
        else:
            blob = git_bytes(implementation_commit, item["path"])
            require(blob is not None and item["status"] == "PRESENT" and
                    item["sha256"] == hashlib.sha256(blob).hexdigest(),
                    f"M24 source digest changed: {item['path']}")

    adb = data["adbClassification"]
    require(set(adb) == {
        "status", "endpoint", "protocol", "request", "responseSha256", "devices", "physicalDeviceCount",
        "emulatorCount", "unknownDeviceCount", "adbCliInvoked", "rawSerialRecorded", "packageQueryRun", "apkPullRun",
    }, "ADB classification keys changed")
    require(adb["status"] == "PASS" and adb["physicalDeviceCount"] == 0, "physical phone count must remain zero")
    require(adb["endpoint"] == {"host": "127.0.0.1", "port": 5037} and
            adb["protocol"] == "ADB_SERVER_HOST_PROTOCOL_V1" and adb["request"] == "host:devices-l",
            "ADB endpoint/protocol binding changed")
    require(re.fullmatch(r"[0-9a-f]{64}", adb["responseSha256"]) is not None, "ADB response hash invalid")
    require(adb["unknownDeviceCount"] == 0, "unknown ADB devices invalidate zero-phone claim")
    require(adb["adbCliInvoked"] is False and adb["rawSerialRecorded"] is False and
            adb["packageQueryRun"] is False and adb["apkPullRun"] is False,
            "ADB read-only boundary changed")
    for device in adb["devices"]:
        require(set(device) == {"serialSha256", "state", "classification", "metadata"}, "ADB device keys changed")
        require(set(device["metadata"]) == {"product", "model", "device", "transportId"}, "ADB metadata changed")
        require(re.fullmatch(r"[0-9a-f]{64}", device["serialSha256"]) is not None, "ADB serial hash invalid")
        require(device["state"] == "device" and device["classification"] in {"EMULATOR", "PHYSICAL"},
                "offline, unauthorized, or unknown ADB device invalidates zero-phone claim")
    if live_read_only:
        require(adb_classification() == adb, "live read-only ADB classification changed")
    require(data["phone"] == {"status": "NOT_RUN", "physicalDeviceCount": 0, "installedIdentityStatus": "NOT_RUN"},
            "phone NOT_RUN boundary changed")

    require(data["publishedBaseline"] == {
        "status": "PASS", "tag": "v0.1.37", "package": "app.codecks", "versionCode": 37,
        "versionName": "0.1.37", "apkSha256": "8c8eca1b3e4b0f56a2128185c42a062687011e68a9d3fd16fe24851616baa9f2",
        "signerSha256": "07a642e758f394b6aeaecfe35c64ca84d891ca4e6de4b6cc010702c0e52e2df6",
    }, "published M21 baseline changed")

    m16 = data["m16"]
    require((m16["status"], m16["capacityStatus"], m16["timeStatus"], m16["receiptPresent"]) ==
            ("NOT_RUN", "NOT_RUN", "NOT_RUN", False), "M16 NOT_RUN boundary changed")
    require((m16["plannedAvds"], m16["plannedProfilesPerAvd"], m16["plannedProfiles"], m16["observedProfiles"]) ==
            (4, 5, 20, 0), "M16 capacity accounting changed")
    require((m16["requiredHours"], m16["observedHours"], m16["requiredEligibleProfileHours"],
             m16["observedEligibleProfileHours"], m16["requiredAcknowledgedOperations"],
             m16["observedAcknowledgedOperations"]) == (168, 0, 3360, 0, 336000, 0), "M16 time/operation accounting changed")
    for path_key, sha_key in (("controllerPath", "controllerSha256"), ("validatorPath", "validatorSha256"), ("schemaPath", "schemaSha256")):
        require(sha256(safe_path(m16[path_key])) == m16[sha_key], f"M16 binding changed: {path_key}")

    validate_closure(data["m20Closure"], "COMPLETE", M20_INTEGRATION_COMMIT, implementation_commit)
    validate_closure(data["m21Closure"], "LOCAL_COMPLETE", M21_INTEGRATION_COMMIT, implementation_commit)
    require(data["m21Closure"]["receiptSchema"] == "codecks.m21.public-release-live.v2", "M21 receipt schema changed")
    require(data["m23"] == {
        "status": "NOT_A_CANDIDATE", "preflightPath": "docs/release/M23_LOCAL_PREFLIGHT.md",
        "preflightSha256": sha256(safe_path("docs/release/M23_LOCAL_PREFLIGHT.md")),
        "versionAssigned": False, "artifactAdmitted": False,
    }, "M23 NOT_A_CANDIDATE boundary changed")
    require(data["signing"] == {
        "status": "UNSET", "source": "CANONICAL_AGENT_ENV_NAME_PRESENCE_ONLY",
        "names": list(SIGNING_NAMES), "presentNames": [], "valuesRecorded": False,
    }, "signing-input boundary changed")
    if live_read_only:
        require(signing_presence() == data["signing"], "canonical signing-input presence changed")
    require(data["candidate"] == {
        "status": "ABSENT", "reason": "M23_NOT_A_CANDIDATE",
        "expectedArtifactPath": "release-candidate/codecks-release.apk", "artifactPresent": False,
        "sourceSha": None, "versionCode": None, "apkSha256": None, "signerSha256": None,
    }, "candidate absent boundary changed")
    require(data["dependencies"] == [
        {"milestone": "M16", "status": "NOT_RUN"},
        {"milestone": "M20", "status": "COMPLETE"},
        {"milestone": "M21", "status": "LOCAL_COMPLETE"},
        {"milestone": "M23", "status": "NOT_A_CANDIDATE"},
    ], "dependency status changed")
    require([tuple(gate[key] for key in ("id", "status", "code")) for gate in data["gates"]] == EXPECTED_GATES,
            "gate status/code changed")
    require(data["safeInPlaceUpdate"] == {
        "authorizedNow": False,
        "forbidden": ["UNINSTALL_PROTECTED_PACKAGE", "CLEAR_PROTECTED_PACKAGE_DATA", "DOWNGRADE_PROTECTED_PACKAGE", "DIFFERENT_SIGNER", "PHYSICAL_INSTRUMENTATION"],
    }, "update safety boundary changed")
    require(data["privacy"] == {
        "rawDeviceSerialRecorded": False, "privateKeyInspected": False,
        "credentialRecorded": False, "appDataRead": False,
    }, "privacy boundary changed")
    require(data["blockers"] == EXPECTED_BLOCKERS, "M24 blocker set changed")
    require(data["limitations"] == EXPECTED_LIMITATIONS, "M24 limitations changed")
    _reject_sensitive(data)


def _reject_sensitive(value: object) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            require(PROHIBITED_KEYS.search(key) is None, "M24 prohibited sensitive key")
            _reject_sensitive(child)
    elif isinstance(value, list):
        for child in value:
            _reject_sensitive(child)
    elif isinstance(value, str):
        require(not any(pattern.search(value) for pattern in PROHIBITED_VALUES), "M24 prohibited sensitive value")


def main() -> int:
    try:
        validate(live_read_only=True)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print("NO_GO: M24 current preflight; M21 local complete; M16/phone/M23/candidate blocked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
