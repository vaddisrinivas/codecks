# Codecks v0.1.9 release notes

Release date: July 21, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.9

## Summary

Codecks v0.1.9 is a UI rescue release focused on making the app feel like a creation/control cockpit instead of a setup console.

## What changed

- `Create button` and `AI builder` now lead the drawer before setup-heavy sections.
- Edit Deck now starts with the custom emoji/decor button composer.
- Edit Deck puts making or assigning buttons before resize/reorder controls.
- Automations now presents a simpler Rules surface with one primary `New rule` action.
- Settings opens to Look by default so deck style and icon choices are immediately visible.
- Bluetooth setup copy is shorter and the unconfigured target area no longer shows clipped host rows.

## Release artifacts

- `codecks-v0.1.9.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: SHA-256 checksum for the APK.

Codecks still ships as one APK only.

## Validation

- Android release workflow gates run before publication.
- Local debug UI proof was captured on an emulator before release.
- `:app:check` passed before tagging.

## Compare

https://github.com/vaddisrinivas/codecks/compare/v0.1.8...v0.1.9
