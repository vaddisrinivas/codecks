# Codecks v0.1.25 release notes

Date: July 28, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.25

## Summary

Codecks v0.1.25 is a helper-pairing hotfix for v0.1.24. The Reactive Mac
helper code shipped in v0.1.24, but the external `codecks://helper-pair` route
was not declared in the Android manifest. This release exposes that route to
`MainActivity` so helper pairing links can actually reach the importer.

## Changes since v0.1.24

- Added the `codecks://helper-pair` public VIEW/BROWSABLE intent filter to
  `MainActivity`.
- Kept `codecks://trackpad` routed to the restricted lockscreen Trackpad entry.
- Isolated Mac helper runtime tests from any helper config installed on the
  developer Mac.
- Kept production minification and resource shrinking disabled.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.24...v0.1.25
