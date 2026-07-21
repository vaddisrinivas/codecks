# Changelog

## 0.1.6 — 2026-07-21

Classic deck layout preservation and release-note cleanup.

- Preserved the existing four-column luminous deck layout shown in the current installed phone build.
- Kept the wide Trackpad key plus side Keys/automation-style layout rather than switching to a cramped alternative.
- Sanitized public release notes and documentation to avoid device-specific wording.
- Added canonical `v0.1.5` release notes in the repo and updated release/distribution docs.
- Rebuilt and republished signed APKs/checksums from a green `main`.

## 0.1.5 — 2026-07-21

Codex Cockpit and fancy deck preview release.

- Added a Codecks v2 Codex Cockpit preview APK alongside the signed root APK.
- Added fancy deck buttons, emoji/effect/theme controls, guarded action ids, and local persistence for button metadata.
- Added local-first Codex snapshot protocol, example payloads, Mac emitters, and a local HTTP bridge server.
- Added sanitized local Codex metadata snapshots that avoid prompt/source/session body/tool-output content by default.
- Added compact phone bottom navigation so the Codex tab is reachable.
- Updated release packaging to upload both APKs plus `SHA256SUMS.txt`.
- Verified local release gates, GitHub Quality, GitHub Release workflow, release assets, and checksums.
- Caveat: emulator bridge/render proof exists; physical Android-device proof is still pending.

## 0.1.4 — 2026-07-20

Trackpad correction and redacted diagnostics beta.

- Improved Trackpad tap correction.
- Added redacted HID diagnostics.
- Published a signed APK and checksum through the release workflow.

## 0.1.2 — 2026-07-17

AI Creator V2 and release automation beta.

- Replaced prompt-shaped AI drafts with strict typed V2 envelopes.
- Added provider-native structured output for OpenAI, Gemini, Anthropic, and OpenAI-compatible providers.
- Added semantic validation, deterministic compilation, bounded repair, proposal review, dry-run/test-before-save, and generation history.
- Added 120-prompt local eval corpus plus opt-in live provider release gate.
- Added GitHub Actions signed APK release workflow and release artifact checksums.

## 0.1.0 — 2026-07-17

First public local-only beta.

- Full-screen customizable Mac command deck.
- Bluetooth HID trackpad with gestures, haptics, controls, and screen-pinning option.
- Local automation builder with safe templates, test-before-enable, and run history.
- Manual deck editing plus optional AI-assisted button and automation drafting.
- Adaptive phone, tablet, landscape, freeform, and Samsung DeX layouts.
- Encrypted AI and SSH credential storage.
- No Codecks account, backend, database, analytics, ads, or cloud sync.
