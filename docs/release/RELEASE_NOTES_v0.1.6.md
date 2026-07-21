# Codecks v0.1.6 release notes

Published: 2026-07-21

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.6

## Summary

Codecks v0.1.6 preserves the existing installed deck layout and republishes the current Codecks APKs after documentation and release-note cleanup.

## What's new

- Preserved the classic four-column luminous deck layout from the current installed phone build.
- Kept the wide Trackpad key and side utility key layout.
- Sanitized public release notes and tracked documentation to use generic physical Android-device wording.
- Added canonical in-repo release notes for `v0.1.5`.
- Kept the Codecks v2 Codex Cockpit preview packaged as a separate debug APK.

## Artifacts

- `codecks-v0.1.6.apk`: signed root Codecks APK.
- `codecks-v2-cockpit-preview-v0.1.6.apk`: Codecks v2 cockpit preview debug APK.
- `SHA256SUMS.txt`: checksums for both APKs.

## Verification plan

- Public-source privacy check.
- AI Creator V2 local corpus check.
- Root Android test/lint/release-surface checks.
- Signed root APK build.
- Codecks v2 cockpit preview APK build.
- GitHub Release workflow asset publication.

## Known caveat

- Emulator bridge/render proof exists; physical Android-device proof is still pending.

## Full changelog

https://github.com/vaddisrinivas/codecks/compare/v0.1.5...v0.1.6
