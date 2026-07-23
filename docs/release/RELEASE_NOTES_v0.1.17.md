# Codecks v0.1.17 release notes

Date: July 23, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.17

## Summary

Codecks v0.1.17 hardens Mac actions, AI-created drafts, Smart Suggestions, and Rules while simplifying navigation for phone and large-screen layouts.

## Fixes

- Fixed the release-only Mac action crash caused by R8 removing JSch Android/JCE classes such as `com.jcraft.jsch.jce.Random`.
- Background-triggered Rules now require a successful test for the current revision before running automatically.
- Smart Suggestions no longer show a live Run path for untested AI-generated commands.
- AI draft previews are saved before the workspace clears its current draft state.

## UI

- Phone navigation now uses `Deck`, `Trackpad`, `Keyboard`, `Clipboard`, and `More`.
- `More` contains Rules, AI Builder, Run history, and Settings.
- DeX/tablet layouts use a left navigation rail grouped by Control, Build, and Manage.
- Settings is reorganized into Setup, Mac, Data and privacy, Control surfaces, Build, Appearance, and Support.

## AI Builder

- AI generation now receives only capabilities reported by currently ready devices.
- HID capabilities are not advertised unless a device transport explicitly reports them.

## Verification

- `python3 tools/secret_surface_check.py`
- `python3 tools/ai_creator_v2_eval.py`
- `python3 scripts/verify_mac_actions.py`
- `./gradlew :app:check :app:assembleDebug`
- `./gradlew :app:assembleRelease` with local non-production signing properties
- Decompiled local minified release APK and confirmed `com.jcraft.jsch.jce.Random` and AES JCE classes are present.
- `./gradlew -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect :app:pixel6Api35DebugAndroidTest`
- Phone ADB debug install attempted; Samsung Maximum power saving suspended `app.codecks.debug`, so deeper physical debug-clone testing was blocked by device policy. Production `app.codecks` was restored and debug clone removed.

## Release assets

- `codecks-v0.1.17.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.16...v0.1.17
