# Codecks v0.1.28 release notes

Date: July 29, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.28

## Summary

Codecks v0.1.28 is a daily-friction cleanup release for the Deck, Trackpad,
Keyboard, Clipboard, and editor experience. It also replaces the old launcher
icon with the new robot-face control-pad icon.

## Changes since v0.1.27

- Added a Trackpad dashboard overlay for sample rate, jitter, dispatch time,
  last gesture, link state, and touch count.
- Added an explicit Trackpad Controls button and made Back leave Trackpad
  instead of reopening controls.
- Added a four-finger hold shortcut from Trackpad to Deck.
- Improved Keyboard send guards and disconnected-state copy.
- Improved Clipboard empty/offline states and hid meaningless empty hashes.
- Improved Rules rows so unavailable actions say they need a Mac connection.
- Added persisted colored blank Deck buttons through the editor color picker.
- Moved bundled decorative celebration actions to Mac-side SSH overlay actions
  instead of phone-local celebration effects.
- Prevented debug builds from auto-registering HID on launch.
- Replaced the launcher icon with the robot-face control-pad icon.
- Kept production minification and resource shrinking disabled.

## Validation

- Debug APK build passed.
- Debug lint passed.
- Shared JVM tests passed.
- Debug APK installed side-by-side on the physical phone as `app.codecks.debug`.
- Debug Deck, Trackpad setup, Bluetooth permission path, and color-picker editor
  smoke tests passed on the physical phone.
- Release publication is built by the GitHub release workflow from this tag.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.27...v0.1.28
