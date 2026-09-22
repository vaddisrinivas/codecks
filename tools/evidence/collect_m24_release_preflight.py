#!/usr/bin/env python3
"""Collect a read-only, fail-closed M24 release preflight."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import stat
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ID = "codecks.autonomous-maturity.m24-preflight.v3"
BASE_COMMIT = "3e0e022819a1204a0a07c2db48798d0c2b890d23"
M20_INTEGRATION_COMMIT = "a5d0cc09d47179d30227369802932f29f1d82a18"
M21_INTEGRATION_COMMIT = "0a8f84faddbe8a1015a85f6431fd14d68b402c73"
SIGNING_NAMES = (
    "CODECKS_RELEASE_STORE_FILE", "CODECKS_RELEASE_KEY_ALIAS",
    "CODECKS_RELEASE_STORE_PASSWORD", "CODECKS_RELEASE_KEY_PASSWORD",
)
AGENT_ENV_WRAPPER = Path.home() / ".codex/skills/agent-env/scripts/run-with-agent-env.sh"
SOURCE_PATHS = (
    ".github/workflows/release.yml",
    "app/build.gradle.kts",
    "docs/release/M23_LOCAL_PREFLIGHT.md",
    "docs/release/M24_CURRENT_PREFLIGHT.md",
    "docs/release/production-state.json",
    "scripts/m16_autonomous_soak.py",
    "scripts/verify_release_no_shrink.sh",
    "tasks/AUTONOMOUS_MATURITY_TODO.md",
    "tasks/test-evidence/autonomous-maturity-m16-soak.json",
    "tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json",
    "tasks/test-evidence/m21-public-release-live.json",
    "tools/evidence/collect_m20_rollback_rehearsal.py",
    "tools/evidence/collect_m21_public_release_live.py",
    "tools/evidence/collect_m24_release_preflight.py",
    "tools/evidence/validate_m20_rollback_rehearsal.py",
    "tools/evidence/validate_m21_m23_local_readiness.py",
    "tools/evidence/validate_m24_release_preflight.py",
    "tools/evidence/test_m24_release_preflight.py",
    "tools/evidence/schemas/autonomous-maturity-m16-soak-v1.schema.json",
    "tools/evidence/schemas/autonomous-maturity-m24-preflight-v3.schema.json",
)
OPTIONAL_ABSENT_SOURCE = "tasks/test-evidence/autonomous-maturity-m16-soak.json"
ADB_HOST = "127.0.0.1"
ADB_PORT = 5037
IMPLEMENTATION_PATHS = (
    "docs/release/M24_CURRENT_PREFLIGHT.md",
    "tools/evidence/collect_m24_release_preflight.py",
    "tools/evidence/test_m24_release_preflight.py",
    "tools/evidence/validate_m24_release_preflight.py",
    "tools/evidence/schemas/autonomous-maturity-m24-preflight-v3.schema.json",
)
GATE_IDS = (
    "source.current_head_bound",
    "adb.read_only_classification",
    "phone.single_physical_connected",
    "m16.capacity_execution",
    "m16.elapsed_168h",
    "m21.local_support_package",
    "m23.exact_candidate",
    "candidate.source_artifact_signer",
    "signing.canonical_inputs",
    "update.data_preserving_authorized",
)


def run(*argv: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(argv, cwd=ROOT, check=check, capture_output=True, text=True)


def safe_path(value: str) -> Path:
    candidate = Path(value)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise ValueError(f"unsafe repository path: {value}")
    resolved = (ROOT / candidate).resolve(strict=False)
    if not resolved.is_relative_to(ROOT.resolve()):
        raise ValueError(f"path escapes repository: {value}")
    return resolved


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git_bytes(commit: str, path: str) -> bytes | None:
    result = subprocess.run(
        ["git", "show", f"{commit}:{path}"], cwd=ROOT, check=False, capture_output=True,
    )
    return result.stdout if result.returncode == 0 else None


def implementation_provenance(implementation_commit: str) -> dict:
    if run("git", "merge-base", "--is-ancestor", BASE_COMMIT, implementation_commit, check=False).returncode != 0:
        raise ValueError("M24 implementation commit is not descended from the canonical base")
    changed = run("git", "diff", "--name-only", BASE_COMMIT, implementation_commit).stdout.splitlines()
    if changed != sorted(IMPLEMENTATION_PATHS):
        raise ValueError(f"M24 implementation path set changed: {changed}")
    bindings = []
    for path in IMPLEMENTATION_PATHS:
        before = git_bytes(BASE_COMMIT, path)
        after = git_bytes(implementation_commit, path)
        if after is None:
            raise ValueError(f"M24 implementation path absent at implementation commit: {path}")
        patch_bytes = subprocess.run(
            ["git", "diff", "--binary", BASE_COMMIT, implementation_commit, "--", path],
            cwd=ROOT, check=True, capture_output=True,
        ).stdout
        bindings.append({
            "path": path,
            "change": "ADDED" if before is None else "MODIFIED",
            "baseSha256": hashlib.sha256(before).hexdigest() if before is not None else None,
            "implementationSha256": hashlib.sha256(after).hexdigest(),
            "patchSha256": hashlib.sha256(patch_bytes).hexdigest(),
        })
    full_patch = subprocess.run(
        ["git", "diff", "--binary", BASE_COMMIT, implementation_commit, "--", *IMPLEMENTATION_PATHS],
        cwd=ROOT, check=True, capture_output=True,
    ).stdout
    return {
        "baseCommit": BASE_COMMIT,
        "implementationCommit": implementation_commit,
        "implementationPaths": bindings,
        "implementationDiffSha256": hashlib.sha256(full_patch).hexdigest(),
        "receiptPath": "tasks/test-evidence/autonomous-maturity-m24-preflight.json",
        "receiptCommitRequired": True,
    }


def require_collection_worktree_clean() -> None:
    dirty = {
        line[3:] for line in run("git", "status", "--porcelain", "--untracked-files=all").stdout.splitlines()
        if len(line) >= 4
    }
    unexpected = sorted(dirty - {"tasks/test-evidence/autonomous-maturity-m24-preflight.json"})
    if unexpected:
        raise ValueError(f"commit M24 implementation before collection; dirty paths: {unexpected}")


def classify_android_device(serial: str, metadata: dict[str, str]) -> str:
    combined = " ".join((serial, metadata.get("product", ""), metadata.get("model", ""), metadata.get("device", ""))).lower()
    markers = ("emulator-", "emulator", "android_sdk", "sdk_gphone", "gphone", "aosp_")
    if any(marker in combined for marker in markers):
        return "EMULATOR"
    if all(metadata.get(key, "").strip() for key in ("product", "model", "device", "transportId")):
        return "PHYSICAL"
    return "UNKNOWN"


def recv_exact(connection: socket.socket, size: int) -> bytes:
    chunks = []
    remaining = size
    while remaining:
        chunk = connection.recv(remaining)
        if not chunk:
            raise ValueError("ADB server response ended before the declared length")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def adb_classification() -> dict:
    request = b"host:devices-l"
    frame = f"{len(request):04x}".encode("ascii") + request
    try:
        connection = socket.create_connection((ADB_HOST, ADB_PORT), timeout=0.5)
    except OSError as exc:
        raise ValueError("ADB default listener is absent or wrong; no adb CLI was invoked") from exc
    with connection:
        connection.sendall(frame)
        status = recv_exact(connection, 4)
        if status == b"FAIL":
            length_bytes = recv_exact(connection, 4)
            try:
                failure_length = int(length_bytes, 16)
            except ValueError as exc:
                raise ValueError("ADB FAIL response length is malformed") from exc
            failure = recv_exact(connection, failure_length).decode("utf-8", errors="replace")
            raise ValueError(f"ADB server rejected host:devices-l: {failure}")
        if status != b"OKAY":
            raise ValueError("ADB server protocol/version mismatch: expected OKAY")
        length_bytes = recv_exact(connection, 4)
        if not re.fullmatch(rb"[0-9A-Fa-f]{4}", length_bytes):
            raise ValueError("ADB server response length is malformed")
        payload_length = int(length_bytes, 16)
        if payload_length > 65535:
            raise ValueError("ADB server response exceeds protocol bound")
        payload = recv_exact(connection, payload_length)
    try:
        response = payload.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError("ADB server response is not UTF-8") from exc
    devices = []
    for line in response.splitlines():
        fields = line.split("\t")
        if len(fields) != 2 or not fields[0] or not fields[1]:
            raise ValueError("ADB host:devices-l contains a malformed device row")
        serial, details = fields
        tokens = details.split()
        state = tokens[0]
        if state != "device":
            raise ValueError(f"ADB device state is not eligible for a zero-phone claim: {state}")
        raw_metadata = {}
        for token in tokens[1:]:
            if ":" not in token:
                raise ValueError("ADB host:devices-l metadata token is malformed")
            key, value = token.split(":", 1)
            if key in raw_metadata or not key or not value:
                raise ValueError("ADB host:devices-l metadata is malformed")
            raw_metadata[key] = value
        required = {"product", "model", "device", "transport_id"}
        if not required.issubset(raw_metadata):
            raise ValueError("ADB host:devices-l metadata is incomplete")
        metadata = {
            "product": raw_metadata["product"], "model": raw_metadata["model"],
            "device": raw_metadata["device"], "transportId": raw_metadata["transport_id"],
        }
        classification = classify_android_device(serial, metadata)
        if classification == "UNKNOWN":
            raise ValueError("ADB device classification is unknown; zero-phone claim refused")
        devices.append({
            "serialSha256": hashlib.sha256(serial.encode()).hexdigest(),
            "state": state,
            "classification": classification,
            "metadata": metadata,
        })
    devices.sort(key=lambda item: item["serialSha256"])
    return {
        "status": "PASS",
        "endpoint": {"host": ADB_HOST, "port": ADB_PORT},
        "protocol": "ADB_SERVER_HOST_PROTOCOL_V1",
        "request": "host:devices-l",
        "responseSha256": hashlib.sha256(status + length_bytes + payload).hexdigest(),
        "devices": devices,
        "physicalDeviceCount": sum(item["classification"] == "PHYSICAL" for item in devices),
        "emulatorCount": sum(item["classification"] == "EMULATOR" for item in devices),
        "unknownDeviceCount": sum(item["classification"] == "UNKNOWN" for item in devices),
        "adbCliInvoked": False,
        "rawSerialRecorded": False,
        "packageQueryRun": False,
        "apkPullRun": False,
    }


def signing_presence() -> dict:
    if not AGENT_ENV_WRAPPER.is_file():
        raise ValueError("canonical agent-env wrapper unavailable")
    script = (
        "import json,os; names=" + repr(SIGNING_NAMES) + "; "
        "print(json.dumps({name:bool(os.getenv(name)) for name in names},sort_keys=True))"
    )
    result = subprocess.run(
        [str(AGENT_ENV_WRAPPER), "python3", "-c", script],
        check=True, capture_output=True, text=True,
    )
    present = json.loads(result.stdout)
    if set(present) != set(SIGNING_NAMES) or any(not isinstance(value, bool) for value in present.values()):
        raise ValueError("signing presence output malformed")
    return {
        "status": "PRESENT" if any(present.values()) else "UNSET",
        "source": "CANONICAL_AGENT_ENV_NAME_PRESENCE_ONLY",
        "names": list(SIGNING_NAMES),
        "presentNames": sorted(name for name, value in present.items() if value),
        "valuesRecorded": False,
    }


def closure(path: str, collector: str, validator: str, status: str, integration_commit: str) -> dict:
    receipt_path = safe_path(path)
    receipt = json.loads(receipt_path.read_text())
    return {
        "status": status,
        "integrationCommit": integration_commit,
        "receiptPath": path,
        "receiptSha256": sha256(receipt_path),
        "receiptSchema": receipt["schema"],
        "collectorPath": collector,
        "collectorSha256": sha256(safe_path(collector)),
        "validatorPath": validator,
        "validatorSha256": sha256(safe_path(validator)),
    }


def collect(implementation_commit: str | None = None) -> dict:
    source_commit = implementation_commit or run("git", "rev-parse", "HEAD").stdout.strip()
    require_collection_worktree_clean()
    provenance = implementation_provenance(source_commit)

    adb = adb_classification()
    if adb["physicalDeviceCount"] != 0:
        raise ValueError("physical phone present; this refresh is classification-only and requires phone count 0")

    m20_validator = run("python3", "tools/evidence/validate_m20_rollback_rehearsal.py")
    if m20_validator.returncode != 0:
        raise ValueError("M20 validator failed")
    m21_validator = run("python3", "tools/evidence/validate_m21_m23_local_readiness.py")
    m21_result = json.loads(m21_validator.stdout)
    if m21_result.get("m21") != "LOCAL_SUPPORT_PACKAGE_READY":
        raise ValueError("M21 local package is not complete")

    state = json.loads(safe_path("docs/release/production-state.json").read_text())
    if state["candidate"] != {"version_assigned": False, "artifact_admitted": False, "status": "unreleased_working_state"}:
        raise ValueError("candidate state promoted")
    signing = signing_presence()
    if signing["status"] != "UNSET":
        raise ValueError("release signing inputs are present; M24 candidate remains blocked")

    m21_receipt = json.loads(safe_path("tasks/test-evidence/m21-public-release-live.json").read_text())
    public = m21_receipt["artifact"]
    m16_receipt = safe_path(OPTIONAL_ABSENT_SOURCE)
    if m16_receipt.exists():
        raise ValueError("M16 runtime receipt unexpectedly exists; refresh status before proceeding")
    m16_script = safe_path("scripts/m16_autonomous_soak.py").read_text()
    for marker in ("range(1, 5)", "range(1, 6)", '"soak168h"', '"profiles": 20'):
        if marker not in m16_script:
            raise ValueError(f"M16 planned-capacity marker missing: {marker}")

    sources = []
    for path in SOURCE_PATHS:
        target = safe_path(path)
        sources.append({
            "path": path,
            "status": "ABSENT" if path == OPTIONAL_ABSENT_SOURCE else "PRESENT",
            "sha256": None if path == OPTIONAL_ABSENT_SOURCE else sha256(target),
        })

    gates = [
        {"id": GATE_IDS[0], "status": "PASS", "code": "exact_head_and_sources_bound"},
        {"id": GATE_IDS[1], "status": "PASS", "code": "raw_adb_server_devices_l_only"},
        {"id": GATE_IDS[2], "status": "NOT_RUN", "code": "physical_phone_count_zero"},
        {"id": GATE_IDS[3], "status": "NOT_RUN", "code": "m16_runtime_receipt_absent"},
        {"id": GATE_IDS[4], "status": "NOT_RUN", "code": "m16_168h_not_started"},
        {"id": GATE_IDS[5], "status": "PASS", "code": "m21_local_complete"},
        {"id": GATE_IDS[6], "status": "NOT_RUN", "code": "m23_not_a_candidate"},
        {"id": GATE_IDS[7], "status": "NOT_RUN", "code": "candidate_absent"},
        {"id": GATE_IDS[8], "status": "PASS", "code": "canonical_signing_inputs_unset"},
        {"id": GATE_IDS[9], "status": "NOT_RUN", "code": "explicit_execution_not_authorized"},
    ]
    return {
        "schema": SCHEMA_ID,
        "milestone": "M24",
        "evidenceLevel": "PREFLIGHT_ONLY",
        "verdict": "NO_GO",
        "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sourceCommit": source_commit,
        "provenance": provenance,
        "sources": sources,
        "adbClassification": adb,
        "phone": {"status": "NOT_RUN", "physicalDeviceCount": 0, "installedIdentityStatus": "NOT_RUN"},
        "publishedBaseline": {
            "status": "PASS", "tag": m21_receipt["release"]["tag"],
            "package": public["package"], "versionCode": public["versionCode"],
            "versionName": public["versionName"], "apkSha256": public["apkSha256"],
            "signerSha256": public["signerCertificateSha256"],
        },
        "m16": {
            "status": "NOT_RUN", "capacityStatus": "NOT_RUN", "timeStatus": "NOT_RUN",
            "plannedAvds": 4, "plannedProfilesPerAvd": 5, "plannedProfiles": 20,
            "observedProfiles": 0, "requiredHours": 168, "observedHours": 0,
            "requiredEligibleProfileHours": 3360, "observedEligibleProfileHours": 0,
            "requiredAcknowledgedOperations": 336000, "observedAcknowledgedOperations": 0,
            "receiptPath": OPTIONAL_ABSENT_SOURCE, "receiptPresent": False,
            "controllerPath": "scripts/m16_autonomous_soak.py",
            "controllerSha256": sha256(safe_path("scripts/m16_autonomous_soak.py")),
            "validatorPath": "tools/evidence/validate_m16_autonomous_soak.py",
            "validatorSha256": sha256(safe_path("tools/evidence/validate_m16_autonomous_soak.py")),
            "schemaPath": "tools/evidence/schemas/autonomous-maturity-m16-soak-v1.schema.json",
            "schemaSha256": sha256(safe_path("tools/evidence/schemas/autonomous-maturity-m16-soak-v1.schema.json")),
        },
        "m20Closure": closure(
            "tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json",
            "tools/evidence/collect_m20_rollback_rehearsal.py",
            "tools/evidence/validate_m20_rollback_rehearsal.py",
            "COMPLETE", M20_INTEGRATION_COMMIT,
        ),
        "m21Closure": closure(
            "tasks/test-evidence/m21-public-release-live.json",
            "tools/evidence/collect_m21_public_release_live.py",
            "tools/evidence/validate_m21_m23_local_readiness.py",
            "LOCAL_COMPLETE", M21_INTEGRATION_COMMIT,
        ),
        "m23": {
            "status": "NOT_A_CANDIDATE",
            "preflightPath": "docs/release/M23_LOCAL_PREFLIGHT.md",
            "preflightSha256": sha256(safe_path("docs/release/M23_LOCAL_PREFLIGHT.md")),
            "versionAssigned": False, "artifactAdmitted": False,
        },
        "signing": signing,
        "candidate": {
            "status": "ABSENT", "reason": "M23_NOT_A_CANDIDATE",
            "expectedArtifactPath": "release-candidate/codecks-release.apk",
            "artifactPresent": safe_path("release-candidate/codecks-release.apk").exists(),
            "sourceSha": None, "versionCode": None, "apkSha256": None, "signerSha256": None,
        },
        "dependencies": [
            {"milestone": "M16", "status": "NOT_RUN"},
            {"milestone": "M20", "status": "COMPLETE"},
            {"milestone": "M21", "status": "LOCAL_COMPLETE"},
            {"milestone": "M23", "status": "NOT_A_CANDIDATE"},
        ],
        "gates": gates,
        "safeInPlaceUpdate": {
            "authorizedNow": False,
            "forbidden": [
                "UNINSTALL_PROTECTED_PACKAGE", "CLEAR_PROTECTED_PACKAGE_DATA",
                "DOWNGRADE_PROTECTED_PACKAGE", "DIFFERENT_SIGNER", "PHYSICAL_INSTRUMENTATION",
            ],
        },
        "privacy": {
            "rawDeviceSerialRecorded": False, "privateKeyInspected": False,
            "credentialRecorded": False, "appDataRead": False,
        },
        "blockers": [
            "PHYSICAL_PHONE_NOT_CONNECTED", "M16_CAPACITY_NOT_RUN", "M16_168H_NOT_RUN",
            "M23_NOT_A_CANDIDATE", "CANDIDATE_NOT_BUILT", "PRODUCTION_SIGNING_INPUTS_UNSET",
        ],
        "limitations": [
            "Read-only ADB classification used one raw host:devices-l server request; no adb CLI, package query, pull, install, or instrumentation ran.",
            "M21 local distribution/support preparation is complete; physical update and external publication remain outside this preflight.",
            "M16 capacity execution and 168-hour evidence remain NOT_RUN; planned source capacity is not runtime proof.",
        ],
    }


def atomic_write(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    previous = path.stat() if path.exists() else None
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        if previous is not None:
            os.fchmod(descriptor, stat.S_IMODE(previous.st_mode))
            os.fchown(descriptor, previous.st_uid, previous.st_gid)
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        directory_descriptor = os.open(path.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
        try:
            os.fsync(directory_descriptor)
        finally:
            os.close(directory_descriptor)
    finally:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="tasks/test-evidence/autonomous-maturity-m24-preflight.json")
    args = parser.parse_args()
    try:
        payload = collect()
        atomic_write(safe_path(args.output), (json.dumps(payload, indent=2) + "\n").encode())
    except (OSError, ValueError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print("NO_GO: M24 current preflight; M21 local complete; M16/phone/M23/candidate blocked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
