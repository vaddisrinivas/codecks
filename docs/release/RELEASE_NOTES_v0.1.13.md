# Codecks v0.1.13 release notes

Date: July 22, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.13

## Summary

Codecks v0.1.13 hardens AI-created and user-authored Mac commands, fixes the final custom-command execution path, tightens Rule verification, and moves release checks onto real emulator and macOS verifier gates.

## Command safety

- Added command origin, reviewed revision, tested revision, risk reason, confirmation copy, and execution authorization to Deck actions.
- Added SHA-256 revision fingerprints for Deck actions and Rules.
- Changed reviewed custom commands to use a reviewed-command SSH path instead of the strict bundled safe-template path.
- Kept destructive/exfiltration command blocking in the final execution gateway.
- Dangerous command confirmation is now revision-bound instead of a plain boolean.
- Editing a command, target, risk, or confirmation metadata invalidates prior review/test state.

## AI Builder and Rules

- Accepted AI Deck buttons remain disabled/unverified until tested.
- Accepted AI Rules save disabled/manual and carry reviewed-but-untested revision state.
- User-created Rules save with a matching review revision after explicit edit/save.
- Known blocked Rule commands are rejected instead of being stored as dangerous drafts.
- Successful Rule testing is still required before enabling.

## Trackpad

- Reset fast and precision scroll accumulators between gestures.
- Prevented multi-finger hold callbacks from firing more than once for the same gesture.
- Kept regression coverage for two-finger double tap, three/four-finger holds, and fast/slow scroll rails.

## Release gates

- Added a Gradle managed Pixel 6 API 35 emulator test target.
- Quality and release workflows now run managed emulator tests.
- Quality and release workflows upload test reports on failure.
- Added macOS CI verification for bundled Mac action JSON, shell syntax, AppleScript compilation, and required tools.
- Release workflow now checks out and builds the exact requested tag.

## Verification

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:check`
- `./gradlew -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect :app:pixel6Api35DebugAndroidTest`
- `python3 scripts/verify_mac_actions.py`

## Release assets

- `codecks-v0.1.13.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.12...v0.1.13
