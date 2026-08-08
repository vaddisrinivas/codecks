#!/usr/bin/env python3
"""Fail-closed DEX boundary check for Codecks distribution artifacts."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import zipfile


BANNED_COMMERCIAL_NAMESPACES = (
    b"com/android/billingclient/",
    b"com/google/android/gms/ads/",
    b"com/google/android/ump/",
    b"com/google/android/play/core/integrity/",
    b"com/google/android/play/integrity/",
    b"com/google/firebase/auth/",
    b"com/google/firebase/remoteconfig/",
    b"com/google/firebase/firestore/",
    b"com/google/firebase/functions/",
    b"com/google/firebase/analytics/",
)
INTERNAL_OVERRIDE_MARKER = b"io/codecks/internalcommercial/CommercialTestOverrideMarker"
INTERNAL_OVERRIDE_NAMESPACE = b"io/codecks/internalcommercial/"
DEFAULT_INTERNAL_BACKEND = "https://codecks.invalid"

EXPECTED_POLICY = {
    "ossRelease": b"ABSENT",
    "playRelease": b"PRODUCTION_DARK",
    "playInternalRelease": b"INTERNAL_TEST_CAPABLE",
}
EXPECTED_MANIFEST = {
    "ossRelease": (b"oss", b"absent", b"app.codecks"),
    "playRelease": (b"play", b"production_dark", b"app.codecks"),
    "playInternalRelease": (b"play_internal", b"internal_test_capable", b"app.codecks.internal"),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--variant", choices=sorted(EXPECTED_POLICY), required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--internal-backend-url", default=DEFAULT_INTERNAL_BACKEND)
    return parser.parse_args()


def fail(message: str) -> None:
    print(f"commercial artifact validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def manifest_contains(manifest: bytes, value: bytes) -> bool:
    """Android's binary XML string pool is commonly UTF-16, unlike DEX."""
    decoded = value.decode("utf-8")
    return any(
        candidate in manifest
        for candidate in (value, decoded.encode("utf-16le"), decoded.encode("utf-16be"))
    )


def main() -> None:
    args = parse_args()
    if not args.artifact.is_file():
        fail(f"artifact missing: {args.artifact}")
    try:
        with zipfile.ZipFile(args.artifact) as archive:
            dex_names = sorted(name for name in archive.namelist() if name.endswith(".dex"))
            if not dex_names:
                fail("artifact contains no DEX payload")
            dex = b"".join(archive.read(name) for name in dex_names)
            manifest_names = sorted(
                name for name in archive.namelist() if name.endswith("AndroidManifest.xml")
            )
            if not manifest_names:
                fail("artifact contains no Android manifest payload")
            manifest = b"".join(archive.read(name) for name in manifest_names)
    except zipfile.BadZipFile:
        fail("artifact is not a readable APK/AAB ZIP")

    if args.variant != "playInternalRelease":
        leaked = [value.decode("ascii") for value in BANNED_COMMERCIAL_NAMESPACES if value in dex]
        if leaked:
            fail(f"commercial namespaces leaked into public DEX: {', '.join(leaked)}")
    expected_policy = EXPECTED_POLICY[args.variant]
    if expected_policy not in dex:
        fail(f"missing compiled policy marker {expected_policy.decode('ascii')}")

    expected_distribution, expected_manifest_policy, expected_application_id = EXPECTED_MANIFEST[args.variant]
    for label, value in (
        ("distribution", expected_distribution),
        ("commercial policy", expected_manifest_policy),
        ("application ID", expected_application_id),
    ):
        if not manifest_contains(manifest, value):
            fail(f"manifest is missing expected {label} marker {value.decode('ascii')}")

    marker_present = INTERNAL_OVERRIDE_MARKER in dex
    internal_namespace_present = INTERNAL_OVERRIDE_NAMESPACE in dex
    internal_backend = args.internal_backend_url.encode("utf-8")
    backend_present = manifest_contains(manifest, internal_backend)
    if args.variant == "playInternalRelease":
        if not marker_present:
            fail("playInternalRelease lacks its compile-time override marker")
        if not internal_namespace_present:
            fail("playInternalRelease lacks its isolated override namespace")
        if not backend_present:
            fail("playInternalRelease lacks its inert test-backend placeholder")
    else:
        if internal_namespace_present:
            fail(f"{args.variant} contains internal override implementation")
        if backend_present:
            fail(f"{args.variant} contains the internal test-backend placeholder")

    print(f"validated {args.variant}: DEX policy and SDK boundaries are clean")


if __name__ == "__main__":
    main()
