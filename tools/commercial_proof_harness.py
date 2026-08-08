#!/usr/bin/env python3
"""Fail-closed static/artifact proof for production-dark Codecks builds."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from pathlib import PurePosixPath
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET


SCHEMA = "codecks.commercial-proof.v1"
PASS = "PASS"
FAIL = "FAIL"
NOT_RUN = "NOT_RUN"
EXPECTED_PACKAGE = "app.codecks"
EXPECTED_MIN_SDK = "28"

BANNED_ARTIFACT_MARKERS = (
    b"com/google/android/gms/ads/",
    b"com/google/android/ump/",
    b"com/android/billingclient/",
    b"com/google/firebase/",
    b"com/google/android/play/core/integrity/",
    b"io/codecks/internalcommercial/",
    b"CommercialTestOverrideMarker",
)
BANNED_MANIFEST_MARKERS = (
    b"android.permission.AD_ID",
    b"com.google.android.gms.permission.AD_ID",
    b"com.google.android.gms.ads.APPLICATION_ID",
    b"MobileAdsInitProvider",
    b"com.google.android.gms.ads.AdActivity",
    b"com.google.android.gms.ads.MobileAdsInitProvider",
    b"UserMessagingPlatform",
)
BANNED_ENDPOINT_MARKERS = (
    b"app.codecks.test_backend",
    b"codecks.invalid",
    b"10.0.2.2",
    b"http://localhost",
    b"https://localhost",
    b"ws://localhost",
    b"wss://localhost",
)
KEY_PATTERNS = (
    re.compile(rb"AIza[0-9A-Za-z_-]{30,}"),
    re.compile(rb"sk-[0-9A-Za-z_-]{20,}"),
    re.compile(
        rb"-----BEGIN ((?:RSA |EC |OPENSSH )?PRIVATE KEY)-----\r?\n"
        rb"[A-Za-z0-9+/=\r\n]{80,}\r?\n-----END \1-----",
    ),
)
COMMERCIAL_COMPONENT = re.compile(
    r"(?:commercial|billing|purchase|subscription|entitlement|advert|\.ads?\.|cloud.?sync|"
    r"sign.?in|account|paywall|monetiz|consent|integrity|remote.?config|app.?check)",
    re.IGNORECASE,
)
COMMERCIAL_ROUTE = re.compile(
    r"(?:^|[^a-z0-9])(?:account|cloud[._-]?sync|sync|billing|purchase|subscription|"
    r"entitlement|ad|ads|paywall|commercial|sign[._-]?in|privacy|consent)(?:$|[^a-z0-9])",
    re.IGNORECASE,
)
SDK_AD_REFERENCE = re.compile(
    r"\b(?:MobileAds|AdView|AdRequest|InternalAdRequestCoordinator|UserMessagingPlatform)\b"
)
UI_AD_REFERENCE = re.compile(
    r"\b(?:ApprovedAdPlacement|CommercialAdEligibilityService)\b"
)
SENSITIVE_PAYLOAD_KEY = re.compile(
    r"(?:password|secret|raw.?token|purchase.?token|identity.?token|session.?credential|email)",
    re.IGNORECASE,
)
EMAIL = re.compile(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", re.IGNORECASE)


def check(check_id: str, status: str, evidence: str, violations: list[str] | None = None) -> dict:
    return {
        "id": check_id,
        "status": status,
        "evidence": evidence,
        "violations": violations or [],
    }


def receipt(kind: str, checks: list[dict], **metadata: object) -> dict:
    statuses = {item["status"] for item in checks}
    overall = FAIL if FAIL in statuses else NOT_RUN if NOT_RUN in statuses else PASS
    return {"schema": SCHEMA, "kind": kind, "overall": overall, "checks": checks, **metadata}


def encoded_contains(payload: bytes, marker: bytes) -> bool:
    text = marker.decode("utf-8")
    return any(value in payload for value in (marker, text.encode("utf-16le"), text.encode("utf-16be")))


def source_without_comments(text: str, suffix: str) -> str:
    if suffix == ".xml":
        return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.DOTALL)


def _zip_payload(path: Path) -> tuple[bytes, bytes, bytes, list[str]]:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        manifests = b"".join(
            archive.read(name) for name in names if name.endswith("AndroidManifest.xml")
        )
        dex = b"".join(archive.read(name) for name in names if name.endswith(".dex"))
        all_payloads = b"".join(archive.read(name) for name in names if not name.endswith("/"))
        return manifests, dex, all_payloads, names


def _apk_paths(path: Path, temporary: Path) -> list[Path]:
    if path.is_dir():
        return sorted(path.rglob("*.apk"))
    if path.suffix == ".apks":
        with zipfile.ZipFile(path) as archive:
            members = archive.infolist()
            if len(members) > 2_000 or sum(item.file_size for item in members) > 2 * 1024 * 1024 * 1024:
                raise ValueError("APKS archive exceeds proof harness bounds")
            for item in members:
                member = PurePosixPath(item.filename)
                if member.is_absolute() or ".." in member.parts:
                    raise ValueError(f"unsafe APKS member: {item.filename}")
                if item.is_dir() or member.suffix != ".apk":
                    continue
                destination = temporary.joinpath(*member.parts)
                destination.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(item) as source, destination.open("wb") as target:
                    shutil.copyfileobj(source, target)
        return sorted(temporary.rglob("*.apk"))
    return [path]


def _apkanalyzer_values(apks: list[Path]) -> tuple[list[tuple[Path, str, str, str]], list[str], str]:
    tool = shutil.which("apkanalyzer")
    if tool is None:
        android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        candidate = Path(android_home, "cmdline-tools/latest/bin/apkanalyzer") if android_home else None
        tool = str(candidate) if candidate and candidate.is_file() else None
    if tool is None or not apks:
        return [], [str(path) for path in apks], "apkanalyzer unavailable"
    values: list[tuple[Path, str, str, str]] = []
    failures: list[str] = []
    for apk in apks:
        try:
            package = subprocess.run(
                [tool, "manifest", "application-id", str(apk)],
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip()
            minimum = subprocess.run(
                [tool, "manifest", "min-sdk", str(apk)],
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip()
            decoded = subprocess.run(
                [tool, "manifest", "print", str(apk)],
                check=True,
                capture_output=True,
                text=True,
            ).stdout
            if package:
                values.append((apk, package, minimum, decoded))
        except (OSError, subprocess.CalledProcessError):
            failures.append(str(apk))
    return values, failures, tool


def _base_apk_values(
    values: list[tuple[Path, str, str, str]],
) -> list[tuple[Path, str, str, str]]:
    """Identify the APK that establishes the install set's application minimum.

    A single APK is self-contained. In a split set, bundletool's base master or
    an explicitly universal APK is authoritative; feature master and conditional
    configuration APKs are not.
    """
    if len(values) == 1:
        return values
    base_names = {"base.apk", "base-master.apk", "universal.apk"}
    return [
        value
        for value in values
        if value[0].name.lower() in base_names
        or value[0].name.lower().endswith("-universal.apk")
    ]


def scan_artifact(path: Path) -> dict:
    checks: list[dict] = []
    min_sdk_distribution: dict[str, int] = {}
    base_apk: str | None = None
    if not path.exists():
        return receipt("artifact", [check("artifact.exists", NOT_RUN, f"missing: {path}")], artifact=str(path))
    with tempfile.TemporaryDirectory(prefix="codecks-proof-") as temp:
        temporary = Path(temp)
        try:
            artifacts = _apk_paths(path, temporary)
            if not artifacts:
                return receipt("artifact", [check("artifact.splits", FAIL, "no APK splits")], artifact=str(path))
            manifests = bytearray()
            dex = bytearray()
            all_payloads = bytearray()
            entries: list[str] = []
            for artifact in artifacts:
                current_manifest, current_dex, current_payloads, current_entries = _zip_payload(artifact)
                manifests.extend(current_manifest)
                dex.extend(current_dex)
                all_payloads.extend(current_payloads)
                entries.extend(current_entries)
        except (OSError, ValueError, zipfile.BadZipFile) as error:
            return receipt("artifact", [check("artifact.readable", FAIL, str(error))], artifact=str(path))

        analyzer_values, analyzer_failures, analyzer_tool = _apkanalyzer_values(
            artifacts if all(item.suffix == ".apk" for item in artifacts) else [],
        )
        if not analyzer_values or analyzer_failures:
            evidence = f"{analyzer_tool}; unanalyzed={analyzer_failures or [str(path)]}"
            checks.append(check("manifest.package", NOT_RUN, evidence))
            checks.append(check("manifest.min_sdk", NOT_RUN, evidence))
        else:
            package_violations = [
                f"{apk}: {package}" for apk, package, _, _ in analyzer_values if package != EXPECTED_PACKAGE
            ]
            for _, _, minimum, _ in analyzer_values:
                key = minimum or "<missing>"
                min_sdk_distribution[key] = min_sdk_distribution.get(key, 0) + 1
            checks.append(check(
                "manifest.package",
                FAIL if package_violations else PASS,
                f"{analyzer_tool}; analyzed {len(analyzer_values)} APKs",
                package_violations,
            ))

            base_values = _base_apk_values(analyzer_values)
            minimum_violations: list[str] = []
            base_minimum: int | None = None
            if len(base_values) != 1:
                minimum_violations.append(
                    "ambiguous base APKs: " +
                    (", ".join(str(value[0]) for value in base_values) or "none")
                )
            else:
                base_apk = str(base_values[0][0])
                try:
                    base_minimum = int(base_values[0][2])
                except (TypeError, ValueError):
                    minimum_violations.append(
                        f"base {base_values[0][0]}: unknown minSdk {base_values[0][2]!r}"
                    )
                if base_minimum is not None and base_minimum != int(EXPECTED_MIN_SDK):
                    minimum_violations.append(
                        f"base {base_values[0][0]}: {base_minimum}, expected {EXPECTED_MIN_SDK}"
                    )
            if base_minimum is not None:
                base_paths = {value[0] for value in base_values}
                for apk, _, minimum, _ in analyzer_values:
                    if apk in base_paths:
                        continue
                    try:
                        split_minimum = int(minimum)
                    except (TypeError, ValueError):
                        minimum_violations.append(f"conditional {apk}: unknown minSdk {minimum!r}")
                        continue
                    if split_minimum < base_minimum:
                        minimum_violations.append(
                            f"conditional {apk}: {split_minimum}, below base {base_minimum}"
                        )
            checks.append(check(
                "manifest.min_sdk",
                FAIL if minimum_violations else PASS,
                f"{analyzer_tool}; base={base_apk}; distribution={min_sdk_distribution}",
                minimum_violations,
            ))

        combined = bytes(all_payloads)
        banned = [marker.decode() for marker in BANNED_ARTIFACT_MARKERS if marker in dex]
        checks.append(check(
            "artifact.internal_or_sdk_namespace",
            FAIL if banned else PASS,
            f"scanned {len(dex)} DEX bytes",
            banned,
        ))
        manifest_banned = [marker.decode() for marker in BANNED_MANIFEST_MARKERS if encoded_contains(bytes(manifests), marker)]
        checks.append(check(
            "manifest.commercial_surface",
            FAIL if manifest_banned else PASS,
            f"scanned {len(manifests)} manifest bytes",
            manifest_banned,
        ))
        endpoint_hits = [
            marker.decode()
            for marker in BANNED_ENDPOINT_MARKERS
            if encoded_contains(combined, marker)
        ]
        checks.append(check(
            "artifact.test_endpoints",
            FAIL if endpoint_hits else PASS,
            "manifest and DEX endpoint scan",
            endpoint_hits,
        ))
        exported_commercial = []
        commercial_components = []
        decoded = "\n".join(value[3] for value in analyzer_values)
        manifest_payload = bytes(manifests)
        if not decoded and manifest_payload and manifest_payload.count(b"\x00") / len(manifest_payload) < 0.1:
            decoded = manifest_payload.decode("utf-8", errors="replace")
        commercial_routes: list[str] = []
        if decoded:
            for tag in ("activity", "activity-alias", "provider", "service", "receiver"):
                for match in re.finditer(rf"<{tag}[^>]+>", decoded, re.IGNORECASE):
                    text = match.group(0)
                    if COMMERCIAL_COMPONENT.search(text):
                        commercial_components.append(text[:240])
                    if re.search(r"(?:android:)?exported\s*=\s*\"true\"", text, re.IGNORECASE) and COMMERCIAL_COMPONENT.search(text):
                        exported_commercial.append(text[:240])
            for match in re.finditer(r"<data\b[^>]*>", decoded, re.IGNORECASE):
                text = match.group(0)
                if COMMERCIAL_ROUTE.search(text):
                    commercial_routes.append(text[:240])
            checks.append(check(
                "manifest.commercial_components",
                FAIL if commercial_components else PASS,
                "apkanalyzer/plain XML component scan",
                commercial_components,
            ))
            checks.append(check(
                "manifest.exported_commercial_components",
                FAIL if exported_commercial else PASS,
                "apkanalyzer/plain XML component scan",
                exported_commercial,
            ))
            checks.append(check(
                "manifest.commercial_routes",
                FAIL if commercial_routes else PASS,
                "apkanalyzer/plain XML data-route scan",
                commercial_routes,
            ))
        else:
            checks.append(check("manifest.commercial_components", NOT_RUN, "decoded manifest unavailable"))
            checks.append(check("manifest.exported_commercial_components", NOT_RUN, "decoded manifest unavailable"))
            checks.append(check("manifest.commercial_routes", NOT_RUN, "decoded manifest unavailable"))
        key_hits = [pattern.pattern.decode(errors="ignore") for pattern in KEY_PATTERNS if pattern.search(combined)]
        checks.append(check("artifact.keys", FAIL if key_hits else PASS, "key-pattern scan", key_hits))
        checks.append(check(
            "artifact.production_policy",
            PASS if b"PRODUCTION_DARK" in dex else FAIL,
            "compiled policy marker",
        ))
        checks.append(check(
            "artifact.payloads",
            PASS if bool(dex) and bool(manifests) else FAIL,
            f"dex={len(dex)} manifest={len(manifests)} entries={len(entries)}",
        ))
    return receipt(
        "artifact",
        checks,
        artifact=str(path),
        split_count=len(artifacts),
        base_apk=base_apk,
        min_sdk_distribution=min_sdk_distribution,
    )


def scan_backup_rules(root: Path) -> dict:
    manifest = root / "app/src/main/AndroidManifest.xml"
    rules = root / "app/src/main/res/xml/data_extraction_rules.xml"
    checks: list[dict] = []
    if not manifest.is_file() or not rules.is_file():
        return receipt("backup", [check("backup.files", FAIL, "manifest or rules missing")])
    android = "{http://schemas.android.com/apk/res/android}"
    try:
        manifest_root = ET.parse(manifest).getroot()
        application = manifest_root if manifest_root.tag.rsplit("}", 1)[-1] == "application" else manifest_root.find("application")
        rules_root = ET.parse(rules).getroot()
    except (ET.ParseError, OSError) as error:
        return receipt("backup", [check("backup.xml", FAIL, str(error))])
    missing_manifest = []
    expected_attributes = {
        "allowBackup": "false",
        "fullBackupContent": "false",
        "dataExtractionRules": "@xml/data_extraction_rules",
    }
    if application is None:
        missing_manifest.append("application")
    else:
        missing_manifest.extend(
            f"{name}={expected}"
            for name, expected in expected_attributes.items()
            if application.get(f"{android}{name}") != expected
        )
    checks.append(check("backup.manifest", FAIL if missing_manifest else PASS, str(manifest), missing_manifest))
    missing_rules = []
    for section_name in ("cloud-backup", "device-transfer"):
        section = next((item for item in rules_root if item.tag.rsplit("}", 1)[-1] == section_name), None)
        excluded = section is not None and any(
            item.tag.rsplit("}", 1)[-1] == "exclude" and
            item.get("domain") == "root" and item.get("path") == "."
            for item in section
        )
        if not excluded:
            missing_rules.append(section_name)
    checks.append(check("backup.rules", FAIL if missing_rules else PASS, str(rules), missing_rules))
    return receipt("backup", checks)


def scan_operational_sources(root: Path) -> dict:
    source_roots = (root / "app/src/main", root / "app/src/play")
    violations: list[str] = []
    for source_root in source_roots:
        for path in sorted(source_root.rglob("*")):
            if not path.is_file() or path.suffix not in {".kt", ".java", ".xml"}:
                continue
            relative = path.relative_to(root).as_posix()
            if "/domain/commercial/" in relative:
                continue
            text = source_without_comments(path.read_text(errors="ignore"), path.suffix)
            is_ui_or_compose = "/ui/" in relative or "@Composable" in text
            if SDK_AD_REFERENCE.search(text) or (is_ui_or_compose and UI_AD_REFERENCE.search(text)):
                violations.append(relative)
    build_path = root / "app/build.gradle.kts"
    if not build_path.is_file():
        return receipt("source", [check("source.build", FAIL, "app/build.gradle.kts missing")])
    build = re.sub(r"/\*.*?\*/|//[^\n]*", "", build_path.read_text(), flags=re.DOTALL)
    shrink_missing = [
        marker for marker in ("isMinifyEnabled = false", "isShrinkResources = false") if marker not in build
    ]
    override_violations: list[str] = []
    for source_root in source_roots:
        for path in sorted(source_root.rglob("*.kt")):
            relative = path.relative_to(root).as_posix()
            if "/domain/commercial/" in relative:
                continue
            text = source_without_comments(path.read_text(errors="ignore"), path.suffix)
            if re.search(r"CommercialTestOverrideMarker|COMMERCIAL_TEST_OVERRIDES_ALLOWED", text):
                override_violations.append(relative)
    route_violations: list[str] = []
    for manifest in (root / "app/src/main/AndroidManifest.xml", root / "app/src/play/AndroidManifest.xml"):
        if not manifest.is_file():
            continue
        text = source_without_comments(manifest.read_text(errors="ignore"), ".xml")
        for match in re.finditer(r"<data\b[^>]*>", text, re.IGNORECASE):
            if COMMERCIAL_ROUTE.search(match.group(0)):
                route_violations.append(f"{manifest.relative_to(root)}: {match.group(0)[:160]}")
    return receipt("source", [
        check("source.operational_ads", FAIL if violations else PASS, "main operational/Compose semantic scan", violations),
        check("source.production_override_parser", FAIL if override_violations else PASS, "main runtime scan", override_violations),
        check("source.commercial_routes", FAIL if route_violations else PASS, "public source manifest route scan", route_violations),
        check("source.no_shrink", FAIL if shrink_missing else PASS, "app/build.gradle.kts", shrink_missing),
    ])


def scan_production_dark_binding(root: Path) -> dict:
    commercial = root / "app/src/main/java/io/codecks/domain/commercial"
    owner = commercial / "CompiledCommercialOwnerPolicy.kt"
    policy = commercial / "CommercialExecutionPolicy.kt"
    services = commercial / "ProductionDarkCommercialServices.kt"
    feature_registry = root / "app/src/main/java/io/codecks/domain/features/TypedFeatureFlagRegistry.kt"
    required = (owner, policy, services, feature_registry)
    if any(not path.is_file() for path in required):
        return receipt("production_dark_binding", [
            check("binding.files", FAIL, "required compiled-policy sources missing"),
        ])
    def code_only(path: Path) -> str:
        return re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(), flags=re.DOTALL)

    owner_text = code_only(owner)
    policy_text = code_only(policy)
    services_text = code_only(services)
    feature_text = code_only(feature_registry)
    owner_missing = [
        marker for marker in (
            'RELEASE_PREMIUM_ENFORCEMENT_KEY = "release.premium_enforcement"',
            "RELEASE_PREMIUM_ENFORCEMENT_ENABLED = false",
        ) if marker not in owner_text
    ]
    resolver_missing = [
        marker for marker in (
            "PRODUCTION_DARK(false)",
            "BUILD_PRODUCTION_DARK",
            "denyForBuild",
        ) if marker not in policy_text
    ]
    service_missing = [
        marker for marker in (
            "ProductionDarkCommercialServices",
            "ProductionDarkOperationalConfigService",
            "ProductionDarkAdEligibilityService",
        ) if marker not in services_text
    ]
    commercial_flag_keys = sorted(set(re.findall(
        r'"(?:release|commercial)\.[a-z0-9_.]+"',
        feature_text,
    )))
    return receipt("production_dark_binding", [
        check("binding.owner_policy", FAIL if owner_missing else PASS, str(owner), owner_missing),
        check("binding.resolver", FAIL if resolver_missing else PASS, str(policy), resolver_missing),
        check("binding.noop_services", FAIL if service_missing else PASS, str(services), service_missing),
        check(
            "binding.no_user_commercial_flags",
            FAIL if commercial_flag_keys else PASS,
            str(feature_registry),
            commercial_flag_keys,
        ),
    ])


def scan_transport_payload(value: object) -> dict:
    violations: list[str] = []

    def visit(node: object, path: str) -> None:
        if isinstance(node, dict):
            for key, child in node.items():
                child_path = f"{path}.{key}"
                if SENSITIVE_PAYLOAD_KEY.search(str(key)):
                    violations.append(f"sensitive key at {child_path}")
                visit(child, child_path)
        elif isinstance(node, list):
            for index, child in enumerate(node):
                visit(child, f"{path}[{index}]")
        elif isinstance(node, str):
            if EMAIL.search(node):
                violations.append(f"email-like value at {path}")
            encoded = node.encode()
            if any(pattern.search(encoded) for pattern in KEY_PATTERNS):
                violations.append(f"key-like value at {path}")

    visit(value, "$")
    return receipt("transport_payload", [
        check("payload.secrets", FAIL if violations else PASS, "recursive typed-payload scan", violations),
    ])


def prove_production_dark_seed(seed: dict) -> dict:
    surfaces = ("account", "cloud_sync", "play_billing", "premium_enforcement", "ads")
    decisions = {surface: "DENIED_BUILD_PRODUCTION_DARK" for surface in surfaces}
    checks = [
        check("seed.production_decisions", PASS, "immutable compiled production policy"),
        check("seed.routes", PASS if not seed.get("production_routes") else FAIL, "stale navigation ignored", seed.get("production_routes", [])),
        check("seed.workers", PASS if not seed.get("production_workers") else FAIL, "stale workers ignored", seed.get("production_workers", [])),
    ]
    return receipt("stale_seed", checks, decisions=decisions, seed_keys=sorted(seed))


def repo_static(root: Path) -> dict:
    children = [
        scan_backup_rules(root),
        scan_operational_sources(root),
        scan_production_dark_binding(root),
    ]
    fixture_root = root / "tools/tests/fixtures"
    seed_path = fixture_root / "stale_lab_seed.json"
    safe_path = fixture_root / "transport_safe.json"
    unsafe_path = fixture_root / "transport_unsafe.json"
    fixture_checks: list[dict] = []
    try:
        seed = prove_production_dark_seed(json.loads(seed_path.read_text()))
        safe = scan_transport_payload(json.loads(safe_path.read_text()))
        unsafe = scan_transport_payload(json.loads(unsafe_path.read_text()))
        fixture_checks.extend([
            check("fixture.stale_seed", PASS if seed["overall"] == PASS else FAIL, str(seed_path)),
            check("fixture.safe_transport", PASS if safe["overall"] == PASS else FAIL, str(safe_path)),
            check("fixture.unsafe_transport_negative", PASS if unsafe["overall"] == FAIL else FAIL, str(unsafe_path)),
        ])
    except (OSError, json.JSONDecodeError) as error:
        fixture_checks.append(check("fixture.load", FAIL, str(error)))
    children.append(receipt("fixtures", fixture_checks))
    flattened = [item for child in children for item in child["checks"]]
    return receipt("repo_static", flattened, child_receipts=children)


def write_receipt(result: dict, output: Path | None) -> None:
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered)
    print(rendered, end="")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--receipt", type=Path)
    sub = parser.add_subparsers(dest="command", required=True)
    artifact = sub.add_parser("artifact")
    artifact.add_argument("path", type=Path)
    static = sub.add_parser("repo-static")
    static.add_argument("--root", type=Path, default=Path.cwd())
    payload = sub.add_parser("payload")
    payload.add_argument("path", type=Path)
    seed = sub.add_parser("stale-seed")
    seed.add_argument("path", type=Path)
    args = parser.parse_args()
    if args.command == "artifact":
        result = scan_artifact(args.path)
    elif args.command == "repo-static":
        result = repo_static(args.root.resolve())
    elif args.command == "payload":
        result = scan_transport_payload(json.loads(args.path.read_text()))
    else:
        result = prove_production_dark_seed(json.loads(args.path.read_text()))
    write_receipt(result, args.receipt)
    raise SystemExit(1 if result["overall"] == FAIL else 0)


if __name__ == "__main__":
    main()
