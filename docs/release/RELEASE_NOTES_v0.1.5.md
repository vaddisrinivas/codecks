# Codecks v0.1.5 release notes

Published: 2026-07-21

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.5

## Summary

Codecks v0.1.5 packages the new Codecks v2 Codex cockpit preview alongside the existing signed Codecks APK.

## What's new

- Added a Codex Cockpit preview for monitoring agent/task work from Android.
- Added a fancy deck layer with expressive buttons, emoji-forward controls, status styling, glow/effect presets, and playful completion affordances.
- Added editable/persisted custom button metadata for label, status, action id, emoji/effect/theme/safety settings.
- Added guarded bridge actions for safe local snapshot fetches only.
- Added a local-first Codex bridge protocol and example snapshot schema.
- Added Mac helper scripts for mock, release, and sanitized local Codex metadata snapshots.
- Added a local HTTP bridge endpoint for Android/emulator development.
- Added compact phone bottom navigation so the Codex tab is reachable on smaller layouts.
- Added cleartext network allowlisting only for local development bridge hosts.

## Privacy and safety

- Local Codex metadata bridge reads status metadata only.
- Prompt/source/session bodies and tool outputs are intentionally excluded by default.
- Fancy button action execution is guarded; unknown custom action ids are saved but not executed.

## Artifacts

- `codecks-v0.1.5.apk`: signed root Codecks APK.
- `codecks-v2-cockpit-preview-v0.1.5.apk`: Codecks v2 cockpit preview debug APK.
- `SHA256SUMS.txt`: checksums for both APKs.

## Checksums

```text
2ae53623203bbb73f93f05093556ee8bc56a8928ef23afb8d0c66d449a4407fc  codecks-v0.1.5.apk
2711c3b8b2dbf780227d881bd4060e1e8f5f356e19e82326e33a37c1ac255daa  codecks-v2-cockpit-preview-v0.1.5.apk
```

## Verification

- Local privacy scan passed.
- Local AI Creator V2 corpus check passed.
- Root Android `:app:check` passed.
- Nested Codecks v2 build/test/release-boundary suite passed.
- GitHub Quality run for the release-prep commit passed.
- GitHub Release workflow for `v0.1.5` passed and uploaded both APKs plus checksums.
- Post-release `main` Quality run passed.

## Known caveat

- Emulator bridge/render proof exists; physical Android-device proof is still pending.

## Full changelog

https://github.com/vaddisrinivas/codecks/compare/v0.1.4...v0.1.5
