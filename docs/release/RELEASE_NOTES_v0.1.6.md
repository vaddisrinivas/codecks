# Codecks v0.1.6 release notes

Published: 2026-07-21

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.6

## Summary

Codecks v0.1.6 preserves the existing installed deck layout and republishes Codecks as one simple signed APK after documentation and release-note cleanup.

## What's new

- Preserved the classic four-column luminous deck layout from the current installed phone build.
- Kept the wide Trackpad key and side utility key layout.
- Sanitized public release notes and tracked documentation to use generic physical Android-device wording.
- Added canonical in-repo release notes for `v0.1.5`.
- Removed the oversized alternate debug APK from the public release surface.

## Artifacts

- `codecks-v0.1.6.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

## Verification plan

- Public-source privacy check.
- AI Creator V2 local corpus check.
- Root Android test/lint/release-surface checks.
- Signed root APK build.
- GitHub Release workflow asset publication.

## Known caveat

- Emulator bridge/render proof exists; physical Android-device proof is still pending.

## Full changelog

https://github.com/vaddisrinivas/codecks/compare/v0.1.5...v0.1.6
