# Codecks v0.1.8 release notes

Published: 2026-07-21

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.8

## Summary

Codecks v0.1.8 keeps the one-APK release model and adds the missing playful deck pieces directly to the stable app.

## What changed

- Added safe decorative deck buttons: `🎉 Confetti`, `✨ Sparkle`, `💚 Good`, `🔥 Hot`, `🎯 Focus`, `☕ Break`, `Blank`, and `Magic`.
- Added a `Decor` category to Edit Deck.
- Added a lightweight full-screen celebration overlay for celebration/decor buttons.
- Fixed action-library icon sizing so icon packs cannot create giant cropped rows.
- Removed Font Awesome from the visible icon-pack picker and normalize saved Font Awesome settings back to Tabler.
- Exposed the existing richer deck styles in Settings > Look.
- Continued publishing exactly one signed APK plus `SHA256SUMS.txt`.

## Artifacts

- `codecks-v0.1.8.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

## Verification plan

- Public-source privacy check.
- AI Creator V2 local corpus check.
- Root Android test/lint/release-surface checks.
- Signed APK build.
- GitHub Release workflow asset publication.

## Known caveat

- Physical Android-device visual proof was limited because the device lock screen blocked app screenshots during this pass. Install proof exists; unlocked UI proof should be captured next.

## Full changelog

https://github.com/vaddisrinivas/codecks/compare/v0.1.7...v0.1.8
