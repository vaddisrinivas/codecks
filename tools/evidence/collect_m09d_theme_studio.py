#!/usr/bin/env python3
"""Capture and bind the bounded M09D Theme Studio managed-device proof."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ID = "codecks.m09d.theme-studio-managed.v1"
CLASS_NAME = "io.codecks.ui.theme.ThemeStudioInstrumentedTest"
METHODS = {
    "allPresetsAndGeneratedCustomRenderAtTwoHundredPercentText",
    "iconPackSelectorUsesRadioSelectionAndFortyEightDpTargets",
    "keyImportDialogUsesCurrentTheme",
    "lockscreenPolicyDisablesChangedThemeApply",
    "m09dProductionRepositoryLifecycleSavesRenamesLoadsAndDeletesNamedTheme",
    "namedLibraryUiSavesLoadsAndRequiresDeleteConfirmation",
    "overlayAndLockscreenPoliciesDisableAtomicApply",
    "persistedThemeRepairsSystemSurfaceMirrorAfterRepositoryRecreation",
    "presetCardsExposeFullRadioSemanticsAndNonOverlappingTargetsAtLargeText",
    "rawHexEditorIsCollapsedUntilAdvancedIsRequested",
    "rtlLandscapePolicyOverrideBindsActualPhoneOrTabletWindow",
    "v1LibraryMigratesAtomicallyAndCorruptBytesStayQuarantinedUntilReset",
    "widgetAndNotificationSurfaceStoreKeepsOpaqueThemeColors",
}
SOURCE_COMMIT = "28b3e53613b8c0cd189ba58f4a673aafbed653b2"
C1D_REVIEWED_COMMIT = "6d04c51c19c8cc64becfc80c950eb53d558b68c2"
SOURCE_PATHS = (
    ".gitignore",
    "app/build.gradle.kts",
    "app/src/androidTest/java/io/codecks/ui/theme/ThemeStudioInstrumentedTest.kt",
    "app/src/main/java/io/codecks/domain/icons/DeckIconCatalog.kt",
    "app/src/main/java/io/codecks/ui/icons/DeckIconResolver.kt",
    "app/src/main/java/io/codecks/ui/settings/SettingsControlSections.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeLibraryCodec.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeScheme.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeSettingsRepository.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeStudioPanel.kt",
    "app/src/test/java/io/codecks/domain/icons/DeckIconCatalogTest.kt",
    "app/src/test/java/io/codecks/ui/icons/RoundedDeckIconResolverTest.kt",
    "app/src/test/java/io/codecks/ui/settings/DeckStyleGalleryPolicyTest.kt",
    "app/src/test/java/io/codecks/ui/theme/ThemeLibraryCodecTest.kt",
    "app/src/test/java/io/codecks/ui/theme/ThemeSchemeTest.kt",
    "docs/ux/THEME_STUDIO_LIBRARY.md",
    "tools/evidence/collect_m09d_theme_studio.py",
    "tools/evidence/validate_m09d_theme_studio.py",
    "tools/evidence/test_m09d_theme_studio.py",
    "tools/evidence/schemas/codecks-m09d-theme-studio-v1.schema.json",
    "tools/evidence/strict_json_schema.py",
    "tools/evidence/run_m09d_impeccable_detector.py",
)
C1_CHANGED_PATHS = frozenset(SOURCE_PATHS)
RUNTIME = Path("tasks/test-evidence/m09d-theme-studio/runtime")
RECEIPT = Path("tasks/test-evidence/m09d-theme-studio.json")
TARGET_APK = RUNTIME / "app-playInternal-release.apk"
TEST_APK = RUNTIME / "app-playInternal-release-androidTest.apk"
BUILD_TARGET_APK = Path("app/build/outputs/apk/playInternal/release/app-playInternal-release.apk")
BUILD_TEST_APK = Path("app/build/outputs/apk/androidTest/playInternal/release/app-playInternal-release-androidTest.apk")
REPRODUCIBLE_BUILD_COMMAND = (
    "./gradlew", ":app:clean", ":app:assemblePlayInternalRelease",
    ":app:assemblePlayInternalReleaseAndroidTest", "--no-daemon", "--no-build-cache",
    "--no-configuration-cache", "--rerun-tasks", "-PcodecksEvidenceBuild=true",
)
PROFILE_DEVICES = {"phone": "pixel6Api35", "tablet": "m10TabletApi35"}
PROFILE_TOPOLOGY = {
    "phone": {
        "device": "pixel6Api35", "api": 35, "model": "Pixel 6",
        "config": "dev35_default_arm64-v8a_Pixel_6",
    },
    "tablet": {
        "device": "m10TabletApi35", "api": 35, "model": "Pixel Tablet",
        "config": "dev35_default_arm64-v8a_Pixel_Tablet",
    },
}
DETECTOR_ARTIFACT = Path("tasks/test-evidence/m09d-theme-studio/impeccable-detector.json")
C2_ARTIFACT_PATHS = frozenset({
    "tasks/test-evidence/m09d-theme-studio/excluded/attempt-2-initialization-error.xml",
    DETECTOR_ARTIFACT.as_posix(), TARGET_APK.as_posix(), TEST_APK.as_posix(),
    *(
        f"tasks/test-evidence/m09d-theme-studio/runtime/{profile}/{name}"
        for profile in ("phone", "tablet")
        for name in ("device-info.pb", "result.xml", "test-result.sanitized.textproto")
    ),
})
C1E_CHANGED_PATHS = frozenset({
    "docs/ux/THEME_STUDIO_LIBRARY.md",
    "tools/evidence/collect_m09d_theme_studio.py",
    "tools/evidence/test_m09d_theme_studio.py",
})
DIRTY_REVIEW_PATHS = C1E_CHANGED_PATHS
DIRTY_REVIEW_MODIFIED_PATHS = C1E_CHANGED_PATHS
PINNED_BUILD_TOOLS = "36.0.0"
MAX_XML = 4 * 1024 * 1024
MAX_COMPANION = 4 * 1024 * 1024
REBUILD_PROJECTED_BYTES = 2 * 1024 * 1024 * 1024
REBUILD_RESERVE_BYTES = 5 * 1024 * 1024 * 1024
GIT_TIMEOUT_SECONDS = 120
GRADLE_TIMEOUT_SECONDS = 15 * 60
APK_DEPENDENCY_INFO_BLOCK_ID = 0x504B4453
APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
MAX_APK_BYTES = 128 * 1024 * 1024
MAX_APK_SIGNING_BLOCK_BYTES = 16 * 1024 * 1024
MAX_APK_SIGNING_BLOCK_PAIRS = 32
MAX_CENTRAL_DIRECTORY_ENTRIES = 100_000


def sha_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def sha(path: Path) -> str:
    return sha_bytes(path.read_bytes())


def canonical_json(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


def safe_repo_path(relative: str | Path) -> Path:
    value = Path(relative)
    if value.is_absolute() or ".." in value.parts:
        raise ValueError(f"unsafe repository path: {value}")
    cursor = ROOT.resolve()
    for part in value.parts:
        cursor /= part
        if cursor.is_symlink():
            raise ValueError(f"symlink forbidden: {value}")
    resolved = (ROOT / value).resolve(strict=False)
    if not resolved.is_relative_to(ROOT.resolve()):
        raise ValueError(f"path escapes repository: {value}")
    return resolved


def require_file(path: Path, label: str, maximum: int | None = None) -> bytes:
    if not path.is_file() or path.is_symlink():
        raise ValueError(f"{label} missing or unsafe: {path}")
    size = path.stat().st_size
    if size <= 0 or (maximum is not None and size > maximum):
        raise ValueError(f"{label} empty or oversized: {path}")
    raw = path.read_bytes()
    if len(raw) != size:
        raise ValueError(f"{label} empty or oversized: {path}")
    return raw


def apk_signing_block_pair_ids(raw: bytes) -> tuple[int, ...]:
    if not raw or len(raw) > MAX_APK_BYTES:
        raise ValueError("APK empty or oversized")
    eocd_minimum = 22
    eocd_start = raw.rfind(b"PK\x05\x06", max(0, len(raw) - 65557))
    if eocd_start < 0 or eocd_start + eocd_minimum > len(raw):
        raise ValueError("APK end-of-central-directory record missing")
    comment_length = struct.unpack_from("<H", raw, eocd_start + 20)[0]
    if eocd_start + eocd_minimum + comment_length != len(raw):
        raise ValueError("APK end-of-central-directory record is not terminal")
    disk_number, central_disk, disk_entries, total_entries = struct.unpack_from(
        "<HHHH", raw, eocd_start + 4,
    )
    if disk_number != 0 or central_disk != 0 or disk_entries != total_entries:
        raise ValueError("multi-disk APK is forbidden")
    if total_entries == 0 or total_entries > MAX_CENTRAL_DIRECTORY_ENTRIES:
        raise ValueError("APK central-directory count invalid")
    central_size = struct.unpack_from("<I", raw, eocd_start + 12)[0]
    central_offset = struct.unpack_from("<I", raw, eocd_start + 16)[0]
    if central_size < 46 or central_offset + central_size != eocd_start:
        raise ValueError("APK central-directory bounds invalid")
    cursor, parsed_entries = central_offset, 0
    while cursor < eocd_start:
        if cursor + 46 > eocd_start or raw[cursor:cursor + 4] != b"PK\x01\x02":
            raise ValueError("APK central-directory record invalid")
        name_size, extra_size, entry_comment_size = struct.unpack_from("<HHH", raw, cursor + 28)
        record_size = 46 + name_size + extra_size + entry_comment_size
        if record_size > eocd_start - cursor:
            raise ValueError("APK central-directory record truncated")
        cursor += record_size
        parsed_entries += 1
        if parsed_entries > MAX_CENTRAL_DIRECTORY_ENTRIES:
            raise ValueError("APK central-directory count invalid")
    if cursor != eocd_start or parsed_entries != total_entries:
        raise ValueError("APK central-directory count mismatch")
    if central_offset < 32 or raw[central_offset - 16:central_offset] != APK_SIG_BLOCK_MAGIC:
        raise ValueError("APK signing block missing")
    footer_size = struct.unpack_from("<Q", raw, central_offset - 24)[0]
    if (
        footer_size < 24
        or footer_size > central_offset - 8
        or footer_size + 8 > MAX_APK_SIGNING_BLOCK_BYTES
    ):
        raise ValueError("APK signing block size invalid")
    block_start = central_offset - footer_size - 8
    if struct.unpack_from("<Q", raw, block_start)[0] != footer_size:
        raise ValueError("APK signing block sizes disagree")
    cursor, pairs_end = block_start + 8, central_offset - 24
    pair_ids: list[int] = []
    while cursor < pairs_end:
        if len(pair_ids) >= MAX_APK_SIGNING_BLOCK_PAIRS:
            raise ValueError("APK signing block pair count exceeds bound")
        if cursor + 8 > pairs_end:
            raise ValueError("APK signing block pair length truncated")
        pair_size = struct.unpack_from("<Q", raw, cursor)[0]
        cursor += 8
        if pair_size < 4 or pair_size > pairs_end - cursor:
            raise ValueError("APK signing block pair invalid")
        pair_ids.append(struct.unpack_from("<I", raw, cursor)[0])
        cursor += pair_size
    if cursor != pairs_end or len(pair_ids) != len(set(pair_ids)):
        raise ValueError("APK signing block pairs invalid or duplicated")
    return tuple(pair_ids)


def require_evidence_apk_without_dependency_info(path: Path, label: str) -> None:
    raw = require_file(path, label, MAX_APK_BYTES)
    if APK_DEPENDENCY_INFO_BLOCK_ID in apk_signing_block_pair_ids(raw):
        raise ValueError(f"{label} contains randomized PKDS dependency metadata")


def validate_gradle_evidence_contract(build_script: str) -> None:
    required = (
        'providers.gradleProperty("codecksEvidenceBuild")',
        ".map { value -> value.toBooleanStrict() }",
        ".orElse(false)",
        "dependenciesInfo {",
        "includeInApk = !codecksEvidenceBuild.get()",
    )
    if any(fragment not in build_script for fragment in required):
        raise ValueError("evidence build dependency-metadata contract missing")


def validate_checkout_gradle_contract(checkout: Path) -> None:
    validate_gradle_evidence_contract(
        (checkout / "app/build.gradle.kts").read_text(encoding="utf-8"),
    )


def commit_paths(commit: str) -> frozenset[str]:
    result = subprocess.run(
        ["git", "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", commit],
        cwd=ROOT, check=True, capture_output=True, text=True,
    )
    return frozenset(line for line in result.stdout.splitlines() if line)


def range_paths(base: str, head: str) -> frozenset[str]:
    result = subprocess.run(
        ["git", "diff", "--name-only", f"{base}..{head}"],
        cwd=ROOT, check=True, capture_output=True, text=True,
    )
    return frozenset(line for line in result.stdout.splitlines() if line)


def commit_parent(commit: str) -> str:
    return subprocess.run(
        ["git", "rev-parse", f"{commit}^"], cwd=ROOT,
        check=True, capture_output=True, text=True,
    ).stdout.strip()


def commit_parents(commit: str) -> tuple[str, ...]:
    line = subprocess.run(
        ["git", "rev-list", "--parents", "-n", "1", commit], cwd=ROOT,
        check=True, capture_output=True, text=True,
    ).stdout.split()
    return tuple(line[1:])


def validate_commit_relationship_values(
    source_commit: str,
    artifact_commit: str,
    receipt_commit: str | None,
    *,
    base_is_ancestor: bool,
    source_parents: tuple[str, ...],
    source_commit_paths: frozenset[str],
    source_paths: frozenset[str],
    artifact_parent: str,
    artifact_paths: frozenset[str],
    receipt_parent: str | None = None,
    receipt_paths: frozenset[str] = frozenset(),
) -> None:
    if not base_is_ancestor:
        raise ValueError("M09D reviewed base is not an ancestor of sourceCommit")
    if source_parents != (C1D_REVIEWED_COMMIT,):
        raise ValueError("M09D sourceCommit is not the direct single-parent C1e child")
    if source_commit_paths != C1E_CHANGED_PATHS:
        raise ValueError("M09D C1e commit path closure mismatch")
    if source_paths != C1_CHANGED_PATHS:
        raise ValueError("M09D base..sourceCommit path closure mismatch")
    if artifact_parent != source_commit:
        raise ValueError("M09D C2 is not a direct child of sourceCommit")
    if artifact_paths != C2_ARTIFACT_PATHS:
        raise ValueError("M09D C2 artifact path closure mismatch")
    if receipt_commit is not None:
        if receipt_parent != artifact_commit:
            raise ValueError("M09D C3 is not a direct child of C2")
        if receipt_paths != {RECEIPT.as_posix()}:
            raise ValueError("M09D C3 receipt-only path closure mismatch")


def validate_commit_relationships(
    source_commit: str, artifact_commit: str, receipt_commit: str | None,
) -> None:
    identities = [("base", SOURCE_COMMIT), ("source", source_commit), ("artifact", artifact_commit)]
    if receipt_commit is not None:
        identities.append(("receipt", receipt_commit))
    for label, commit in identities:
        probe = subprocess.run(
            ["git", "cat-file", "-t", commit], cwd=ROOT, capture_output=True, text=True,
        )
        if probe.returncode or probe.stdout.strip() != "commit":
            raise ValueError(f"M09D {label} identity is not a commit")
    base_is_ancestor = subprocess.run(
        ["git", "merge-base", "--is-ancestor", SOURCE_COMMIT, source_commit], cwd=ROOT,
    ).returncode == 0
    validate_commit_relationship_values(
        source_commit,
        artifact_commit,
        receipt_commit,
        base_is_ancestor=base_is_ancestor,
        source_parents=commit_parents(source_commit),
        source_commit_paths=commit_paths(source_commit),
        source_paths=range_paths(SOURCE_COMMIT, source_commit),
        artifact_parent=commit_parent(artifact_commit),
        artifact_paths=commit_paths(artifact_commit),
        receipt_parent=commit_parent(receipt_commit) if receipt_commit else None,
        receipt_paths=commit_paths(receipt_commit) if receipt_commit else frozenset(),
    )


def commit_chain() -> dict:
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()
    head_paths = commit_paths(head)
    if head_paths == C2_ARTIFACT_PATHS:
        artifact_commit, receipt_commit = head, None
        source_commit = subprocess.run(["git", "rev-parse", "HEAD^"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()
    elif head_paths == {RECEIPT.as_posix()}:
        receipt_commit = head
        artifact_commit = subprocess.run(["git", "rev-parse", "HEAD^"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()
        source_commit = subprocess.run(["git", "rev-parse", "HEAD^^"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()
    else:
        raise ValueError("M09D must be collected at C2 or validated at receipt-only C3")
    validate_commit_relationships(source_commit, artifact_commit, receipt_commit)
    status = subprocess.run(
        ["git", "status", "--porcelain=v1", "-z", "--untracked-files=all"],
        cwd=ROOT, check=True, capture_output=True,
    ).stdout
    validate_commit_chain_phase_status(receipt_commit, status)
    return {"baseCommit": SOURCE_COMMIT, "sourceCommit": source_commit, "artifactCommit": artifact_commit}


def validate_commit_chain_phase_status(receipt_commit: str | None, status: bytes) -> None:
    phase = "C3" if receipt_commit is not None else "C2"
    if status != b"":
        raise ValueError(f"M09D {phase} worktree must be exact clean before collection")


def validate_dirty_review_entries(entries: list[str]) -> None:
    if any(len(entry) < 4 or entry[:2] not in {" M", "??"} for entry in entries):
        raise ValueError("dirty review contains a rename, conflict, deletion, or staged path")
    actual = {entry[3:]: entry[:2] for entry in entries}
    expected = {
        path: " M" if path in DIRTY_REVIEW_MODIFIED_PATHS else "??"
        for path in DIRTY_REVIEW_PATHS
    }
    if actual != expected or len(actual) != len(entries):
        raise ValueError("dirty review path set is not the exact 3-path C1e candidate")


def validate_dirty_review_scope() -> None:
    result = subprocess.run(
        ["git", "status", "--porcelain=v1", "-z", "--untracked-files=all"],
        cwd=ROOT, check=True, capture_output=True,
    ).stdout.decode("utf-8", errors="strict")
    validate_dirty_review_entries([entry for entry in result.split("\0") if entry])


def source_binding(source_commit: str) -> dict:
    object_type = subprocess.run(
        ["git", "cat-file", "-t", source_commit], cwd=ROOT, capture_output=True, text=True,
    )
    if object_type.returncode != 0 or object_type.stdout.strip() != "commit":
        raise ValueError("M09D source commit is not a commit object")
    if subprocess.run(["git", "merge-base", "--is-ancestor", source_commit, "HEAD"], cwd=ROOT).returncode:
        raise ValueError("M09D source commit is not an ancestor of HEAD")
    sources, changes = [], []
    for relative in sorted(SOURCE_PATHS):
        current = require_file(safe_repo_path(relative), f"source {relative}")
        probe = subprocess.run(
            ["git", "cat-file", "-e", f"{SOURCE_COMMIT}:{relative}"], cwd=ROOT,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        baseline = None
        if probe.returncode == 0:
            baseline = subprocess.run(
                ["git", "show", f"{SOURCE_COMMIT}:{relative}"], cwd=ROOT, check=True, capture_output=True,
            ).stdout
        committed = subprocess.run(
            ["git", "show", f"{source_commit}:{relative}"], cwd=ROOT, check=True, capture_output=True,
        ).stdout
        if committed != current:
            raise ValueError(f"current source differs from C1: {relative}")
        sources.append({"path": relative, "sha256": sha_bytes(current)})
        changes.append({
            "path": relative,
            "baseSha256": sha_bytes(baseline) if baseline is not None else None,
            "currentSha256": sha_bytes(current),
        })
    return {
        "baseCommit": SOURCE_COMMIT,
        "sourceCommit": source_commit,
        "sources": sources,
        "sourceTreeSha256": sha_bytes(canonical_json(sources)),
        "sourceDiffSha256": sha_bytes(canonical_json(changes)),
    }


def parse_result(path: Path, device: str) -> dict:
    raw = require_file(path, f"{device} XML", MAX_XML)
    if b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
        raise ValueError("unsafe XML declaration")
    root = ET.fromstring(raw)
    suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
    if len(suites) != 1:
        raise ValueError("managed XML must contain exactly one suite")
    suite = suites[0]
    if suite.attrib.get("name") != CLASS_NAME:
        raise ValueError("managed suite class mismatch")
    property_nodes = suite.findall("./properties/property")
    properties = {node.attrib.get("name", ""): node.attrib.get("value", "") for node in property_nodes}
    expected_properties = {"device": device, "flavor": "playInternal", "project": ":app"}
    if len(properties) != len(property_nodes) or properties != expected_properties:
        raise ValueError(f"managed properties mismatch: {properties}")
    cases = suite.findall("testcase")
    identities = [(case.attrib.get("classname"), case.attrib.get("name")) for case in cases]
    expected = {(CLASS_NAME, method) for method in METHODS}
    if len(identities) != len(set(identities)) or set(identities) != expected:
        raise ValueError("managed testcase set is not exact")
    counts = {
        "tests": len(cases),
        "failures": sum(bool(case.findall("failure")) for case in cases),
        "errors": sum(bool(case.findall("error")) for case in cases),
        "skipped": sum(bool(case.findall("skipped")) for case in cases),
    }
    if counts != {"tests": len(METHODS), "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError("managed result is not a clean exact pass")
    for node in (root, suite):
        for key, value in counts.items():
            if int(node.attrib.get(key, "-1")) != value:
                raise ValueError(f"managed XML {node.tag} {key} mismatch")
    return {"className": CLASS_NAME, "methods": sorted(METHODS), "properties": properties, **counts}


def apk_id(path: Path) -> str:
    analyzer = Path(os.environ["ANDROID_HOME"]) / "cmdline-tools/latest/bin/apkanalyzer"
    return subprocess.run(
        [str(analyzer), "manifest", "application-id", str(path)], check=True, capture_output=True, text=True,
    ).stdout.strip()


def apk_identity(path: Path) -> dict:
    sdk = Path(os.environ["ANDROID_HOME"]).resolve(strict=True)
    signer = (sdk / "build-tools" / PINNED_BUILD_TOOLS / "apksigner").resolve(strict=True)
    if not signer.is_relative_to(sdk) or signer.is_symlink():
        raise ValueError("unsafe pinned apksigner")
    verified = subprocess.run(
        [str(signer), "verify", "--verbose", "--print-certs", str(path)],
        check=True, capture_output=True, text=True,
    ).stdout
    matches = re.findall(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})", verified, re.IGNORECASE)
    if len(matches) != 1:
        raise ValueError("APK signer digest is missing or ambiguous")
    analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    def manifest(field: str) -> str:
        return subprocess.run(
            [str(analyzer), "manifest", field, str(path)], check=True, capture_output=True, text=True,
        ).stdout.strip()
    return {
        "applicationId": apk_id(path), "sha256": sha(path),
        "signerSha256": matches[0].lower(), "versionCode": manifest("version-code"),
        "versionName": manifest("version-name"),
    }


def apksigner_binding() -> dict:
    sdk = Path(os.environ["ANDROID_HOME"]).resolve(strict=True)
    tool = (sdk / "build-tools" / PINNED_BUILD_TOOLS / "apksigner").resolve(strict=True)
    version = subprocess.run([str(tool), "version"], check=True, capture_output=True, text=True).stdout.strip()
    return {"buildToolsVersion": PINNED_BUILD_TOOLS, "sdkRelativePath": f"build-tools/{PINNED_BUILD_TOOLS}/apksigner", "reportedVersion": version, "sha256": sha(tool)}


def reproducible_build_contract() -> dict:
    return {
        "validation": "MANDATORY_LIVE_CLEAN_REBUILD",
        "sourceMode": "DETACHED_SOURCE_COMMIT_WORKTREE",
        "command": list(REPRODUCIBLE_BUILD_COMMAND),
        "targetOutput": BUILD_TARGET_APK.as_posix(),
        "testOutput": BUILD_TEST_APK.as_posix(),
    }


def verify_rebuilt_apk(
    label: str, committed: Path, rebuilt: Path, expected: dict,
    identity_reader=apk_identity,
) -> None:
    require_evidence_apk_without_dependency_info(committed, f"committed {label} APK")
    require_evidence_apk_without_dependency_info(rebuilt, f"rebuilt {label} APK")
    committed_raw = require_file(committed, f"committed {label} APK")
    rebuilt_raw = require_file(rebuilt, f"rebuilt {label} APK")
    if committed_raw != rebuilt_raw or sha_bytes(rebuilt_raw) != expected.get("sha256"):
        raise ValueError(f"live rebuilt {label} APK bytes differ from committed C2")
    actual = identity_reader(rebuilt)
    identity_keys = ("applicationId", "sha256", "signerSha256", "versionCode", "versionName")
    if {key: actual.get(key) for key in identity_keys} != {
        key: expected.get(key) for key in identity_keys
    }:
        raise ValueError(f"live rebuilt {label} APK identity differs from committed C2")


def raise_rebuild_errors(
    primary: BaseException | None, cleanup: BaseException | None,
) -> None:
    if primary is not None and cleanup is not None:
        raise RuntimeError(f"detached rebuild failed: {primary}; cleanup failed: {cleanup}") from primary
    if primary is not None:
        raise primary
    if cleanup is not None:
        raise cleanup


def common_worktree_admin_root() -> Path:
    common = subprocess.run(
        ["git", "rev-parse", "--path-format=absolute", "--git-common-dir"],
        cwd=ROOT, check=True, capture_output=True, text=True,
        timeout=GIT_TIMEOUT_SECONDS,
    ).stdout.strip()
    common_root = Path(common)
    return validate_worktree_admin_root(common_root / "worktrees", require_exists=False)


def validate_worktree_admin_root(root: Path, *, require_exists: bool) -> Path:
    if not root.is_absolute() or not root.parent.is_dir() or root.is_symlink():
        raise ValueError("unsafe Git worktree administration root")
    ancestor = root.parent
    while True:
        if ancestor.is_symlink():
            raise ValueError("Git worktree administration root has a symlink ancestor")
        if ancestor == ancestor.parent:
            break
        ancestor = ancestor.parent
    canonical = root.parent.resolve(strict=True) / root.name
    if canonical != root:
        raise ValueError("Git worktree administration root escapes its canonical parent")
    if root.exists():
        if not root.is_dir() or root.resolve(strict=True) != root:
            raise ValueError("unsafe Git worktree administration root")
    elif require_exists:
        raise ValueError("Git worktree administration root was not created")
    return root


def admin_entry_matches_checkout(entry: Path, checkout: Path) -> bool:
    pointer = entry / "gitdir"
    if not pointer.is_file() or pointer.is_symlink():
        return False
    try:
        target = Path(pointer.read_text(encoding="utf-8").strip()).resolve(strict=False)
    except (OSError, UnicodeError):
        return False
    return target == (checkout / ".git").resolve(strict=False)


def lexical_path_exists(path: Path) -> bool:
    try:
        path.lstat()
        return True
    except FileNotFoundError:
        return False


def owned_admin_from_checkout(checkout: Path, root: Path) -> Path:
    pointer = checkout / ".git"
    if not pointer.is_file() or pointer.is_symlink() or pointer.stat().st_size > 4096:
        raise RuntimeError("detached worktree gitdir pointer is missing or unsafe")
    lines = pointer.read_text(encoding="utf-8").splitlines()
    if len(lines) != 1 or not lines[0].startswith("gitdir: "):
        raise RuntimeError("detached worktree gitdir pointer is malformed")
    root_canonical = root.resolve(strict=True)
    candidate = Path(lines[0][len("gitdir: "):])
    if not candidate.is_absolute() or candidate.parent != root_canonical:
        raise RuntimeError("detached worktree gitdir is outside the administration root")
    if not candidate.is_dir() or candidate.is_symlink() or candidate.resolve(strict=True) != candidate:
        raise RuntimeError("detached worktree administration entry is unsafe")
    if not admin_entry_matches_checkout(candidate, checkout):
        raise RuntimeError("detached worktree administration entry does not bind checkout")
    return candidate


def matching_admin_entries(
    root: Path, checkout: Path, owned_admin: Path | None = None,
) -> frozenset[Path]:
    if root.is_symlink():
        raise ValueError("unsafe Git worktree administration root during cleanup")
    if not root.exists():
        return frozenset()
    validate_worktree_admin_root(root, require_exists=True)
    matches = []
    for entry in root.iterdir():
        if owned_admin is not None and entry == owned_admin and lexical_path_exists(entry):
            matches.append(entry)
            continue
        if entry.is_dir() and not entry.is_symlink() and admin_entry_matches_checkout(entry, checkout):
            matches.append(entry)
    return frozenset(matches)


def execute_detached_rebuild(
    source_commit: str,
    target_expected: dict,
    test_expected: dict,
    *,
    runner=subprocess.run,
    disk_usage=shutil.disk_usage,
    make_temp=tempfile.mkdtemp,
    remove_tree=shutil.rmtree,
    admin_root: Path | None = None,
    contract_checker=validate_checkout_gradle_contract,
) -> None:
    temporary_root = Path(tempfile.gettempdir()).resolve(strict=True)
    required_free = REBUILD_PROJECTED_BYTES + REBUILD_RESERVE_BYTES
    if disk_usage(temporary_root).free < required_free:
        raise ValueError("detached rebuild temp volume lacks projected build bytes plus reserve")
    worktree_root = admin_root if admin_root is not None else common_worktree_admin_root()
    worktree_root = validate_worktree_admin_root(worktree_root, require_exists=False)
    temporary = Path(make_temp(prefix="codecks-m09d-rebuild-")).resolve(strict=True)
    checkout = temporary / "source"
    owned_admin: Path | None = None
    primary_error: BaseException | None = None
    cleanup_errors: list[BaseException] = []
    try:
        runner(
            ["git", "worktree", "add", "--detach", str(checkout), source_commit],
            cwd=ROOT, check=True, timeout=GIT_TIMEOUT_SECONDS,
        )
        validate_worktree_admin_root(worktree_root, require_exists=True)
        owned_admin = owned_admin_from_checkout(checkout, worktree_root)
        contract_checker(checkout)
        runner(
            list(REPRODUCIBLE_BUILD_COMMAND), cwd=checkout, check=True,
            timeout=GRADLE_TIMEOUT_SECONDS,
        )
        verify_rebuilt_apk(
            "target", safe_repo_path(TARGET_APK), checkout / BUILD_TARGET_APK, target_expected,
        )
        verify_rebuilt_apk(
            "test", safe_repo_path(TEST_APK), checkout / BUILD_TEST_APK, test_expected,
        )
    except BaseException as error:
        primary_error = error
    finally:
        if owned_admin is None:
            try:
                matches = matching_admin_entries(worktree_root, checkout)
                if len(matches) > 1:
                    raise RuntimeError("ambiguous detached worktree administration entries")
                owned_admin = next(iter(matches), None)
            except BaseException as error:
                cleanup_errors.append(error)
        if checkout.exists() or owned_admin is not None:
            try:
                removed = runner(
                    ["git", "worktree", "remove", "--force", str(checkout)],
                    cwd=ROOT, check=False, capture_output=True, text=True,
                    timeout=GIT_TIMEOUT_SECONDS,
                )
                if removed.returncode or checkout.exists():
                    raise RuntimeError("exact detached worktree removal failed")
            except BaseException as error:
                cleanup_errors.append(error)
        try:
            owned_residue = owned_admin is not None and lexical_path_exists(owned_admin)
            if owned_residue:
                cleanup_errors.append(RuntimeError("owned worktree admin metadata remains"))
            if matching_admin_entries(worktree_root, checkout, owned_admin):
                cleanup_errors.append(RuntimeError("owned worktree admin binding remains"))
        except BaseException as error:
            cleanup_errors.append(error)
        try:
            remove_tree(temporary)
            if temporary.exists():
                raise RuntimeError("owned rebuild temp path remains")
        except BaseException as error:
            cleanup_errors.append(error)
    cleanup_error = RuntimeError(
        "; ".join(str(error) for error in cleanup_errors)
    ) if cleanup_errors else None
    raise_rebuild_errors(primary_error, cleanup_error)


def verify_reproducible_apk_build(
    source_commit: str, target_expected: dict, test_expected: dict,
) -> None:
    if subprocess.run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=ROOT, check=True, capture_output=True,
    ).stdout:
        raise ValueError("live APK rebuild requires an exact clean C3 worktree")
    gradlew = safe_repo_path("gradlew")
    if not gradlew.is_file() or gradlew.is_symlink():
        raise ValueError("unsafe Gradle wrapper")
    execute_detached_rebuild(source_commit, target_expected, test_expected)


def sanitize_textproto(raw: bytes) -> bytes:
    if len(raw) > MAX_COMPANION:
        raise ValueError("textproto oversized")
    text = raw.decode("utf-8", errors="strict")
    text = text.replace(str(ROOT.resolve()), "$REPO_ROOT")
    forbidden = ("/Users/", "/opt/codex-auth/worktrees/", "BEGIN PRIVATE KEY", "password=", "token=")
    if any(value.lower() in text.lower() for value in forbidden):
        raise ValueError("textproto contains a private path or secret marker")
    if f"scheduled_test_case_count: {len(METHODS)}" not in text or "test_status: PASSED" not in text:
        raise ValueError("textproto does not describe the exact passing suite")
    for method in METHODS:
        if f'test_method: "{method}"' not in text:
            raise ValueError(f"textproto missing method: {method}")
    return text.encode("utf-8")


def parse_topology(profile: str, device_info: bytes, companion: bytes) -> dict:
    expected = PROFILE_TOPOLOGY[profile]
    other = PROFILE_TOPOLOGY["tablet" if profile == "phone" else "phone"]
    for token in (expected["config"], "arm64-v8a", "Android SDK built for arm64"):
        if token.encode() not in device_info:
            raise ValueError(f"{profile} device-info missing topology token: {token}")
    if other["config"].encode() in device_info:
        raise ValueError(f"{profile} device-info contains the other profile config")
    serials = {item.decode() for item in re.findall(rb"emulator-[0-9]{4}", device_info)}
    if len(serials) != 1:
        raise ValueError(f"{profile} device-info serial is missing or ambiguous")
    serial = serials.pop()
    text = companion.decode("utf-8", errors="strict")
    expected_root = (
        "$REPO_ROOT/app/build/outputs/androidTest-results/managedDevice/"
        f"release/flavors/playInternal/{expected['device']}"
    )
    paths = re.findall(r'path: "([^"]+)"', text)
    if not paths or any(not path.startswith(expected_root + "/") for path in paths):
        raise ValueError(f"{profile} textproto contains an unexpected managed-device path")
    device_info_path = f"{expected_root}/device-info.pb"
    if paths.count(device_info_path) != len(METHODS):
        raise ValueError(f"{profile} textproto device-info path count mismatch")
    if other["device"] in text or text.count(f'id: "{serial}"') < len(METHODS) + 1:
        raise ValueError(f"{profile} textproto topology mismatch")
    return {
        "profile": profile,
        "device": expected["device"],
        "api": expected["api"],
        "model": expected["model"],
        "config": expected["config"],
        "serial": serial,
        "resultPathRoot": expected_root,
        "deviceInfoPath": device_info_path,
        "formFactor": profile,
        "smallestScreenWidthRule": "<600dp" if profile == "phone" else ">=600dp",
        "windowProofMethod": "rtlLandscapePolicyOverrideBindsActualPhoneOrTabletWindow",
        "policyOverride": "1280x720_POLICY_ONLY_NOT_DEX_RUNTIME",
    }


def detector_gate() -> dict:
    path = safe_repo_path(DETECTOR_ARTIFACT)
    artifact = json.loads(require_file(path, "Impeccable detector artifact", MAX_COMPANION))
    expected_targets = [
        "app/src/main/java/io/codecks/ui/settings/SettingsControlSections.kt",
        "app/src/main/java/io/codecks/ui/theme/ThemeStudioPanel.kt",
    ]
    expected = {
        "schema": "codecks.m09d.impeccable-detector.v1",
        "status": "PASS",
        "command": ["node", "$IMPECCABLE_DETECTOR", "--json", *expected_targets],
        "detector": artifact.get("detector"),
        "targets": [
            {"path": target, "sha256": sha(safe_repo_path(target))}
            for target in expected_targets
        ],
        "result": [],
    }
    if artifact != expected:
        raise ValueError("Impeccable detector artifact does not bind current UI source/result")
    detector = artifact.get("detector")
    if not isinstance(detector, dict) or set(detector) != {"name", "sha256"}:
        raise ValueError("Impeccable detector identity is not closed")
    if detector["name"] != "detect.mjs" or not re.fullmatch(r"[0-9a-f]{64}", detector["sha256"]):
        raise ValueError("Impeccable detector identity is invalid")
    return {"path": DETECTOR_ARTIFACT.as_posix(), "sha256": sha(path), **artifact}


def verify_detector_live(detector_path: Path) -> None:
    detector = detector_path.resolve(strict=True)
    if detector.is_symlink() or not detector.is_file() or detector.name != "detect.mjs":
        raise ValueError("unsafe or unexpected Impeccable detector")
    artifact = detector_gate()
    if sha(detector) != artifact["detector"]["sha256"]:
        raise ValueError("live Impeccable detector differs from bound tool")
    targets = [item["path"] for item in artifact["targets"]]
    run = subprocess.run(["node", str(detector), "--json", *targets], cwd=ROOT, check=True, capture_output=True, text=True)
    if json.loads(run.stdout) != []:
        raise ValueError("live Impeccable detector reported findings")


def artifact_set() -> list[dict]:
    return [{"path": path, "sha256": sha(safe_repo_path(path))} for path in sorted(C2_ARTIFACT_PATHS)]


def atomic_copy(source: Path, destination: Path, transform=None) -> None:
    raw = require_file(source, f"capture source {source}")
    if transform:
        raw = transform(raw)
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{destination.name}.", dir=destination.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, destination)
        directory = os.open(destination.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        Path(temporary).unlink(missing_ok=True)


def capture(profile_sources: dict[str, Path], target: Path, test: Path) -> None:
    atomic_copy(target, safe_repo_path(TARGET_APK))
    atomic_copy(test, safe_repo_path(TEST_APK))
    for profile, source_result in profile_sources.items():
        destination = RUNTIME / profile
        atomic_copy(source_result, safe_repo_path(destination / "result.xml"))
        atomic_copy(source_result.parent / "device-info.pb", safe_repo_path(destination / "device-info.pb"))
        atomic_copy(
            source_result.parent / "test-result.textproto",
            safe_repo_path(destination / "test-result.sanitized.textproto"),
            sanitize_textproto,
        )


def collect_receipt() -> dict:
    chain = commit_chain()
    target, test = safe_repo_path(TARGET_APK), safe_repo_path(TEST_APK)
    validate_gradle_evidence_contract(safe_repo_path("app/build.gradle.kts").read_text(encoding="utf-8"))
    require_evidence_apk_without_dependency_info(target, "C2 target APK")
    require_evidence_apk_without_dependency_info(test, "C2 test APK")
    target_identity, test_identity = apk_identity(target), apk_identity(test)
    target_id, test_id = target_identity["applicationId"], test_identity["applicationId"]
    if (target_id, test_id) != ("app.codecks.internal", "app.codecks.internal.test"):
        raise ValueError(f"APK package mismatch: {target_id}, {test_id}")
    if target_identity["signerSha256"] != test_identity["signerSha256"]:
        raise ValueError("target and test APK signer mismatch")
    runs = []
    for profile, device in PROFILE_DEVICES.items():
        base = RUNTIME / profile
        result = safe_repo_path(base / "result.xml")
        device_info = safe_repo_path(base / "device-info.pb")
        textproto = safe_repo_path(base / "test-result.sanitized.textproto")
        parsed = parse_result(result, device)
        companion = require_file(textproto, f"{profile} textproto", MAX_COMPANION)
        sanitize_textproto(companion)
        device_info_raw = require_file(device_info, f"{profile} device-info", MAX_COMPANION)
        topology = parse_topology(profile, device_info_raw, companion)
        runs.append({
            "profile": profile,
            "device": device,
            "status": "PASS",
            "result": {"path": (base / "result.xml").as_posix(), "sha256": sha(result), **parsed},
            "deviceInfo": {"path": (base / "device-info.pb").as_posix(), "sha256": sha(device_info)},
            "textproto": {"path": (base / "test-result.sanitized.textproto").as_posix(), "sha256": sha(textproto), "sanitized": True},
            "topology": topology,
            "executionBinding": {
                "androidTestSourceSha256": sha(safe_repo_path("app/src/androidTest/java/io/codecks/ui/theme/ThemeStudioInstrumentedTest.kt")),
                "targetApkSha256": target_identity["sha256"], "testApkSha256": test_identity["sha256"],
                "resultSha256": sha(result), "deviceInfoSha256": sha(device_info), "textprotoSha256": sha(textproto),
            },
        })
    excluded_result = safe_repo_path("tasks/test-evidence/m09d-theme-studio/excluded/attempt-2-initialization-error.xml")
    return {
        "schema": SCHEMA_ID,
        "milestone": "M09D",
        "status": "PASS",
        "scope": "PLAY_INTERNAL_MANAGED_REPOSITORY_AND_UI_ONLY",
        "commitChain": {**chain, "artifacts": artifact_set(), "artifactSetSha256": sha_bytes(canonical_json(artifact_set()))},
        "sourceBinding": source_binding(chain["sourceCommit"]),
        "tooling": {"apksigner": apksigner_binding()},
        "reproducibleBuild": reproducible_build_contract(),
        "targetApk": {"path": TARGET_APK.as_posix(), **target_identity},
        "testApk": {"path": TEST_APK.as_posix(), **test_identity},
        "runs": runs,
        "detectorGate": detector_gate(),
        "excludedAttempts": [
            {
                "ordinal": 1,
                "status": "EXCLUDED",
                "device": "pixel6Api35",
                "tests": 9,
                "passed": 8,
                "failed": 1,
                "failure": "allPresetsAndGeneratedCustomRenderAtTwoHundredPercentText: offscreen Ocean preset scroll",
                "artifactRetention": "NOT_RETAINED",
                "eligibleForPass": False,
            },
            {
                "ordinal": 2,
                "status": "EXCLUDED",
                "device": "pixel6Api35",
                "tests": 1,
                "passed": 0,
                "failed": 1,
                "failure": "initializationError: coroutine expression-body tests exposed non-void JUnit signatures",
                "artifactRetention": "RETAINED",
                "artifact": {
                    "path": "tasks/test-evidence/m09d-theme-studio/excluded/attempt-2-initialization-error.xml",
                    "sha256": sha(excluded_result),
                },
                "eligibleForPass": False,
            },
        ],
        "claims": {
            "repositoryLifecycle": "PASS",
            "broaderControllerProcessLifecycle": "NOT_RUN",
            "physicalDevice": "NOT_RUN",
            "publicRelease": "NOT_RUN",
        },
    }


def repository_status_bytes() -> bytes:
    return subprocess.run(
        ["git", "status", "--porcelain=v1", "-z", "--untracked-files=all"],
        cwd=ROOT, check=True, capture_output=True,
    ).stdout


def validate_clean_c2_receipt_write(status: bytes, receipt_exists: bool) -> None:
    if status != b"":
        raise ValueError("M09D receipt write requires an exact clean C2 worktree")
    if receipt_exists:
        raise ValueError("M09D receipt already exists; refusing replacement")


def write_receipt(
    receipt: dict,
    *,
    destination: Path | None = None,
    status_reader=repository_status_bytes,
) -> None:
    output = safe_repo_path(RECEIPT) if destination is None else destination
    status = status_reader()
    if output.is_symlink():
        raise ValueError("M09D receipt destination symlink forbidden")
    validate_clean_c2_receipt_write(status, output.exists())
    atomic_create_bytes(canonical_json(receipt) + b"\n", output)


def atomic_create_bytes(raw: bytes, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{destination.name}.", dir=destination.parent)
    temporary_path = Path(temporary)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary_path, destination)
        directory = os.open(destination.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        temporary_path.unlink(missing_ok=True)


def atomic_copy_bytes(raw: bytes, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{destination.name}.", dir=destination.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, destination)
        directory = os.open(destination.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        Path(temporary).unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--capture", action="store_true")
    parser.add_argument("--verify-dirty-review-scope", action="store_true")
    parser.add_argument("--phone-result", type=Path)
    parser.add_argument("--tablet-result", type=Path)
    parser.add_argument("--target-apk", type=Path)
    parser.add_argument("--test-apk", type=Path)
    args = parser.parse_args()
    if args.verify_dirty_review_scope:
        validate_dirty_review_scope()
        print("M09D_C1E_DIRTY_REVIEW_SCOPE_PASS_3")
        return
    if args.capture:
        required = (args.phone_result, args.tablet_result, args.target_apk, args.test_apk)
        if any(value is None for value in required):
            parser.error("--capture requires both results and both APKs")
        capture({"phone": args.phone_result, "tablet": args.tablet_result}, args.target_apk, args.test_apk)
    receipt = collect_receipt()
    write_receipt(receipt)
    print(f"M09D receipt PASS: {RECEIPT}")


if __name__ == "__main__":
    main()
