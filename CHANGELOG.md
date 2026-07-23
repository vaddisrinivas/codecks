# Changelog

## 0.1.17 — 2026-07-23

Release hardening and navigation cleanup.

- Fixed the release-only SSH crash by keeping JSch's Android/JCE implementation classes through R8.
- Changed phone navigation to four pinned destinations plus More: Deck, Trackpad, Keyboard, Clipboard, More.
- Added a large-screen navigation rail for DeX/tablet layouts.
- Reorganized Settings around Setup, Mac, Data and privacy, Control surfaces, Build, Appearance, and Support.
- AI Builder now advertises only capabilities reported by currently ready devices.
- AI draft previews are persisted before the workspace can clear them.
- Smart Suggestions no longer expose Run for untested AI-generated commands.
- Background-triggered Rules now fail closed until their current revision has a successful test.

## 0.1.16 — 2026-07-22

Upgrade cleanup release.

- Added a compatibility shim for old pre-Codecks WorkManager automation jobs so upgraded installs do not log a missing legacy worker class.
- Kept the current automation scheduler intact; stale legacy jobs refresh into the current Codecks worker and finish safely.

## 0.1.15 — 2026-07-22

Smart Deck correctness release.

- Smart Run feedback now records success/failure from the final action result instead of scoring on tap.
- Smart candidate expiry now uses a real clock and refresh tick.
- Smart Mac identity is stored as a short local hash, not `user@host:port`.
- Smart ranking sees all actions before pinned Deck buttons are filtered from the suggestion row.
- Added transition learning so successful button sequences can suggest the likely next button.
- Smart cards now show Run and Pin directly, with Why/Hide/Never in an overflow menu.
- Added Clear smart history under Settings when Smart flags are enabled.
- Notification access/privacy is no longer coupled to the Smart Deck flag.
- Updated v0.1.14 GitHub release notes body with the full in-repo notes.

## 0.1.14 — 2026-07-22

Smart foundation release.

- Added default-off Smart flags for Deck, Keyboard, Clipboard, Rules, Settings, Trackpad suggest/snap, and OCR.
- Added Smart system, privacy, and evaluation docs.
- Added a pure deterministic Smart engine with typed context, candidates, risks, confidence labels, policy, expiry, and unavailable states.
- Added bounded local learning that stores only IDs, coarse buckets, success/failure, and sanitized context keys.
- Added a temporary Smart Deck suggestion row behind Smart flags: Run, Pin, Hide, Why, and Never for this app.
- Removed the old Context Deck/App AI ranking path so local Smart ranking never calls an LLM.
- Kept pinned Deck buttons fixed; Smart can only pin into an empty slot after the user taps Pin.
- Verified unit tests, lint/check, managed emulator tests, debug build, and bundled Mac action verifier.

## 0.1.13 — 2026-07-22

Safety hardening release.

- Added revision-bound review/test state for custom Mac commands and Rules.
- Fixed reviewed custom-command execution through the SSH path.
- Kept accepted AI buttons and Rules disabled/unverified until tested.
- Added emulator and macOS action-verifier gates to release workflows.

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
