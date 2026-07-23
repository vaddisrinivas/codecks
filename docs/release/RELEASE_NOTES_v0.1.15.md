# Codecks v0.1.15 release notes

Date: July 22, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.15

## Summary

Codecks v0.1.15 fixes the first Smart Deck correctness issues found after v0.1.14. Smart remains default-off, but its learning, freshness, privacy, and suggestion UI are safer.

## Smart learning fixes

- Smart no longer gives positive learning credit when the user only taps Run.
- Mac commands now record Smart success/failure only after the final Deck action result.
- Failed, blocked, unreviewed, unavailable, or rejected runs now teach failure instead of success.
- Local suggestions record success only after the local route/control is handled.
- Dangerous Smart suggestions do not learn success when the confirmation is canceled.
- Added transition scoring so successful action sequences can suggest likely next controls.

## Smart freshness and privacy

- Smart suggestions refresh on a clock tick while Deck is open.
- The engine now ranks with the actual current time, so expired contexts stop producing candidates.
- Persisted Smart context stores a short hashed Mac identity instead of `user@host:port`.
- Smart learning writes are guarded to avoid read/modify/write loss.
- Local hour buckets now use the phone’s local timezone.

## Smart Deck UI

- Suggestion cards show Run and Pin directly.
- Why, Hide, and Don’t suggest here moved into an overflow menu.
- The row shows at most three suggestions at first.
- Added a small Suggested / Local only label.
- Added Clear smart history under Settings when Smart flags are enabled.

## Settings and notification fix

- Notification access/privacy is no longer tied to the Smart Deck flag.
- Turning Smart Deck off no longer disables unrelated notification privacy controls.

## Verification

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:check`
- `python3 tools/secret_surface_check.py`
- `python3 scripts/verify_mac_actions.py`
- `./gradlew :app:assembleDebug`
- `./gradlew -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect :app:pixel6Api35DebugAndroidTest`
- Manual emulator launch confirmed Deck renders with visible default controls and no startup crash.

## Release assets

- `codecks-v0.1.15.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.14...v0.1.15
