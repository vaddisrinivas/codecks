#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RECEIPT = ROOT / "tasks/test-evidence/visual-accessibility-local.json"
SOURCES = (
    "app/src/main/java/io/codecks/core/design/CoreDesign.kt",
    "app/src/main/java/io/codecks/ui/app/CodecksAppShell.kt",
    "app/src/main/java/io/codecks/ui/editor/DeckBlankButtonComposer.kt",
    "app/src/main/java/io/codecks/ui/home/HomeDeckSections.kt",
    "app/src/main/java/io/codecks/ui/home/HomeScreen.kt",
    "app/src/androidTestPlayInternal/java/io/codecks/ui/designsystem/CodecksDesignSystemInstrumentedTest.kt",
    "tools/evidence/visual_accessibility_receipt.py",
    "tools/evidence/test_visual_accessibility_receipt.py",
)
APP_APK = ROOT / "app/build/outputs/apk/playInternal/release/app-playInternal-release.apk"
TEST_APK = ROOT / "app/build/outputs/apk/androidTest/playInternal/release/app-playInternal-release-androidTest.apk"
XML = ROOT / "app/build/outputs/androidTest-results/managedDevice/release/flavors/playInternal/pixel6Api35/TEST-pixel6Api35-_app-playInternal.xml"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def managed_identity(xml_text: str) -> dict:
    suite = ET.fromstring(xml_text)
    properties = {item.attrib["name"]: item.attrib["value"] for item in suite.findall("./testsuite/properties/property")}
    return {"name": properties.get("device"), "api": 35, "kind": "EMULATOR", "flavor": properties.get("flavor"), "project": properties.get("project")}


def build() -> dict:
    suite = ET.parse(XML).getroot()
    base = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    diff = subprocess.check_output(["git", "diff", "--binary"], cwd=ROOT)
    return {
        "schema": "codecks.visual-accessibility.local.v1",
        "scope": "LOCAL_DIRTY_WORKTREE_EMULATOR_ONLY",
        "baseCommit": base,
        "dirtyDiffSha256": hashlib.sha256(diff).hexdigest(),
        "sources": [{"path": p, "sha256": digest(ROOT / p)} for p in SOURCES],
        "device": managed_identity(XML.read_text()),
        "result": {
            "tests": int(suite.attrib["tests"]), "failures": int(suite.attrib["failures"]),
            "errors": int(suite.attrib["errors"]), "skipped": int(suite.attrib["skipped"]),
            "xmlPath": str(XML.relative_to(ROOT)), "xmlSha256": digest(XML),
        },
        "artifacts": [
            {"kind": "app", "path": str(APP_APK.relative_to(ROOT)), "sha256": digest(APP_APK)},
            {"kind": "test", "path": str(TEST_APK.relative_to(ROOT)), "sha256": digest(TEST_APK)},
        ],
        "claims": ["compact_200_percent_home", "normal_font_four_columns", "compact_200_percent_navigation", "smart_suggestion_reflow", "color_swatch_semantics"],
        "excluded": ["physical_phone", "production_package", "tablet_full_matrix", "release_candidate", "commit_bound_proof"],
    }


def validate(data: dict) -> None:
    expected = build()
    if data != expected:
        raise ValueError("receipt does not match current dirty sources and managed artifacts")
    result = data["result"]
    if (result["tests"], result["failures"], result["errors"], result["skipped"]) != (13, 0, 0, 0):
        raise ValueError("managed result is not 13/13 pass")
    if data["device"] != {"name": "pixel6Api35", "api": 35, "kind": "EMULATOR", "flavor": "playInternal", "project": ":app"}:
        raise ValueError("managed XML identity mismatch")
    if data["scope"] != "LOCAL_DIRTY_WORKTREE_EMULATOR_ONLY":
        raise ValueError("proof boundary changed")


def main() -> int:
    if "--write" in sys.argv:
        RECEIPT.parent.mkdir(parents=True, exist_ok=True)
        RECEIPT.write_text(json.dumps(build(), indent=2) + "\n")
    validate(json.loads(RECEIPT.read_text()))
    print("PASS: local dirty-worktree emulator visual receipt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
