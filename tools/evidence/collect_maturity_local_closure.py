#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys

from generate_autonomous_maturity_source_inventory import atomic_write
from rehearse_m20_disposable_signer import PUBLIC_FIXTURE_NAMES, rehearse

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "tasks/test-evidence/autonomous-maturity-local-closure.json"
M20_FIXTURE_DIR = ROOT / "tasks/test-evidence/m20-disposable-signer"
SOURCE_PATHS = (
    "tasks/AUTONOMOUS_MATURITY_TODO.md",
    "docs/architecture/M09_LOCAL_METRICS.md",
    "tasks/test-evidence/autonomous-maturity-m09-current-source-census.json",
    "tools/evidence/generate_m09_current_source_census.py",
    "tools/evidence/generate_autonomous_maturity_source_inventory.py",
    "tools/evidence/rehearse_m20_disposable_signer.py",
    "tools/evidence/test_rehearse_m20_disposable_signer.py",
    "tools/evidence/collect_maturity_local_closure.py",
    "tools/evidence/schemas/autonomous-maturity-local-closure-v1.schema.json",
    "tools/evidence/validate_maturity_local_closure.py",
    "tools/evidence/test_validate_maturity_local_closure.py",
    "tools/evidence/validate_m02a_dependency_spikes.py",
    "tasks/test-evidence/m02a/dependency-spike-results.json",
    "tools/evidence/validate_m09_current_source_census.py",
    "scripts/verify_m12_current_mac_receipt.py",
    "scripts/m12_current_mac_lib.py",
    "tasks/test-evidence/autonomous-maturity-m12-current-mac.json",
    "tools/evidence/validate_m14_first_run.py",
    "tools/evidence/collect_m14_first_run.py",
    "tools/evidence/managed_execution_binding.py",
    "tasks/test-evidence/autonomous-maturity-m14-first-run.json",
    "tools/evidence/validate_m15_clipboard_battery.py",
    "tools/evidence/collect_m15_clipboard_battery.py",
    "tools/evidence/validate_autonomous_maturity_evidence.py",
    "tools/evidence/schemas/autonomous-maturity-m15-clipboard-battery-v1.schema.json",
    "tasks/test-evidence/autonomous-maturity-m15-clipboard-battery.json",
    "tools/evidence/validate_m20_rollback_rehearsal.py",
    "tools/evidence/collect_m20_rollback_rehearsal.py",
    "tools/evidence/schemas/autonomous-maturity-m20-rollback-rehearsal-v1.schema.json",
    "tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json",
    *(f"tasks/test-evidence/m20-disposable-signer/{name}" for name in PUBLIC_FIXTURE_NAMES),
    "app/src/main/java/io/codecks/ui/theme/CodecksTheme.kt",
    "app/src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadActivity.kt",
    "macHelper/Sources/CodecksMacHelperApp/HelperViews.swift",
    "app/src/main/java/io/codecks/widget/TrackpadWidgetProvider.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeUiPolicy.kt",
)
LANE_SPECS = (
    ("m02a.rejected_decisions", "m02a", "STATIC_AND_CPU", "five_candidates_rejected"),
    ("m09.source_metrics", "m09", "EXACT_SOURCE", "metrics_published"),
    ("m09.runtime_metrics", "NOT_RUN", "RUNTIME", "no_current_candidate_or_device_run"),
    ("m09a.platform_semantics", "m09a", "EXACT_SOURCE", "platform_semantic_split"),
    ("m09d.current_lifecycle", "NOT_RUN", "CPU_AND_DEVICE", "host_resource_stop"),
    ("m12.non_mutating_local", "m12", "CURRENT_MAC_READ_ONLY", "14_pass_0_fail"),
    ("m12.mutation_and_external", "NOT_RUN", "EXTERNAL", "11_lanes_held"),
    ("m14.moderated_human", "NOT_RUN", "EXTERNAL", "human_evidence_required"),
    ("m15.path_stable_proxy", "m15", "CPU_AND_MANAGED_PROXY", "receipt_valid"),
    ("m15.runtime_and_physical", "NOT_RUN", "EXTERNAL", "six_lanes_held"),
    ("m20.disposable_signer", "m20_disposable", "DISPOSABLE_LOCAL", "same_accept_different_reject"),
    ("m20.real_key_and_update", "NOT_RUN", "EXTERNAL", "real_key_and_phone_unavailable"),
)
VALIDATOR_SPECS = (
    ("m02a", ("tools/evidence/validate_m02a_dependency_spikes.py",), "all decisions explicit"),
    ("m09", ("tools/evidence/validate_m09_current_source_census.py",), "PASS: M09 current census"),
    ("m12", ("scripts/verify_m12_current_mac_receipt.py", "tasks/test-evidence/autonomous-maturity-m12-current-mac.json"), "pass=14 fail=0 not_run=11"),
    ("m14", ("tools/evidence/validate_m14_first_run.py", "tasks/test-evidence/autonomous-maturity-m14-first-run.json"), "human pairing NOT_RUN"),
    ("m15", ("tools/evidence/validate_m15_clipboard_battery.py",), "6 runtime/external lanes remain NOT_RUN"),
    ("m20_rollback", ("tools/evidence/validate_m20_rollback_rehearsal.py",), "5 external lanes remain NOT_RUN"),
)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def digest(value: dict) -> str:
    copy = dict(value)
    copy.pop("receiptDigest", None)
    return hashlib.sha256(json.dumps(copy, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def run_validators() -> list[dict]:
    environment = {**__import__("os").environ, "ANDROID_HOME": __import__("os").environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))}
    results = []
    for check_id, arguments, expected in VALIDATOR_SPECS:
        result = subprocess.run([sys.executable, *arguments], cwd=ROOT, text=True, capture_output=True, env=environment)
        if result.returncode != 0 or expected not in result.stdout:
            raise RuntimeError(f"{check_id} validator failed: {result.stdout}{result.stderr}")
        results.append({
            "id": check_id,
            "command": ["python3", *arguments],
            "expectedOutput": expected,
            "stdoutSha256": hashlib.sha256(result.stdout.encode()).hexdigest(),
            "stderrSha256": hashlib.sha256(result.stderr.encode()).hexdigest(),
        })
    return results


def validate_platform_semantics() -> None:
    android = (ROOT / "app/src/main/java/io/codecks/ui/theme/CodecksTheme.kt").read_text()
    lock = (ROOT / "app/src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadActivity.kt").read_text()
    helper = (ROOT / "macHelper/Sources/CodecksMacHelperApp/HelperViews.swift").read_text()
    widget = (ROOT / "app/src/main/java/io/codecks/widget/TrackpadWidgetProvider.kt").read_text()
    overlay = (ROOT / "app/src/main/java/io/codecks/ui/theme/ThemeUiPolicy.kt").read_text()
    checks = (
        "LocalCodecksSemanticColors" in android and "MaterialTheme(" in android,
        "CodecksTheme" in lock,
        "ThemeSystemSurfaceStore" in widget and "theme.primary" in widget and "theme.content" in widget,
        "environment.overlay" in overlay,
        "Color(nsColor: .windowBackgroundColor)" in helper and helper.count(".foregroundStyle(.secondary)") >= 8,
    )
    if not all(checks):
        raise RuntimeError("platform semantic validation failed")


def derive_lanes(passed: set[str]) -> list[dict]:
    lanes = []
    for lane, gate, evidence, code in LANE_SPECS:
        status = "NOT_RUN" if gate == "NOT_RUN" else "PASS" if gate in passed else "FAIL"
        if status == "FAIL":
            raise RuntimeError(f"lane gate absent: {lane}")
        lanes.append({"id": lane, "status": status, "evidence": evidence, "code": code})
    return lanes


def collect() -> dict:
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    validations = run_validators()
    validate_platform_semantics()
    signer_result = rehearse(M20_FIXTURE_DIR)
    passed = {item["id"] for item in validations} | {"m09a"}
    if signer_result["sameKeyAccepted"] and signer_result["differentKeyRejected"]:
        passed.add("m20_disposable")
    value = {
        "schema": "codecks.autonomous-maturity.local-closure.v1",
        "status": "PASS_WITH_NOT_RUN",
        "sourceCommit": commit,
        "sources": [{"path": path, "sha256": sha(ROOT / path)} for path in SOURCE_PATHS],
        "validations": validations,
        "m20DisposableSigner": signer_result,
        "lanes": derive_lanes(passed),
        "limitations": [
            "No physical device, protected app, private signing key, release candidate, live provider, human, or publication evidence.",
            "M09D current execution stopped under host resource pressure and remains NOT_RUN.",
            "M16, M21, and M23 are outside this receipt.",
        ],
    }
    value["receiptDigest"] = digest(value)
    return value


def write_receipt(path: Path, value: dict, root: Path = ROOT) -> None:
    payload = (json.dumps(value, indent=2) + "\n").encode()
    atomic_write(root, path.absolute(), payload)


def main() -> None:
    write_receipt(OUTPUT, collect())


if __name__ == "__main__":
    main()
