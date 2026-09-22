#!/usr/bin/env python3
"""Validate locally closable maturity claims without promoting external lanes."""

from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess

from collect_maturity_local_closure import M20_FIXTURE_DIR, SOURCE_PATHS, derive_lanes, digest, run_validators
from rehearse_m20_disposable_signer import PUBLIC_FIXTURE_NAMES, apksigner, rehearse, signer_evidence, tool_binding
from validate_autonomous_maturity_evidence import validate_schema_node

ROOT = Path(__file__).resolve().parents[2]
TODO = ROOT / "tasks/AUTONOMOUS_MATURITY_TODO.md"
METRICS = ROOT / "docs/architecture/M09_LOCAL_METRICS.md"
CENSUS = ROOT / "tasks/test-evidence/autonomous-maturity-m09-current-source-census.json"
BASELINE = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"
RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-local-closure.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-local-closure-v1.schema.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_metrics(text: str, census: dict, baseline: dict) -> None:
    require(
        census["method"]["scope"] == "tracked and untracked non-ignored *.kt, *.java, and *.swift workspace files from git ls-files --cached --others --exclude-standard",
        "current census scope label",
    )
    require("baseline tracked; current tracked + untracked non-ignored" in text, "metric scope label")
    current = census["summary"]
    public = census["publicProduction"]
    base = baseline["summary"]
    expected = (
        (base["file_count"], current["fileCount"]),
        (base["physical_lines"], current["physicalLines"]),
        (base["by_category"]["public_production"], public["physicalLines"]),
        (max(item["physical_lines"] for item in baseline["files"] if item["category"] == "public_production"), public["largestFile"]["physicalLines"]),
    )
    for before, after in expected:
        require(f"{before:,}" in text and f"{after:,}" in text, f"missing metric {before}->{after}")
    require(f"{public['excessLines']:,}-line target excess" in text, "wrong excess")
    current_build = (ROOT / "app/build.gradle.kts").read_text()
    baseline_build = subprocess.check_output(["git", "show", f"{census['baseline']['commit']}:app/build.gradle.kts"], cwd=ROOT, text=True)
    production_pattern = re.compile(r"^\s*(implementation|api|compileOnly|runtimeOnly|ksp)\s*\(", re.MULTILINE)
    test_pattern = re.compile(r"^\s*(testImplementation|androidTestImplementation|debugImplementation)\s*\(", re.MULTILINE)
    dependency_rows = (
        ("Production dependency declarations", len(production_pattern.findall(baseline_build)), len(production_pattern.findall(current_build))),
        ("Test/debug dependency declarations", len(test_pattern.findall(baseline_build)), len(test_pattern.findall(current_build))),
    )
    for label, before, after in dependency_rows:
        require(f"| {label} | {before} | {after} |" in text, f"dependency metric {label}")
    require(text.count("`NOT_RUN`") >= 6, "runtime boundaries missing")
    require("not startup, APK, device, signer, candidate, or release evidence" in text, "metric overclaim")


def validate_todo(text: str) -> None:
    required_checked = (
        "M02A publish net LOC",
        "M02A reject every candidate",
        "M09 publish before/after LOC",
        "M09A apply Codecks semantic roles",
        "M12 pass the non-mutating current-Mac",
        "M12 label SSH-auth",
        "M14 preserve moderated-human pairing",
        "M20 verify signer-continuity logic with disposable local keys",
    )
    for claim in required_checked:
        require(f"- [x] {claim}" in text, f"unchecked local claim: {claim}")
    required_unchecked = (
        "M09D refresh bounded production-controller",
        "M09D pass the same lifecycle on a live provider",
        "M15 run foreground/background",
        "M20 verify real release-key custody",
        "M16 run 20 isolated profiles",
    )
    for claim in required_unchecked:
        require(f"- [ ] {claim}" in text, f"external/runtime lane promoted: {claim}")


def validate_semantic_platform_split(root: Path = ROOT) -> None:
    android = (root / "app/src/main/java/io/codecks/ui/theme/CodecksTheme.kt").read_text()
    lock = (root / "app/src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadActivity.kt").read_text()
    helper = (root / "macHelper/Sources/CodecksMacHelperApp/HelperViews.swift").read_text()
    widget = (root / "app/src/main/java/io/codecks/widget/TrackpadWidgetProvider.kt").read_text()
    overlay_policy = (root / "app/src/main/java/io/codecks/ui/theme/ThemeUiPolicy.kt").read_text()
    require("LocalCodecksSemanticColors" in android and "MaterialTheme(" in android, "Android semantic theme missing")
    require("CodecksTheme" in lock, "lockscreen theme missing")
    require("ThemeSystemSurfaceStore" in widget and "theme.primary" in widget and "theme.content" in widget, "widget semantic roles missing")
    require("environment.overlay" in overlay_policy, "overlay-aware theme policy missing")
    require("Color(nsColor: .windowBackgroundColor)" in helper, "macOS semantic background missing")
    require(helper.count(".foregroundStyle(.secondary)") >= 8, "macOS semantic foreground roles missing")


def validate_receipts() -> list[dict]:
    validations = run_validators()
    signer_result = rehearse()
    require(signer_result["sameKeyAccepted"] is True and signer_result["differentKeyRejected"] is True, "fresh signer rehearsal")
    return validations


def validate_local_receipt(value: dict, current_validations: list[dict] | None = None) -> None:
    schema = json.loads(SCHEMA.read_text())
    validate_schema_node(value, schema, schema, "local-closure")
    require(set(value) == {"schema", "status", "sourceCommit", "sources", "validations", "m20DisposableSigner", "lanes", "limitations", "receiptDigest"}, "receipt shape")
    require(value["schema"] == "codecks.autonomous-maturity.local-closure.v1", "receipt schema")
    require(value["status"] == "PASS_WITH_NOT_RUN", "receipt status")
    require(digest(value) == value["receiptDigest"], "receipt digest")
    require(subprocess.run(["git", "merge-base", "--is-ancestor", value["sourceCommit"], "HEAD"], cwd=ROOT).returncode == 0, "source commit ancestry")
    require([item["path"] for item in value["sources"]] == list(SOURCE_PATHS), "source order")
    for item in value["sources"]:
        require(set(item) == {"path", "sha256"}, "source shape")
        require(item["sha256"] == __import__("hashlib").sha256((ROOT / item["path"]).read_bytes()).hexdigest(), f"source hash {item['path']}")
    require(value["validations"] == (current_validations if current_validations is not None else run_validators()), "validator execution binding")
    signer_result = value["m20DisposableSigner"]
    require(set(signer_result) == {"sameKeyAccepted", "differentKeyRejected", "sameSignerCertificateSha256", "differentSignerCertificateSha256", "publicArtifacts", "apksigner", "privateMaterialPersisted", "productionKeyAccessed", "protectedPackageTouched"}, "signer result shape")
    require(signer_result["sameKeyAccepted"] is True and signer_result["differentKeyRejected"] is True, "signer comparison result")
    require(signer_result["sameSignerCertificateSha256"] != signer_result["differentSignerCertificateSha256"], "signer digests")
    require(all(__import__("re").fullmatch(r"[0-9a-f]{64}", signer_result[key]) for key in ("sameSignerCertificateSha256", "differentSignerCertificateSha256")), "signer result digests")
    sdk, signer = apksigner()
    require(signer_result["apksigner"] == tool_binding(sdk, signer), "apksigner binding")
    artifacts = signer_result["publicArtifacts"]
    require([item["name"] for item in artifacts] == list(PUBLIC_FIXTURE_NAMES), "public signer artifact order")
    for item in artifacts:
        require(set(item) == {"name", "sizeBytes", "sha256"}, "public signer artifact shape")
        payload = (M20_FIXTURE_DIR / item["name"]).read_bytes()
        require(item["sizeBytes"] == len(payload) <= 128 * 1024, "public signer artifact size")
        require(item["sha256"] == __import__("hashlib").sha256(payload).hexdigest(), "public signer artifact hash")
        require(b"PRIVATE KEY" not in payload and b"CODECKS_M20_DISPOSABLE_PASS" not in payload, "private signer material persisted")
    derived = []
    for apk_name, cert_name in (("same-key-a.apk", "same-key-a.cert.txt"), ("same-key-b.apk", "same-key-b.cert.txt"), ("different-key.apk", "different-key.cert.txt")):
        certificate, output = signer_evidence(signer, M20_FIXTURE_DIR / apk_name)
        require(output == (M20_FIXTURE_DIR / cert_name).read_text(), "public certificate output binding")
        derived.append(certificate)
    require(derived[0] == derived[1] and derived[0] != derived[2], "durable signer continuity comparison")
    require(signer_result["sameSignerCertificateSha256"] == derived[0] and signer_result["differentSignerCertificateSha256"] == derived[2], "retained signer digest binding")
    require(all(signer_result[key] is False for key in ("privateMaterialPersisted", "productionKeyAccessed", "protectedPackageTouched")), "signer safety boundary")
    passed = {item["id"] for item in value["validations"]} | {"m09a", "m20_disposable"}
    expected = derive_lanes(passed)
    require(value["lanes"] == expected, "lane binding")
    require(all(item["status"] == "NOT_RUN" for item in value["lanes"] if item["evidence"] in {"RUNTIME", "EXTERNAL", "CPU_AND_DEVICE"}), "external promotion")


def main() -> int:
    try:
        validate_metrics(METRICS.read_text(), json.loads(CENSUS.read_text()), json.loads(BASELINE.read_text()))
        validate_todo(TODO.read_text())
        validate_semantic_platform_split()
        current_validations = validate_receipts()
        validate_local_receipt(json.loads(RECEIPT.read_text()), current_validations)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}")
        return 1
    print("PASS: local maturity closure; external/runtime lanes remain NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
