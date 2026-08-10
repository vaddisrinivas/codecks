#!/usr/bin/env python3
"""Fail-closed validator for autonomous-maturity baseline and source inventory."""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from argparse import ArgumentParser
from collections import Counter
from datetime import datetime
from pathlib import Path

from generate_autonomous_maturity_source_inventory import METHOD, classify, owner, read_source


ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "tasks/test-evidence/autonomous-maturity-m00-baseline.json"
INVENTORY = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-baseline-v1.schema.json"
INVENTORY_SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-source-inventory-v1.schema.json"
HEX_64 = re.compile(r"^[0-9a-f]{64}$")
RFC3339 = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$")
SAFE_PATH = re.compile(r"^(?!/)(?!.*(?:^|/)\.\.(?:/|$))[A-Za-z0-9_.+@/-]+$")
CHECK_KEYS = {
    "id", "status", "argv", "started_at_utc", "finished_at_utc", "exit_code",
    "tool", "environment", "output_sha256", "output_digest_scope",
}
GATE_SPECS_VERSION = "phase0-live-v1"
GATE_SPECS = {
    "source_inventory": ("python3", "tools/evidence/generate_autonomous_maturity_source_inventory.py", "--check"),
    "evidence_schema": ("python3", "tools/evidence/validate_autonomous_maturity_evidence.py", "--structural"),
    "evidence_negative_mutations": (
        "python3", "-m", "unittest", "-q",
        "tools/evidence/test_validate_autonomous_maturity_evidence.py",
        "tools/evidence/test_generate_autonomous_maturity_source_inventory.py",
    ),
    "codebase_map_bound": ("python3", "-c", "from pathlib import Path; assert len(Path('docs/architecture/CODEBASE_MAP.md').read_text().splitlines()) < 1000"),
    "secret_surface": ("python3", "tools/secret_surface_check.py"),
    "no_shrink": ("./scripts/verify_release_no_shrink.sh",),
    "architecture_release_distribution_commercial_manifests_shared_backend": (
        "./gradlew", ":app:validateArchitectureBoundaries", ":app:validateReleaseSurface",
        ":app:validateDistributionMatrix", ":app:validateCommercialDependencyBoundaries",
        ":app:validateCommercialManifests", ":shared:allTests", ":backend:test",
    ),
    "protocol_fixtures": ("python3", "tools/verify_protocol_fixtures.py"),
    "mac_helper_tests": ("swift", "test", "--package-path", "macHelper"),
}
REQUIRED_GATE_IDS = frozenset(GATE_SPECS)
SENSITIVE_VALUE = re.compile(
    r"(?i)(?:bearer\s+[A-Za-z0-9._~+/=-]+|-----BEGIN [A-Z ]*PRIVATE KEY-----|"
    r"(?:api[_-]?key|password|secret|token)\s*[:=]\s*\S+|sk-[A-Za-z0-9_-]{12,}|"
    r"ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})"
)
PRIVATE_OR_UNSAFE_PATH = re.compile(
    r"(?:/(?:Users|home)/|[A-Za-z]:[\\/](?:Users|Documents and Settings)[\\/]|"
    r"file://|(?:^|\s)~[\\/]|\\\\[^\\\s]+\\[^\\\s]+)"
)


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
    if path.is_symlink():
        fail(f"refusing symlinked evidence input: {path.name}")
    return json.loads(path.read_text())


def run(argv: tuple[str, ...], *, cwd: Path = ROOT) -> subprocess.CompletedProcess[str]:
    return subprocess.run(argv, cwd=cwd, capture_output=True, text=True)


def reject_sensitive_values(value: object, field: str = "baseline") -> None:
    if isinstance(value, str):
        if SENSITIVE_VALUE.search(value):
            fail(f"{field}: secret-like value is forbidden")
        if PRIVATE_OR_UNSAFE_PATH.search(value):
            fail(f"{field}: private or unsafe path is forbidden")
    elif isinstance(value, list):
        for index, item in enumerate(value):
            reject_sensitive_values(item, f"{field}[{index}]")
    elif isinstance(value, dict):
        for key, item in value.items():
            reject_sensitive_values(item, f"{field}.{key}")


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
        raw = read_source(ROOT, path)
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
    if validation.get("status") != "STRUCTURE_VALID":
        fail("receipt_validation.status must be STRUCTURE_VALID")
    if validation.get("validator") != "tools/evidence/validate_autonomous_maturity_evidence.py":
        fail("receipt_validation.validator mismatch")

    maturity = data.get("maturity_assessment", {})
    if set(maturity) != {"status", "completed_milestones", "note"}:
        fail("maturity_assessment: unknown or missing field")
    if maturity.get("status") not in {"NOT_ASSESSED", "AUTONOMOUS_PROXY_INCOMPLETE", "EXTERNAL_EVIDENCE_REQUIRED"}:
        fail("maturity_assessment cannot claim GA or completion")

    if data["gate_specs_version"] != GATE_SPECS_VERSION:
        fail("gate_specs_version mismatch")

    check_ids: set[str] = set()
    for index, check in enumerate(data.get("fresh_checks", [])):
        if set(check) != CHECK_KEYS:
            fail(f"fresh_checks[{index}]: unknown or missing field")
        if check["status"] not in {"RECORDED_SUCCESS", "RECORDED_FAILURE"}:
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
        if check["status"] != ("RECORDED_SUCCESS" if check["exit_code"] == 0 else "RECORDED_FAILURE"):
            fail(f"fresh_checks[{index}]: status/exit_code mismatch")
        if not HEX_64.fullmatch(check["output_sha256"]):
            fail(f"fresh_checks[{index}]: invalid output_sha256")
        expected_argv = list(GATE_SPECS.get(check["id"], ()))
        if check["argv"] != expected_argv:
            fail(f"fresh_checks[{index}]: argv does not match allowlisted gate spec")
        if check["output_digest_scope"] != "RAW_COMBINED_STREAM_NOT_RETAINED_HISTORICAL_ONLY":
            fail(f"fresh_checks[{index}]: invalid output digest scope")
    if check_ids != REQUIRED_GATE_IDS:
        fail(f"fresh_checks: required gate IDs mismatch: {sorted(REQUIRED_GATE_IDS - check_ids)}")
    if any(check["status"] != "RECORDED_SUCCESS" for check in data["fresh_checks"]):
        fail("structure cannot be valid while a recorded check is a failure")

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

    reject_sensitive_values(data)
    source_inventory = data.get("source_inventory", {})
    if source_inventory.get("path") != "tasks/test-evidence/autonomous-maturity-source-inventory.json":
        fail("source_inventory.path mismatch")
    if source_inventory.get("sha256") != hashlib.sha256(inventory_raw).hexdigest():
        fail("source_inventory.sha256 mismatch")
    if source_inventory.get("summary") != inventory.get("summary"):
        fail("source_inventory.summary mismatch")

    critical_paths = [item["path"] for item in data["critical_files"]]
    if critical_paths != sorted(set(critical_paths)):
        fail("critical_files paths must be unique and sorted")
    for item in data["critical_files"]:
        if not SAFE_PATH.fullmatch(item["path"]):
            fail(f"critical_files unsafe path: {item['path']}")
        try:
            raw = read_source(ROOT, item["path"])
        except (OSError, ValueError) as exc:
            raise ValueError(f"critical_files unsafe: {item['path']}") from exc
        if hashlib.sha256(raw).hexdigest() != item["sha256"]:
            fail(f"critical_files digest mismatch: {item['path']}")


def validate_evidence(baseline: dict, inventory: dict, inventory_raw: bytes) -> None:
    validate_inventory(inventory)
    validate_baseline(baseline, inventory, inventory_raw)


def checked_output(argv: tuple[str, ...], *, cwd: Path = ROOT) -> str:
    result = run(argv, cwd=cwd)
    if result.returncode != 0:
        fail(f"live command failed: {argv!r}: {(result.stdout + result.stderr)[-1000:]}")
    return result.stdout


def attest_git_and_critical_files(data: dict) -> None:
    if checked_output(("git", "status", "--porcelain=v1")).strip():
        fail("live attestation requires a clean worktree")
    binding = data["commit_binding"]
    head = checked_output(("git", "rev-parse", "HEAD")).strip()
    parent = checked_output(("git", "rev-parse", "HEAD^")).strip()
    if parent != data["source"]["evidence_parent_sha"]:
        fail("live HEAD parent does not match evidence_parent_sha")
    changed = sorted(checked_output(("git", "diff-tree", "--no-commit-id", "--name-only", "-r", head)).splitlines())
    if changed != binding["allowed_commit_paths"]:
        fail("live evidence commit paths do not match allowlist")
    if binding["kind"] != "IMMEDIATE_PARENT_PLUS_ALLOWED_DIFF_AND_CRITICAL_DIGESTS" or binding["self_hash_claimed"]:
        fail("invalid non-self-referential commit binding")

    source = data["source"]
    checks = {
        "implementation baseline": (("git", "rev-parse", source["implementation_baseline_ref"]), source["implementation_baseline_sha"]),
        "origin main": (("git", "rev-parse", "origin/main"), source["origin_main_sha"]),
        "release tag object": (("git", "rev-parse", source["release_tag"]), source["release_tag_object_sha"]),
        "release tag commit": (("git", "rev-parse", f"{source['release_tag']}^{{}}"), source["release_tag_commit_sha"]),
    }
    for label, (argv, expected) in checks.items():
        if checked_output(argv).strip() != expected:
            fail(f"live {label} mismatch")
    for ancestry in source["dependency_pr_ancestry"]:
        tag_result = run(("git", "merge-base", "--is-ancestor", ancestry["merge_sha"], source["release_tag_commit_sha"]))
        baseline_result = run(("git", "merge-base", "--is-ancestor", ancestry["merge_sha"], source["implementation_baseline_sha"]))
        if (tag_result.returncode == 0) != ancestry["ancestor_of_v0.1.37"]:
            fail(f"live tag ancestry mismatch for PR {ancestry['pr']}")
        if (baseline_result.returncode == 0) != ancestry["ancestor_of_baseline"]:
            fail(f"live baseline ancestry mismatch for PR {ancestry['pr']}")


def sdk_tool(name: str) -> Path:
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    roots = [Path(sdk)] if sdk else []
    roots.append(Path.home() / "Library/Android/sdk")
    candidates = []
    for root in roots:
        candidates.extend(root.glob(f"build-tools/*/{name}"))
    if not candidates:
        fail(f"Android SDK tool unavailable: {name}")
    return sorted(candidates)[-1]


def download_public_assets(tag: str, repository: str, destination: Path, apk_name: str) -> None:
    checked_output((
        "gh", "release", "download", tag, "--repo", repository,
        "--pattern", apk_name, "--pattern", "SHA256SUMS.txt", "--dir", str(destination),
    ))


def attest_public_artifact(data: dict) -> None:
    release = data["release_artifact"]
    repository = data["source"]["repository"].removeprefix("https://github.com/")
    metadata_raw = checked_output((
        "gh", "release", "view", data["source"]["release_tag"], "--repo", repository,
        "--json", "tagName,isDraft,isPrerelease,publishedAt,assets,url",
    ))
    metadata = json.loads(metadata_raw)
    if metadata["tagName"] != data["source"]["release_tag"] or metadata["url"] != release["source"]:
        fail("public release identity mismatch")
    if metadata["isDraft"] != release["draft"] or metadata["isPrerelease"] != release["prerelease"]:
        fail("public release state mismatch")
    if metadata["publishedAt"] != release["published_at"]:
        fail("public release timestamp mismatch")
    assets = {asset["name"]: asset for asset in metadata["assets"]}
    apk_asset = assets.get(release["apk_name"])
    checksum_asset = assets.get("SHA256SUMS.txt")
    if not apk_asset or not checksum_asset:
        fail("public release assets missing")
    if apk_asset["size"] != release["apk_size_bytes"] or apk_asset.get("digest") != f"sha256:{release['apk_sha256']}":
        fail("public APK metadata mismatch")
    if checksum_asset.get("digest") != f"sha256:{release['checksum_file_sha256']}":
        fail("public checksum metadata mismatch")

    with tempfile.TemporaryDirectory() as directory:
        destination = Path(directory)
        apk = destination / release["apk_name"]
        checksums = destination / "SHA256SUMS.txt"
        download_public_assets(data["source"]["release_tag"], repository, destination, release["apk_name"])
        if hashlib.sha256(apk.read_bytes()).hexdigest() != release["apk_sha256"]:
            fail("downloaded APK digest mismatch")
        if hashlib.sha256(checksums.read_bytes()).hexdigest() != release["checksum_file_sha256"]:
            fail("downloaded checksum-file digest mismatch")
        checksum_text = checksums.read_text()
        if f"{release['apk_sha256']}  {release['apk_name']}" not in checksum_text:
            fail("checksum file does not bind APK")

        signer = checked_output((str(sdk_tool("apksigner")), "verify", "--verbose", "--print-certs", str(apk)))
        if "Verified using v2 scheme (APK Signature Scheme v2): true" not in signer:
            fail("public APK lacks verified v2 signature")
        certificate = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", signer, re.IGNORECASE)
        if not certificate or certificate.group(1).lower() != release["signer_certificate_sha256"]:
            fail("public APK signer mismatch")
        badging = checked_output((str(sdk_tool("aapt")), "dump", "badging", str(apk)))
        expected_package = (
            f"package: name='{release['package']}' versionCode='{release['version_code']}' "
            f"versionName='{release['version_name']}'"
        )
        if expected_package not in badging:
            fail("public APK package/version metadata mismatch")
        checked_output(("./scripts/verify_release_no_shrink.sh", str(apk)))


def attest_live(data: dict) -> None:
    attest_git_and_critical_files(data)
    for gate_id, argv in GATE_SPECS.items():
        if gate_id == "evidence_schema":
            continue
        checked_output(argv)
    attest_public_artifact(data)
    if checked_output(("git", "status", "--porcelain=v1")).strip():
        fail("live gates changed the worktree")


def main() -> int:
    parser = ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--structural", action="store_true")
    mode.add_argument("--live", action="store_true")
    args = parser.parse_args()
    try:
        inventory = load(INVENTORY)
        baseline = load(BASELINE)
        if not isinstance(inventory, dict) or not isinstance(baseline, dict):
            fail("root JSON values must be objects")
        validate_evidence(baseline, inventory, INVENTORY.read_bytes())
        if args.live:
            attest_live(baseline)
    except (OSError, json.JSONDecodeError, ValueError, KeyError, UnicodeDecodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    if args.live:
        print("PASS: live repository, gates, and public release artifact attested")
    else:
        print("STRUCTURE_VALID: receipt shape only; run --live for current proof")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
