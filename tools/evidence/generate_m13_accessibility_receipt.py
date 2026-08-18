#!/usr/bin/env python3
"""Create a fail-closed M13 receipt from canonical Gradle JUnit evidence."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

from managed_execution_binding import collect_binding


BASE_COMMIT = "b4562b5b9a770da0ea0f47a66b61ef799463adb2"
HEX_COMMIT = re.compile(r"^[0-9a-f]{40}$")
MAX_JUNIT_BYTES = 4 * 1024 * 1024

UNIT_REQUIRED = {
    "io.codecks.ui.app.KeyboardNavigationPolicyTest.tabAndShiftTabHaveDeterministicDirection",
    "io.codecks.ui.app.KeyboardNavigationPolicyTest.highFontScaleRetainsNavigationOrEmergencyStopAcrossRequiredSizes",
    "io.codecks.ui.app.AccessibilityPrimitiveTest.supportedTextScaleMatrixKeepsTargetsAndOnlyReflowsAtTwoHundredPercent",
    "io.codecks.ui.app.AccessibilityCriticalFlowPolicyTest.keyboardComposerHasPersistentLabelLiveDeliveryStatusAndTwoHundredPercentReflow",
    "io.codecks.ui.app.AccessibilityCriticalFlowPolicyTest.trackpadExposesTalkBackEquivalentsWithoutLeakingRestrictedLockscreenActions",
    "io.codecks.ui.app.AccessibilityCriticalFlowPolicyTest.lockscreenHelperOverlayAndWidgetUseNonInteractiveStatusAndNamedSurfaces",
    "io.codecks.ui.designsystem.CodecksDesignSystemTest.all offline themes preserve text and semantic feedback contrast",
    "io.codecks.ui.designsystem.CodecksDesignSystemTest.reduced motion resolves transitions to instant and stops continuous motion",
}

MANAGED_CLASS = "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest"
MANAGED_REQUIRED = {
    f"{MANAGED_CLASS}.blankColorSwatchHasTokenSizedRadioSemantics",
    f"{MANAGED_CLASS}.smartSuggestionReflowsWithoutTruncatingMeaningAtTwoHundredPercentText",
    f"{MANAGED_CLASS}.compactHomeReflowsHeaderAndDeckToTwoColumnsAtTwoHundredPercentText",
    f"{MANAGED_CLASS}.normalFontCompactHomePreservesFourColumnDeck",
    f"{MANAGED_CLASS}.compactBottomNavigationKeepsFullAccessibleNamesAtTwoHundredPercentText",
    "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest.lockscreenCriticalActionsRemainVisibleAndTouchableAtTwoHundredPercentText",
    "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest.keyboardComposerKeepsNamedFullWidthActionsAtTwoHundredPercentText",
    "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest.firstRunTrackpadSetupRemainsScrollableAtTwoHundredPercentText",
    "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest.deckAndHelperRenderInRtlDexWindowWithHighContrastOledThemeAndHaptics",
    "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest.ckDeckKeyFocusRingAndClickLongClickHapticsAreObservable",
    "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest.customThemeAndReducedMotionOverlayRenderWithoutDecorativeSemantics",
    "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest.widgetInitialLayoutInflatesWithTokenOwnedFallbacks",
    "io.codecks.ui.designsystem.CodecksDesignSystemInstrumentedTest.animatorScaleChangesUpdateMotionPolicyWithoutRecreatingComposition",
}

UNIT_CLASSES = (
    "io.codecks.ui.app.AccessibilityCriticalFlowPolicyTest",
    "io.codecks.ui.app.AccessibilityPrimitiveTest",
    "io.codecks.ui.app.KeyboardNavigationPolicyTest",
    "io.codecks.ui.designsystem.CodecksDesignSystemTest",
)

SOURCE_PATHS = (
    "app/src/main/java/io/codecks/AppDestinationSupport.kt",
    "app/src/main/java/io/codecks/ui/designsystem/DeckComponents.kt",
    "app/src/main/java/io/codecks/ui/keyboard/KeyboardScreen.kt",
    "app/src/main/java/io/codecks/ui/mouse/RawTrackpadAdapter.kt",
    "app/src/main/java/io/codecks/ui/mouse/TrackpadHostScreen.kt",
    "app/src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadScreen.kt",
    "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt",
    "app/src/main/res/layout/trackpad_widget.xml",
    "app/src/main/res/values/accessibility_ids.xml",
    "app/src/main/res/values/design_tokens.xml",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/xml/trackpad_widget_info.xml",
)

TEST_SOURCE_PATHS = (
    "app/src/androidTestPlayInternal/java/io/codecks/ui/designsystem/CodecksDesignSystemInstrumentedTest.kt",
    "app/src/androidTest/java/io/codecks/ui/mouse/RawTrackpadViewInstrumentedTest.kt",
    "app/src/test/java/io/codecks/ui/app/AccessibilityCriticalFlowPolicyTest.kt",
    "app/src/test/java/io/codecks/ui/app/AccessibilityPrimitiveTest.kt",
    "tools/evidence/generate_m13_accessibility_receipt.py",
    "tools/evidence/managed_execution_binding.py",
    "tools/evidence/test_generate_m13_accessibility_receipt.py",
    "tools/evidence/validate_m13_accessibility_receipt.py",
    "tools/evidence/schemas/autonomous-maturity-m13-accessibility-v1.schema.json",
)

BOUND_PATHS = SOURCE_PATHS + TEST_SOURCE_PATHS
MANAGED_RESULT = "tasks/test-evidence/m13/runtime/TEST-CodecksDesignSystemInstrumentedTest.xml"
TARGET_APK = "tasks/test-evidence/m13/runtime/app-playInternal-release.apk"
TEST_APK = "tasks/test-evidence/m13/runtime/app-playInternal-release-androidTest.apk"


@dataclass(frozen=True)
class RepositoryIdentity:
    base_commit: str
    source_commit: str
    ancestry_verified: bool


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_managed_path(root: Path) -> Path:
    return root / MANAGED_RESULT


def canonical_unit_paths(root: Path) -> list[Path]:
    directory = root / "app/build/test-results/testPlayInternalReleaseUnitTest"
    return [directory / f"TEST-{class_name}.xml" for class_name in UNIT_CLASSES]


def canonical_managed_companions(root: Path) -> tuple[Path, Path]:
    directory = canonical_managed_path(root).parent
    return directory / "device-info.pb", directory / "test-result.pb"


def parse_junit(path: Path, expected_root: str) -> tuple[dict[str, str], str]:
    if not path.is_file():
        raise ValueError(f"JUnit file missing: {path}")
    raw = path.read_bytes()
    if len(raw) > MAX_JUNIT_BYTES:
        raise ValueError(f"JUnit file oversized: {path}")
    if b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
        raise ValueError(f"JUnit DTD/entity forbidden: {path}")
    root = ET.fromstring(raw)
    if root.tag != expected_root:
        raise ValueError(f"Unexpected JUnit root type {root.tag}: {path}")
    results: dict[str, str] = {}
    for case in root.iter("testcase"):
        identity = f"{case.attrib.get('classname', '')}.{case.attrib.get('name', '')}"
        if not identity.strip("."):
            raise ValueError(f"Unnamed testcase: {path}")
        if identity in results:
            raise ValueError(f"Duplicate testcase: {identity}")
        status = "PASS"
        for child in case:
            if child.tag in {"failure", "error"}:
                status = "FAIL"
            elif child.tag == "skipped" and status != "FAIL":
                status = "SKIP"
        results[identity] = status
    if not results:
        raise ValueError(f"JUnit contains no testcases: {path}")
    return results, hashlib.sha256(raw).hexdigest()


def verify_managed_metadata(path: Path) -> None:
    root = ET.parse(path).getroot()
    properties = {
        node.attrib.get("name"): node.attrib.get("value")
        for node in root.iter("property")
    }
    required = {"device": "pixel6Api35", "flavor": "playInternal", "project": ":app"}
    if any(properties.get(key) != value for key, value in required.items()):
        raise ValueError("Managed JUnit metadata mismatch")


def require_pass(results: dict[str, str], required: set[str], evidence_type: str) -> None:
    missing = sorted(required - results.keys())
    if missing:
        raise ValueError(f"Required {evidence_type} tests missing: " + ", ".join(missing))
    failed = sorted(test for test in required if results[test] != "PASS")
    if failed:
        raise ValueError(f"Required {evidence_type} tests not passing: " + ", ".join(failed))


def validate_repository_identity(identity: RepositoryIdentity) -> None:
    if not HEX_COMMIT.fullmatch(identity.base_commit) or not HEX_COMMIT.fullmatch(identity.source_commit):
        raise ValueError("Repository commit identity malformed")
    if identity.base_commit != BASE_COMMIT or not identity.ancestry_verified:
        raise ValueError("M13 base ancestry not verified")


def hash_paths(root: Path, paths: tuple[str, ...]) -> dict[str, str]:
    hashes: dict[str, str] = {}
    for relative in paths:
        path = root / relative
        if not path.is_file():
            raise ValueError(f"Evidence source missing: {relative}")
        hashes[relative] = digest(path)
    return hashes


def workspace_digest(source_hashes: dict[str, str], test_hashes: dict[str, str]) -> str:
    canonical = "".join(
        f"{path}\0{value}\n" for path, value in sorted((source_hashes | test_hashes).items())
    )
    return hashlib.sha256(canonical.encode()).hexdigest()


def build_receipt(
    root: Path,
    managed_junit: Path,
    managed_device: str,
    repository: RepositoryIdentity,
) -> dict[str, object]:
    root = root.resolve()
    validate_repository_identity(repository)
    if managed_device != "pixel6Api35":
        raise ValueError("M13 runtime evidence must come from pixel6Api35")
    expected_managed = canonical_managed_path(root).resolve()
    if managed_junit.resolve() != expected_managed:
        raise ValueError("Managed JUnit path is not canonical")

    managed_results, managed_digest = parse_junit(expected_managed, "testsuites")
    verify_managed_metadata(expected_managed)
    require_pass(managed_results, MANAGED_REQUIRED, "managed")
    if UNIT_REQUIRED & managed_results.keys():
        raise ValueError("Unit testcase injected into managed evidence")
    companion_evidence = []
    for path, evidence_type in zip(
        canonical_managed_companions(root),
        ("GRADLE_MANAGED_DEVICE_INFO_PROTO", "GRADLE_MANAGED_TEST_RESULT_PROTO"),
        strict=True,
    ):
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"Managed companion evidence missing: {path}")
        companion_evidence.append({
            "path": path.relative_to(root).as_posix(),
            "sha256": digest(path),
            "type": evidence_type,
        })

    unit_results: dict[str, str] = {}
    unit_evidence = []
    for path in canonical_unit_paths(root):
        parsed, junit_digest = parse_junit(path, "testsuite")
        overlap = unit_results.keys() & parsed.keys()
        if overlap:
            raise ValueError("Duplicate testcase across unit JUnit files")
        unit_results.update(parsed)
        unit_evidence.append({
            "path": path.relative_to(root).as_posix(),
            "sha256": junit_digest,
            "type": "GRADLE_JVM_JUNIT_XML",
        })
    require_pass(unit_results, UNIT_REQUIRED, "unit")
    if MANAGED_REQUIRED & unit_results.keys():
        raise ValueError("Managed testcase injected into unit evidence")

    source_hashes = hash_paths(root, SOURCE_PATHS)
    test_hashes = hash_paths(root, TEST_SOURCE_PATHS)
    receipt = {
        "schema": "codecks.autonomous-maturity.m13-accessibility.v1",
        "schema_version": 3,
        "milestone": "M13",
        "status": "PASS",
        "repository": {
            "base_commit": repository.base_commit,
            "source_commit": repository.source_commit,
            "base_is_ancestor": True,
            "workspace_content_sha256": workspace_digest(source_hashes, test_hashes),
        },
        "proof_boundary": {
            "unit_contract_matrix": "PASS",
            "managed_android_api35_accessibility_matrix": "PASS",
            "physical_device": "NOT_RUN",
            "human_talkback_comprehension": "EXTERNAL_EVIDENCE_REQUIRED",
        },
        "managed_evidence": {
            "device": managed_device,
            "flavor": "playInternal",
            "gradle_task": ":app:pixel6Api35PlayInternalReleaseAndroidTest",
            "path": expected_managed.relative_to(root).as_posix(),
            "sha256": managed_digest,
            "type": "GRADLE_MANAGED_DEVICE_JUNIT_XML",
            "companions": companion_evidence,
            "required_tests": {test: managed_results[test] for test in sorted(MANAGED_REQUIRED)},
        },
        "unit_evidence": unit_evidence,
        "unit_gradle_task": ":app:testPlayInternalReleaseUnitTest",
        "required_unit_tests": {test: unit_results[test] for test in sorted(UNIT_REQUIRED)},
        "source_sha256": source_hashes,
        "test_source_sha256": test_hashes,
    }
    receipt["managed_execution"] = collect_binding(
        root,
        source_paths=BOUND_PATHS,
        class_name=MANAGED_CLASS,
        methods={identity.rsplit(".", 1)[1] for identity in MANAGED_REQUIRED},
        result_path=MANAGED_RESULT,
        target_apk=TARGET_APK,
        test_apk=TEST_APK,
    )
    return receipt


def git_output(root: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=root, check=True, text=True, capture_output=True,
    ).stdout.strip()


def repository_identity(root: Path) -> RepositoryIdentity:
    source = git_output(root, "rev-parse", "HEAD")
    ancestry = subprocess.run(
        ["git", "merge-base", "--is-ancestor", BASE_COMMIT, source], cwd=root,
        check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0
    return RepositoryIdentity(BASE_COMMIT, source, ancestry)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--managed-junit", type=Path, required=True)
    parser.add_argument("--managed-device", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve()
    receipt = build_receipt(root, args.managed_junit, args.managed_device, repository_identity(root))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")
    print(f"PASS: M13 receipt written to {args.output}")


if __name__ == "__main__":
    main()
