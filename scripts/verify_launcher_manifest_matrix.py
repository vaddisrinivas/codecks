#!/usr/bin/env python3
"""Fail-closed M09B merged-manifest launcher classifier."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = Path(__file__).resolve().parents[1]
VARIANTS = (
    "ossDebug",
    "ossRelease",
    "playRelease",
    "playInternalRelease",
)
PRIMARY = {
    "io.codecks.launcher.RobotFaceLauncher",
    "io.codecks.launcher.RobotGridLauncher",
    "io.codecks.launcher.PointerGridLauncher",
    "io.codecks.launcher.MinimalGreenLauncher",
}
DEFAULT = "io.codecks.launcher.RobotFaceLauncher"
LAB = "io.codecks.internalcommercial.labui.CommercialLabLauncher"


def attr(node: ET.Element, name: str) -> str | None:
    return node.get(ANDROID + name)


def enabled(node: ET.Element) -> bool:
    return attr(node, "enabled") != "false"


def is_launcher(node: ET.Element) -> bool:
    actions = {attr(item, "name") for item in node.findall("./intent-filter/action")}
    categories = {attr(item, "name") for item in node.findall("./intent-filter/category")}
    return (
        "android.intent.action.MAIN" in actions
        and "android.intent.category.LAUNCHER" in categories
    )


def merged_manifest(variant: str) -> Path:
    directory = ROOT / "app/build/intermediates/merged_manifests" / variant
    matches = sorted(directory.glob("*/AndroidManifest.xml"))
    if len(matches) != 1:
        raise AssertionError(f"{variant}: expected one merged manifest, found {len(matches)}")
    return matches[0]


def verify(variant: str) -> None:
    root = ET.parse(merged_manifest(variant)).getroot()
    application = root.find("application")
    if application is None:
        raise AssertionError(f"{variant}: application missing")
    launchers = [
        node
        for tag in ("activity", "activity-alias")
        for node in application.findall(tag)
        if is_launcher(node)
    ]
    by_name = {attr(node, "name"): node for node in launchers}
    if len(by_name) != len(launchers):
        raise AssertionError(f"{variant}: duplicate launcher component")
    actual_primary = set(by_name).intersection(PRIMARY)
    if actual_primary != PRIMARY:
        raise AssertionError(f"{variant}: primary launcher set {sorted(actual_primary)}")
    enabled_primary = {name for name in PRIMARY if enabled(by_name[name])}
    if enabled_primary != {DEFAULT}:
        raise AssertionError(f"{variant}: enabled primary set {sorted(enabled_primary)}")
    for name in PRIMARY:
        node = by_name[name]
        if attr(node, "targetActivity") != "io.codecks.MainActivity":
            raise AssertionError(f"{variant}: wrong primary target for {name}")
        if attr(node, "exported") != "true":
            raise AssertionError(f"{variant}: non-exported primary {name}")

    internal = variant.startswith("playInternal")
    expected_all = PRIMARY | ({LAB} if internal else set())
    if set(by_name) != expected_all:
        raise AssertionError(f"{variant}: unclassified launcher set {sorted(by_name)}")
    if internal:
        lab = by_name[LAB]
        if not enabled(lab) or attr(lab, "targetActivity") != (
            "io.codecks.internalcommercial.labui.CommercialLabActivity"
        ):
            raise AssertionError(f"{variant}: invalid Commercial Lab classification")


def main() -> int:
    try:
        for variant in VARIANTS:
            verify(variant)
            print(f"PASS {variant}")
    except (AssertionError, ET.ParseError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
