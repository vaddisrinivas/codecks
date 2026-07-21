# Codecks v0.1.7 release notes

Published: 2026-07-21

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.7

## Summary

Codecks v0.1.7 is a cleanup release that makes the product and repository match the intended install story: one simple signed APK.

## What changed

- Removed the nested alternate Android app and related prototype bridge/protocol files from the public source tree.
- Updated GitHub release automation to publish exactly one signed APK plus `SHA256SUMS.txt`.
- Removed stale docs/evidence/planning records that pointed at the deleted alternate app.
- Kept the stable Codecks app focused on Deck, Trackpad, Automations, Settings, editing, and optional AI-assisted drafting.

## Artifacts

- `codecks-v0.1.7.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

## Verification plan

- Public-source privacy check.
- AI Creator V2 local corpus check.
- Root Android test/lint/release-surface checks.
- Signed APK build.
- GitHub Release workflow asset publication.

## Known caveat

- Physical Android-device proof is still pending.

## Full changelog

https://github.com/vaddisrinivas/codecks/compare/v0.1.6...v0.1.7
