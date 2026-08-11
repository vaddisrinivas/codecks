#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess

from collect_m24_release_preflight import (
    GATE_IDS,
    PUBLIC_APK_SHA256,
    PUBLIC_SIGNER_SHA256,
    ROOT,
    SCHEMA_ID,
    SOURCE_PATHS,
    safe_path,
    sha256,
)
from validate_autonomous_maturity_evidence import validate_schema_node

RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m24-preflight.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m24-preflight-v1.schema.json"
EXPECTED_ROOT_KEYS = {
    "schema", "milestone", "evidenceLevel", "verdict", "generatedAtUtc", "sourceCommit",
    "sources", "connectedDevice", "publishedBaseline", "m20Closure", "candidate", "dependencies", "gates",
    "safeInPlaceUpdate", "privacy", "blockers",
}
EXPECTED_BLOCKERS = {"PHYSICAL_PHONE_NOT_CONNECTED", "CANDIDATE_NOT_BUILT"}
CONNECTED_DEVICE_KEYS = {
    "status", "physicalDeviceCount", "emulatorCount", "unknownDeviceCount", "deviceIdSha256",
    "manufacturer", "model", "androidSdk", "package", "versionCode", "versionName", "installer",
    "installedApkSha256", "signerSha256",
}
REQUIRED_UPDATE_ORDER = [
    "one_authorized_physical_phone",
    "read_installed_app.codecks_identity_version_signer",
    "bind_exact_candidate_source_and_checksum",
    "require_candidate_package_app.codecks",
    "require_candidate_version_greater_than_installed",
    "require_candidate_signer_equal_to_installed_signer",
    "require_candidate_minification_and_resource_shrinking_disabled",
    "require_exact_candidate_managed_and_release_tests",
    "capture_redacted_pre_update_data_and_ssh_hid_state",
    "obtain_explicit_install_authorization",
    "adb_install_r_no_streaming_only",
    "verify_post_update_version_and_preserved_data",
    "verify_post_update_ssh_and_hid_without_instrumentation",
]
REQUIRED_FORBIDDEN = [
    "UNINSTALL_PROTECTED_PACKAGE", "CLEAR_PROTECTED_PACKAGE_DATA", "DOWNGRADE_PROTECTED_PACKAGE",
    "DIFFERENT_SIGNER", "PHYSICAL_INSTRUMENTATION",
]
EXPECTED_GATES = [
    ("source.canonical_clean_sha", "PASS", "exact_source_sha"),
    ("phone.single_physical_connected", "NOT_RUN", "phone_unavailable"),
    ("phone.installed_package_identity", "NOT_RUN", "phone_unavailable"),
    ("phone.installed_version", "NOT_RUN", "phone_unavailable"),
    ("phone.installed_signer", "NOT_RUN", "phone_unavailable"),
    ("public.checksum", "PASS", "checksum_verified"),
    ("public.signer_baseline", "PASS", "signer_baseline_verified"),
    ("candidate.available", "DEFERRED", "candidate_not_built"),
    ("candidate.source_and_checksum_bound", "DEFERRED", "candidate_not_built"),
    ("candidate.package_and_version_monotonic", "DEFERRED", "candidate_not_built"),
    ("candidate.signer_matches_installed", "DEFERRED", "candidate_not_built"),
    ("candidate.unshrunk", "DEFERRED", "candidate_not_built"),
    ("candidate.exact_tests", "DEFERRED", "candidate_not_built"),
    ("update.data_preserving_authorized", "DEFERRED", "explicit_execution_not_authorized"),
]
ALLOWED_KEYSETS = {
    "publishedBaseline": {"status", "tag", "package", "versionCode", "versionName", "apkSha256", "signerSha256", "checksumVerified", "privateSigningMaterialInspected"},
    "m20Closure": {"status", "integrationCommit", "receiptSourceCommit", "receiptPath", "receiptSha256", "receiptDigest", "collectorPath", "collectorSha256", "validatorPath", "validatorSha256"},
    "candidate": {"status", "reason", "buildWorkflow", "expectedArtifactPath", "expectedPackage", "sourceSha", "versionCode", "apkSha256", "signerSha256"},
    "safeInPlaceUpdate": {"authorizedNow", "requiredOrder", "forbidden"},
    "privacy": {"rawDeviceSerialRecorded", "privateKeyInspected", "credentialRecorded", "appDataRead"},
}
PROHIBITED_KEYS = re.compile(r"(?:rawSerial|password|secret|privateKeyValue|credentialValue|authToken)", re.I)
PROHIBITED_VALUES = (
    re.compile(r"-----BEGIN [^-\r\n]*PRIVATE KEY-----", re.I),
    re.compile(r"\b(?:password|credential|secret|api[_-]?key|private[_-]?key)\s*[:=]\s*[^\s,;]+", re.I),
    re.compile(r"M24_SECRET_CANARY", re.I),
    re.compile(r"(?:^|\s)(?:adb|pm)\s+(?:uninstall|clear)(?:\s|$)", re.I),
    re.compile(r"(?:^|\s)adb\s+install\b[^\r\n]*(?:\s-d\b|--downgrade\b)", re.I),
    re.compile(r"\brm\s+-rf\b", re.I),
)


def validate(path: Path = RECEIPT) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_schema_node(data, schema, schema, "m24")
    if set(data) != EXPECTED_ROOT_KEYS:
        raise ValueError("M24 receipt keys are not closed")
    if (data["schema"], data["milestone"], data["evidenceLevel"], data["verdict"]) != (
        SCHEMA_ID, "M24", "PREFLIGHT_ONLY", "NO_GO",
    ):
        raise ValueError("M24 identity or verdict mismatch")
    commit = data["sourceCommit"]
    object_type = subprocess.run(
        ["git", "cat-file", "-t", commit], cwd=ROOT, capture_output=True, text=True,
    )
    if object_type.returncode != 0 or object_type.stdout.strip() != "commit":
        raise ValueError("M24 source commit is not a commit object")
    if subprocess.run(
        ["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=ROOT, capture_output=True,
    ).returncode != 0:
        raise ValueError("M24 source commit is not an ancestor")
    if [item["path"] for item in data["sources"]] != list(SOURCE_PATHS):
        raise ValueError("M24 source path set/order mismatch")
    for item in data["sources"]:
        if sha256(safe_path(item["path"])) != item["sha256"]:
            raise ValueError(f"M24 source digest mismatch: {item['path']}")
    baseline = data["publishedBaseline"]
    if baseline != {
        "status": "PASS", "tag": "v0.1.37", "package": "app.codecks",
        "versionCode": 37, "versionName": "0.1.37", "apkSha256": PUBLIC_APK_SHA256,
        "signerSha256": PUBLIC_SIGNER_SHA256, "checksumVerified": True,
        "privateSigningMaterialInspected": False,
    }:
        raise ValueError("M24 public signer baseline mismatch")
    m20 = data["m20Closure"]
    if m20 != {
        "status": "COMPLETE",
        "integrationCommit": "a5d0cc09d47179d30227369802932f29f1d82a18",
        "receiptSourceCommit": "a899c24ac10d2f393ac5ba41d7a47830f6a96f09",
        "receiptPath": "tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json",
        "receiptSha256": "b6690f51d02995ea9d9dabe7e98144e2c10f301dc4f068d0997c7de37596e5ac",
        "receiptDigest": "fdc322bf8f926ac4b2339f714d23a330a16815a715adfcee981f8146ae88088b",
        "collectorPath": "tools/evidence/collect_m20_rollback_rehearsal.py",
        "collectorSha256": "aeee48e892c9e1d49c36f40f843141f27c50cfc262adebf706694c24d3e6030a",
        "validatorPath": "tools/evidence/validate_m20_rollback_rehearsal.py",
        "validatorSha256": "aee6d75733c97d13c9f37a2325df77d5ee8db7f7bbf3b574c4b67871c009e976",
    }:
        raise ValueError("M24 M20 closure binding mismatch")
    if sha256(safe_path(m20["receiptPath"])) != m20["receiptSha256"]:
        raise ValueError("M24 M20 receipt drift")
    if sha256(safe_path(m20["collectorPath"])) != m20["collectorSha256"]:
        raise ValueError("M24 M20 collector drift")
    if sha256(safe_path(m20["validatorPath"])) != m20["validatorSha256"]:
        raise ValueError("M24 M20 validator drift")
    if subprocess.run(
        ["git", "merge-base", "--is-ancestor", m20["receiptSourceCommit"], m20["integrationCommit"]],
        cwd=ROOT,
        capture_output=True,
    ).returncode != 0:
        raise ValueError("M24 M20 source/commit ancestry mismatch")
    phone = data["connectedDevice"]
    if set(phone) != CONNECTED_DEVICE_KEYS:
        raise ValueError("M24 connected-device keys are not closed")
    if phone["physicalDeviceCount"] != 0 or phone["status"] != "NOT_RUN":
        raise ValueError("M24 preflight snapshot must retain physical phone NOT_RUN")
    required_null = {
        "deviceIdSha256", "manufacturer", "model", "androidSdk", "package", "versionCode",
        "versionName", "installer", "installedApkSha256", "signerSha256",
    }
    if any(phone[key] is not None for key in required_null):
        raise ValueError("M24 absent phone cannot claim installed identity")
    candidate = data["candidate"]
    if candidate != {
        "status": "DEFERRED", "reason": "CANDIDATE_NOT_BUILT",
        "buildWorkflow": ".github/workflows/release.yml",
        "expectedArtifactPath": "release-candidate/codecks-release.apk",
        "expectedPackage": "app.codecks", "sourceSha": None, "versionCode": None,
        "apkSha256": None, "signerSha256": None,
    }:
        raise ValueError("M24 candidate must remain unbuilt and deferred")
    if data["dependencies"] != [
        {"milestone": "M20", "status": "COMPLETE"},
        {"milestone": "M21", "status": "DEFERRED"},
        {"milestone": "M23", "status": "DEFERRED"},
    ]:
        raise ValueError("M24 dependency status mismatch")
    if [tuple(gate[key] for key in ("id", "status", "code")) for gate in data["gates"]] != EXPECTED_GATES:
        raise ValueError("M24 gate id/status/code mismatch")
    if any(set(gate) != {"id", "status", "code"} for gate in data["gates"]):
        raise ValueError("M24 gate keys are not closed")
    update = data["safeInPlaceUpdate"]
    if update["authorizedNow"] is not False:
        raise ValueError("M24 install cannot be authorized by preflight")
    if update["requiredOrder"] != REQUIRED_UPDATE_ORDER:
        raise ValueError("M24 required update order changed")
    if update["forbidden"] != REQUIRED_FORBIDDEN:
        raise ValueError("M24 destructive stop conditions changed")
    if set(data["blockers"]) != EXPECTED_BLOCKERS:
        raise ValueError("M24 blocker set mismatch")
    if data["privacy"] != {
        "rawDeviceSerialRecorded": False, "privateKeyInspected": False,
        "credentialRecorded": False, "appDataRead": False,
    }:
        raise ValueError("M24 privacy boundary mismatch")
    for key, expected in ALLOWED_KEYSETS.items():
        if set(data[key]) != expected:
            raise ValueError(f"M24 nested keys are not closed: {key}")
    if any(set(item) != {"milestone", "status"} for item in data["dependencies"]):
        raise ValueError("M24 dependency keys are not closed")
    if any(set(item) != {"path", "sha256"} for item in data["sources"]):
        raise ValueError("M24 source keys are not closed")
    _reject_sensitive(data)


def _reject_sensitive(value: object) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if PROHIBITED_KEYS.search(key):
                raise ValueError("M24 prohibited sensitive key")
            _reject_sensitive(child)
    elif isinstance(value, list):
        for child in value:
            _reject_sensitive(child)
    elif isinstance(value, str):
        if any(pattern.search(value) for pattern in PROHIBITED_VALUES):
            raise ValueError("M24 prohibited secret-like value")


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print("NO_GO: M24 preflight is fail-closed; M20 complete, physical phone/candidate unresolved")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
