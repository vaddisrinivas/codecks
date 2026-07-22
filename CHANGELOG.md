# Changelog

## 0.1.12 — 2026-07-22

Trackpad gesture, Mac action, and AI command reliability release.

- Added configurable two-, three-, and four-finger double-tap and hold actions.
- Added separate fast and precision scroll rails with adjustable slow speed and acceleration.
- Fixed trackpad overlays blocking gestures and prevented rail releases from producing stray clicks.
- Fixed bundled Mac actions, explicit dangerous-action confirmation, and Bluetooth HID media controls.
- Allowed AI to propose reviewable custom commands while retaining hard command blocking, concrete risk explanations, and disabled-until-tested saves.
- Simplified Settings into Mac actions and Mac input, removing the duplicate readiness section.
- Refreshed the untouched default Deck with decorative controls while preserving customized Decks.
- Added emulator interaction coverage and bundled Mac action syntax verification.

## 0.1.9 — 2026-07-21

UI rescue and creation-flow cleanup release.

- Promoted button creation in the drawer so `Create button` and `AI builder` appear before setup-heavy sections.
- Moved the custom emoji/decor button composer to the top of Edit Deck.
- Reordered Edit Deck so making or assigning a button comes before layout management.
- Simplified Automations into a clearer Rules surface with a primary New Rule action.
- Made Settings open directly to Look so theme/icon choices are not buried behind setup.
- Reworked Bluetooth setup copy and removed clipped target rows in the unconfigured state.
- Kept Codecks as one signed APK; no alternate preview/debug APK is published.

## 0.1.8 — 2026-07-21

Fancy deck and editor cleanup release.

- Added safe decorative deck buttons: confetti, sparkle, emoji markers, blank spacer, and magic tile.
- Added a Decor category in Edit Deck so decorative buttons are easy to find.
- Added an in-app celebration overlay for decorative celebration buttons.
- Fixed the action-library icon row so icon packs cannot render giant cropped glyphs.
- Hid the broken Font Awesome option and normalize saved Font Awesome settings back to Tabler.
- Exposed the existing richer deck styles in Settings > Look.
- Kept Codecks as one signed APK; no alternate preview/debug APK is published.

## 0.1.7 — 2026-07-21

One-APK cleanup release.

- Removed the nested alternate Android app and related prototype bridge/protocol files from the public source tree.
- Updated release automation to publish exactly one signed APK plus `SHA256SUMS.txt`.
- Removed stale docs/evidence/planning records that pointed users toward the deleted alternate app.
- Kept Codecks focused on the stable local-first command deck, trackpad, automation, settings, and optional AI drafting app.

## 0.1.6 — 2026-07-21

Classic deck layout preservation and release-note cleanup.

- Preserved the existing four-column luminous deck layout shown in the current installed phone build.
- Kept the wide Trackpad key plus side Keys/automation-style layout rather than switching to a cramped alternative.
- Sanitized public release notes and documentation to avoid device-specific wording.
- Added canonical `v0.1.5` release notes in the repo and updated release/distribution docs.
- Rebuilt and republished the single signed APK/checksum from a green `main`.
- Removed the oversized alternate debug APK from the public release surface; Codecks now ships one APK.

## 0.1.5 — 2026-07-21

Stable one-APK beta cleanup.

- Kept prototype experiments out of the public install surface.
- Updated release packaging to upload the signed APK plus `SHA256SUMS.txt`.
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
