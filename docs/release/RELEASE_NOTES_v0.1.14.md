# Codecks v0.1.14 release notes

Date: July 22, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.14

## Summary

Codecks v0.1.14 starts the Smart system safely: local-only, deterministic, default-off, and visible first as a temporary Deck suggestion row.

## Smart foundation

- Added Smart system, privacy, and evaluation docs.
- Added default-off Smart flags for suggestions, Deck, Keyboard, Clipboard, Rules, Settings, Trackpad suggest/snap, and OCR.
- Added pure Smart domain contracts for context, candidates, feedback, decisions, policy, risks, capabilities, expiry, and unavailable states.
- Added deterministic local ranking from recent actions, current Mac app, connection readiness, and local feedback.
- Added bounded local learning with retention and corruption recovery.

## Smart Deck MVP

- Added a temporary Deck-only suggestion row behind `SmartSuggestions` + `SmartDeck`.
- Suggestions support Run, Pin, Hide, Why, and Never for this app.
- Smart never rearranges pinned Deck buttons.
- Pin only fills an empty Deck slot after user tap.
- Why uses product confidence labels: Very likely, Likely, Possible.

## Removed old Context Deck path

- Removed the old Context Deck flag and god-model storage.
- Removed old app ranking, usage ranking, prompt building, and context-app AI draft mode.
- Local Smart ranking never calls an LLM and does not need an AI key.

## Verification

- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:check`
- `./gradlew -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect :app:pixel6Api35DebugAndroidTest`
- `python3 scripts/verify_mac_actions.py`
- `./gradlew :app:assembleDebug`

## Release assets

- `codecks-v0.1.14.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.13...v0.1.14
