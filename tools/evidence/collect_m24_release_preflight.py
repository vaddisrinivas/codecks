#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ID = "codecks.autonomous-maturity.m24-preflight.v1"
PUBLIC_TAG = "v0.1.37"
PUBLIC_APK_SHA256 = "8c8eca1b3e4b0f56a2128185c42a062687011e68a9d3fd16fe24851616baa9f2"
PUBLIC_SIGNER_SHA256 = "07a642e758f394b6aeaecfe35c64ca84d891ca4e6de4b6cc010702c0e52e2df6"
SOURCE_PATHS = (
    ".github/workflows/release.yml",
    "app/build.gradle.kts",
    "docs/release/PHYSICAL_SSH_RELEASE_GATE.md",
    "docs/release/production-state.json",
    "scripts/physical_release_ssh_smoke.sh",
    "scripts/verify_release_no_shrink.sh",
    "tasks/AUTONOMOUS_MATURITY_PLAN.md",
    "tasks/test-evidence/autonomous-maturity-m00-baseline.json",
    "tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json",
    "tools/evidence/collect_m20_rollback_rehearsal.py",
    "tools/evidence/validate_m20_rollback_rehearsal.py",
    "tools/evidence/collect_m24_release_preflight.py",
    "tools/evidence/validate_m24_release_preflight.py",
    "tools/evidence/test_m24_release_preflight.py",
    "tools/evidence/schemas/autonomous-maturity-m24-preflight-v1.schema.json",
)
GATE_IDS = (
    "source.canonical_clean_sha",
    "phone.single_physical_connected",
    "phone.installed_package_identity",
    "phone.installed_version",
    "phone.installed_signer",
    "public.checksum",
    "public.signer_baseline",
    "candidate.available",
    "candidate.source_and_checksum_bound",
    "candidate.package_and_version_monotonic",
    "candidate.signer_matches_installed",
    "candidate.unshrunk",
    "candidate.exact_tests",
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
    relative = resolved.relative_to(ROOT.resolve())
    for index in range(1, len(relative.parts) + 1):
        if ROOT.joinpath(*relative.parts[:index]).is_symlink():
            raise ValueError(f"symlink forbidden: {value}")
    return resolved


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def apksigner() -> str:
    direct = shutil.which("apksigner")
    if direct:
        return direct
    candidates = sorted(
        Path("/Users/srinivasvaddi/Library/Android/sdk/build-tools").glob("*/apksigner"),
        key=lambda path: tuple(int(piece) for piece in path.parent.name.split(".") if piece.isdigit()),
    )
    if not candidates:
        raise ValueError("apksigner unavailable")
    return str(candidates[-1])


def signer_digest(apk: Path) -> str:
    output = subprocess.run(
        [apksigner(), "verify", "--verbose", "--print-certs", str(apk)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})", output)
    if not match:
        raise ValueError("APK signer digest unavailable")
    return match.group(1).lower()


def public_baseline() -> dict:
    with tempfile.TemporaryDirectory(prefix="codecks-m24-public-") as directory:
        subprocess.run(
            [
                "gh", "release", "download", PUBLIC_TAG,
                "--repo", "vaddisrinivas/codecks",
                "--pattern", "codecks-release.apk",
                "--pattern", "SHA256SUMS.txt",
                "--dir", directory,
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        apk = Path(directory) / "codecks-release.apk"
        checksum = (Path(directory) / "SHA256SUMS.txt").read_text(encoding="utf-8").split()[0].lower()
        actual = sha256(apk)
        signer = signer_digest(apk)
        if checksum != actual or actual != PUBLIC_APK_SHA256 or signer != PUBLIC_SIGNER_SHA256:
            raise ValueError("public release checksum or signer baseline changed")
        return {
            "status": "PASS",
            "tag": PUBLIC_TAG,
            "package": "app.codecks",
            "versionCode": 37,
            "versionName": "0.1.37",
            "apkSha256": actual,
            "signerSha256": signer,
            "checksumVerified": True,
            "privateSigningMaterialInspected": False,
        }


def connected_device() -> dict:
    adb = shutil.which("adb") or "/Users/srinivasvaddi/Library/Android/sdk/platform-tools/adb"
    lines = subprocess.run([adb, "devices"], check=True, capture_output=True, text=True).stdout.splitlines()[1:]
    active = [(line.split()[0], line.split()[1]) for line in lines if len(line.split()) >= 2]
    classified = []
    for serial, state in active:
        if state != "device":
            classified.append((serial, "UNKNOWN"))
            continue
        def read_property(name: str) -> str:
            result = subprocess.run(
                [adb, "-s", serial, "shell", "getprop", name],
                check=False,
                capture_output=True,
                text=True,
            )
            return result.stdout.strip() if result.returncode == 0 else ""
        classified.append((serial, classify_android_device({
            "ro.kernel.qemu": read_property("ro.kernel.qemu"),
            "ro.build.characteristics": read_property("ro.build.characteristics"),
            "ro.product.model": read_property("ro.product.model"),
        })))
    physical = [serial for serial, kind in classified if kind == "PHYSICAL"]
    emulators = [serial for serial, kind in classified if kind == "EMULATOR"]
    unknown = [serial for serial, kind in classified if kind == "UNKNOWN"]
    base = {
        "status": "NOT_RUN" if len(physical) == 0 else "BLOCKED",
        "physicalDeviceCount": len(physical),
        "emulatorCount": len(emulators),
        "unknownDeviceCount": len(unknown),
        "deviceIdSha256": None,
        "manufacturer": None,
        "model": None,
        "androidSdk": None,
        "package": None,
        "versionCode": None,
        "versionName": None,
        "installer": None,
        "installedApkSha256": None,
        "signerSha256": None,
    }
    if len(physical) != 1 or unknown:
        return base
    serial = physical[0]
    def adb_shell(*args: str) -> str:
        return subprocess.run([adb, "-s", serial, "shell", *args], check=True, capture_output=True, text=True).stdout.strip()
    package_path = adb_shell("pm", "path", "app.codecks")
    if not package_path.startswith("package:"):
        return base
    package_dump = adb_shell("dumpsys", "package", "app.codecks")
    version_code = re.search(r"versionCode=(\d+)", package_dump)
    version_name = re.search(r"versionName=([^\s]+)", package_dump)
    installer_line = adb_shell("cmd", "package", "list", "packages", "-i", "app.codecks")
    installer_match = re.search(r"installer=([^\s]+)", installer_line)
    with tempfile.TemporaryDirectory(prefix="codecks-m24-installed-") as directory:
        apk = Path(directory) / "installed.apk"
        subprocess.run(
            [adb, "-s", serial, "pull", package_path.splitlines()[0].removeprefix("package:"), str(apk)],
            check=True,
            capture_output=True,
            text=True,
        )
        installed_apk_sha = sha256(apk)
        installed_signer = signer_digest(apk)
    return {
        **base,
        "status": "PASS",
        "deviceIdSha256": hashlib.sha256(serial.encode()).hexdigest(),
        "manufacturer": adb_shell("getprop", "ro.product.manufacturer"),
        "model": adb_shell("getprop", "ro.product.model"),
        "androidSdk": int(adb_shell("getprop", "ro.build.version.sdk")),
        "package": "app.codecks",
        "versionCode": int(version_code.group(1)) if version_code else None,
        "versionName": version_name.group(1) if version_name else None,
        "installer": installer_match.group(1) if installer_match else "unknown",
        "installedApkSha256": installed_apk_sha,
        "signerSha256": installed_signer,
    }


def classify_android_device(properties: dict[str, str]) -> str:
    qemu = properties.get("ro.kernel.qemu", "").strip().lower()
    characteristics = properties.get("ro.build.characteristics", "").strip().lower()
    model = properties.get("ro.product.model", "").strip().lower()
    emulator_model_markers = ("emulator", "android sdk", "sdk_gphone", "gphone", "aosp on")
    if qemu == "1" or "emulator" in characteristics or any(marker in model for marker in emulator_model_markers):
        return "EMULATOR"
    if qemu in {"", "0"} and characteristics and model:
        return "PHYSICAL"
    return "UNKNOWN"


def collect() -> dict:
    source_commit = run("git", "rev-parse", "HEAD").stdout.strip()
    if run("git", "status", "--porcelain").stdout.strip():
        # This worktree intentionally contains the untracked preflight implementation.
        tracked_drift = run("git", "diff", "--name-only").stdout.splitlines()
        if tracked_drift:
            raise ValueError(f"tracked source drift during preflight: {tracked_drift}")
    published = public_baseline()
    phone = connected_device()
    m20_validator = run("python3", "tools/evidence/validate_m20_rollback_rehearsal.py")
    if m20_validator.returncode != 0:
        raise ValueError("M20 validator failed")
    m20_path = safe_path("tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json")
    m20 = json.loads(m20_path.read_text(encoding="utf-8"))
    if (m20.get("milestone"), m20.get("status")) != ("M20", "PASS"):
        raise ValueError("M20 receipt is not complete")
    m20_source_commit = m20.get("sourceCommit", "")
    if run("git", "merge-base", "--is-ancestor", m20_source_commit, source_commit, check=False).returncode != 0:
        raise ValueError("M20 receipt source is not an ancestor of M24 source")
    phone_present = phone["status"] == "PASS"
    gates = [
        {"id": GATE_IDS[0], "status": "PASS", "code": "exact_source_sha"},
        {"id": GATE_IDS[1], "status": "PASS" if phone_present else "NOT_RUN", "code": "single_physical" if phone_present else "phone_unavailable"},
        {"id": GATE_IDS[2], "status": "PASS" if phone_present and phone["package"] == "app.codecks" else "NOT_RUN", "code": "package_verified" if phone_present else "phone_unavailable"},
        {"id": GATE_IDS[3], "status": "PASS" if phone_present and phone["versionCode"] is not None else "NOT_RUN", "code": "version_verified" if phone_present else "phone_unavailable"},
        {"id": GATE_IDS[4], "status": "PASS" if phone_present and phone["signerSha256"] else "NOT_RUN", "code": "signer_verified" if phone_present else "phone_unavailable"},
        {"id": GATE_IDS[5], "status": "PASS", "code": "checksum_verified"},
        {"id": GATE_IDS[6], "status": "PASS", "code": "signer_baseline_verified"},
    ] + [
        {"id": gate, "status": "DEFERRED", "code": "candidate_not_built"}
        for gate in GATE_IDS[7:13]
    ] + [
        {"id": GATE_IDS[13], "status": "DEFERRED", "code": "explicit_execution_not_authorized"},
    ]
    return {
        "schema": SCHEMA_ID,
        "milestone": "M24",
        "evidenceLevel": "PREFLIGHT_ONLY",
        "verdict": "NO_GO",
        "generatedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "sourceCommit": source_commit,
        "sources": [{"path": path, "sha256": sha256(safe_path(path))} for path in SOURCE_PATHS],
        "connectedDevice": phone,
        "publishedBaseline": published,
        "m20Closure": {
            "status": "COMPLETE",
            "integrationCommit": source_commit,
            "receiptSourceCommit": m20_source_commit,
            "receiptPath": "tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json",
            "receiptSha256": sha256(m20_path),
            "receiptDigest": m20["receiptDigest"],
            "collectorPath": "tools/evidence/collect_m20_rollback_rehearsal.py",
            "collectorSha256": sha256(safe_path("tools/evidence/collect_m20_rollback_rehearsal.py")),
            "validatorPath": "tools/evidence/validate_m20_rollback_rehearsal.py",
            "validatorSha256": sha256(safe_path("tools/evidence/validate_m20_rollback_rehearsal.py")),
        },
        "candidate": {
            "status": "DEFERRED",
            "reason": "CANDIDATE_NOT_BUILT",
            "buildWorkflow": ".github/workflows/release.yml",
            "expectedArtifactPath": "release-candidate/codecks-release.apk",
            "expectedPackage": "app.codecks",
            "sourceSha": None,
            "versionCode": None,
            "apkSha256": None,
            "signerSha256": None,
        },
        "dependencies": [
            {"milestone": "M20", "status": "COMPLETE"},
            {"milestone": "M21", "status": "DEFERRED"},
            {"milestone": "M23", "status": "DEFERRED"},
        ],
        "gates": gates,
        "safeInPlaceUpdate": {
            "authorizedNow": False,
            "requiredOrder": [
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
            ],
            "forbidden": [
                "UNINSTALL_PROTECTED_PACKAGE",
                "CLEAR_PROTECTED_PACKAGE_DATA",
                "DOWNGRADE_PROTECTED_PACKAGE",
                "DIFFERENT_SIGNER",
                "PHYSICAL_INSTRUMENTATION",
            ],
        },
        "privacy": {
            "rawDeviceSerialRecorded": False,
            "privateKeyInspected": False,
            "credentialRecorded": False,
            "appDataRead": False,
        },
        "blockers": [
            "PHYSICAL_PHONE_NOT_CONNECTED" if not phone_present else "PHYSICAL_PHONE_FINAL_UPDATE_NOT_AUTHORIZED",
            "CANDIDATE_NOT_BUILT",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="tasks/test-evidence/autonomous-maturity-m24-preflight.json")
    args = parser.parse_args()
    try:
        payload = collect()
        output = safe_path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}")
        return 1
    print("NO_GO: M24 preflight sealed; M20 complete, M21/M23 deferred, phone/candidate unresolved")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
