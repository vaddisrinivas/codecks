# Codecks v0.1.16 release notes

Date: July 22, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.16

## Summary

Codecks v0.1.16 is a small upgrade-cleanup release after v0.1.15.

## Fix

- Added a compatibility shim for stale pre-Codecks WorkManager automation jobs.
- Upgraded installs that still have an old `io.codex.s23deck...AutomationTriggerWorker` row now refresh into the current Codecks automation worker and finish safely instead of logging a missing legacy class.
- No user Deck, Mac, Trackpad, Keyboard, Clipboard, Rules, AI Builder, or Settings data is removed.

## Verification

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:check`
- `python3 tools/secret_surface_check.py`
- `python3 scripts/verify_mac_actions.py`
- `./gradlew :app:assembleDebug`
- `./gradlew -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect :app:pixel6Api35DebugAndroidTest`

## Release assets

- `codecks-v0.1.16.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.15...v0.1.16
