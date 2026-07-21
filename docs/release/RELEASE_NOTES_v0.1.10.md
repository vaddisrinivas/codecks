# Codecks v0.1.10 release notes

Date: July 21, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.10

## Summary

Codecks v0.1.10 is a product-shape release for the renamed Codecks app: it cleans up Trackpad, promotes Keyboard and Clipboard to first-class navigation tabs, and makes the AI builder skill-driven without turning it into a generic chat transcript.

## Highlights

- Renamed the app package and source namespace to Codecks.
- Reworked bottom navigation into a horizontally scrollable pinned rail that can support configurable future tabs.
- Made Keyboard and Clipboard own bottom-navigation tabs instead of Trackpad submodes.
- Simplified Trackpad into a focused pointer surface; connected target chrome is hidden after connection.
- Kept SSH and Bluetooth HID flows while adding an adb HID smoke script for target-picker regression checks.
- Reworked AI builder around skill + instruction + artifact preview; accepts save generated controls disabled/unverified until tested.
- Split AI provider settings from workspace content.
- Removed dead release surfaces such as widget/paywall/premium/activity/advanced screens from the default public app.

## Release assets

- `codecks-v0.1.10.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

## Verification

- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:check`
- `validateReleaseSurface`
- Physical-device debug smoke checks for Trackpad, Keyboard, Clipboard, and AI surfaces.
