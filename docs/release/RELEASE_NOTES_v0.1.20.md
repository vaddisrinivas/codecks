# Codecks v0.1.20 release notes

Date: July 23, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.20

## Summary

Codecks v0.1.20 hardens Smart Suggestions, Mac identity privacy, action-result correctness, and the signed release pipeline.

## Changes since v0.1.19

- Smart local actions now return typed results and record only accepted, completed execution.
- Busy and rejected runs cannot be credited as successful.
- Completion feedback remains bound to the Mac and app context present when execution began.
- Bluetooth media suggestions require current Mac input connectivity.
- Smart orchestration is dependency-injected and covered by behavioral tests.
- Transition learning, temporary hide, context suppression, and global suppression have separate deterministic semantics.
- Real schema-v1 Smart history migrates to schema v2 without retaining legacy endpoint-derived Mac identity.
- Mac targets use persisted random UUIDs instead of username, hostname, and port-derived IDs.
- Saved Deck and Rule target selectors migrate to UUIDs; corrupt target data is preserved and quarantined rather than overwritten.
- Smart app mappings validate their schema and fail closed on unsupported data.
- Production R8 shrinking and obfuscation are active with narrow keep rules.
- The release workflow verifies exact-tag provenance, uses least-privilege permissions, publishes only the product APK, and keeps test APKs internal.
- The exact signed/minified product APK must cold-start on a managed release emulator; functional Smart, identity, and Trackpad tests run separately against debug.
- Repository agent rules forbid uninstalling or clearing the production app during testing without explicit approval.

## Verification

- Release unit suite: 341 tests, 0 failures/errors, 2 skipped.
- Signed/minified release managed-emulator startup: 1 passed.
- Debug managed-emulator functional suite: 11 passed, 1 live-SSH test skipped.
- Android build, lint, architecture, privacy, AI evaluation, bundled Mac action, shell, workflow, APK signature, checksum, and R8 gates passed.

The emulator release gate does not claim a live Mac/SSH transport test.

## Assets

- `codecks-release.apk`: production-signed Codecks APK.
- `SHA256SUMS.txt`: checksum for release verification.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.19...v0.1.20
