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
C1_CHANGED_PATHS = frozenset(SOURCE_PATHS) - {"app/build.gradle.kts"}
RUNTIME = Path("tasks/test-evidence/m09d-theme-studio/runtime")
RECEIPT = Path("tasks/test-evidence/m09d-theme-studio.json")
TARGET_APK = RUNTIME / "app-playInternal-release.apk"
TEST_APK = RUNTIME / "app-playInternal-release-androidTest.apk"
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
DIRTY_REVIEW_PATHS = C1_CHANGED_PATHS | C2_ARTIFACT_PATHS | {RECEIPT.as_posix()}
DIRTY_REVIEW_MODIFIED_PATHS = frozenset({
    ".gitignore",
    "app/src/androidTest/java/io/codecks/ui/theme/ThemeStudioInstrumentedTest.kt",
    "app/src/main/java/io/codecks/domain/icons/DeckIconCatalog.kt",
    "app/src/main/java/io/codecks/ui/icons/DeckIconResolver.kt",
    "app/src/main/java/io/codecks/ui/settings/SettingsControlSections.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeScheme.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeSettingsRepository.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeStudioPanel.kt",
    "app/src/test/java/io/codecks/domain/icons/DeckIconCatalogTest.kt",
    "app/src/test/java/io/codecks/ui/icons/RoundedDeckIconResolverTest.kt",
    "app/src/test/java/io/codecks/ui/settings/DeckStyleGalleryPolicyTest.kt",
    "app/src/test/java/io/codecks/ui/theme/ThemeSchemeTest.kt",
})
PINNED_BUILD_TOOLS = "36.0.0"
MAX_XML = 4 * 1024 * 1024
MAX_COMPANION = 4 * 1024 * 1024


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
    raw = path.read_bytes()
    if not raw or (maximum is not None and len(raw) > maximum):
        raise ValueError(f"{label} empty or oversized: {path}")
    return raw


def commit_paths(commit: str) -> frozenset[str]:
    result = subprocess.run(
        ["git", "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", commit],
        cwd=ROOT, check=True, capture_output=True, text=True,
    )
    return frozenset(line for line in result.stdout.splitlines() if line)


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
    if commit_paths(artifact_commit) != C2_ARTIFACT_PATHS or commit_paths(source_commit) != C1_CHANGED_PATHS:
        raise ValueError("M09D C1/C2 commit path closure mismatch")
    parent = subprocess.run(["git", "rev-parse", f"{source_commit}^"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()
    if parent != SOURCE_COMMIT:
        raise ValueError("M09D C1 is not directly based on the reviewed base")
    status = subprocess.run(
        ["git", "status", "--porcelain=v1", "-z", "--untracked-files=all"],
        cwd=ROOT, check=True, capture_output=True,
    ).stdout
    allowed = b"" if receipt_commit else f"?? {RECEIPT.as_posix()}\0".encode()
    if status != allowed:
        raise ValueError("M09D C2/C3 worktree state is not exact")
    return {"baseCommit": SOURCE_COMMIT, "sourceCommit": source_commit, "artifactCommit": artifact_commit}


def validate_dirty_review_entries(entries: list[str]) -> None:
    if any(len(entry) < 4 or entry[:2] not in {" M", "??"} for entry in entries):
        raise ValueError("dirty review contains a rename, conflict, deletion, or staged path")
    actual = {entry[3:]: entry[:2] for entry in entries}
    expected = {
        path: " M" if path in DIRTY_REVIEW_MODIFIED_PATHS else "??"
        for path in DIRTY_REVIEW_PATHS
    }
    if actual != expected or len(actual) != len(entries):
        raise ValueError("dirty review path set is not the exact 32-path candidate")


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


def write_receipt(receipt: dict) -> None:
    destination = safe_repo_path(RECEIPT)
    destination.parent.mkdir(parents=True, exist_ok=True)
    atomic_copy_bytes(canonical_json(receipt) + b"\n", destination)


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
        print("M09D_DIRTY_REVIEW_SCOPE_PASS_32")
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
