# Codecks v0.1.21 release notes

Date: July 23, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.21

## Summary

Codecks v0.1.21 fixes the Mac SSH regression in v0.1.20 by permanently disabling release APK code minification and resource shrinking.

## Fix

- Disabled R8 code minification for the production release.
- Disabled Android resource shrinking for the production release.
- Removed the narrow v0.1.20 JSch keep-rule strategy from the active release path.
- Added build and CI failures if release shrinking is re-enabled.
- Restored a physical-phone, real-Mac SSH smoke test for the exact signed release APK.

## Release rule

Release shrinking may not be re-enabled without explicit user approval and a successful physical SSH test of the exact signed candidate.

## Verification

Pre-publish gates:

- Public-source privacy and AI corpus checks.
- Release unit tests and Android lint.
- Signed, unshrunk release build and managed-emulator startup.
- APK signing certificate and checksum verification.

Post-publish device gate:

- Install the exact published APK in place after verifying its signing certificate.
- Run the physical-phone, real-Mac SSH smoke without uninstalling or clearing `app.codecks`.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.20...v0.1.21
