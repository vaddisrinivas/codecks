# Google Play package draft — not submitted

Status: `DRAFT_ONLY`. This file does not authorize Play Console access, upload,
Data Safety submission, commercial activation, or staged rollout.
External Play Console and publication state are `NOT_VERIFIED`; repository
documents are not evidence of external console state.

## Listing copy

- App name: Codecks
- Short description: Android command deck, trackpad, and local Mac automation surface.
- Category: Productivity
- Minimum Android: Android 9 / API 28
- Support URL: https://github.com/vaddisrinivas/codecks/issues
- Privacy URL: repository `PRIVACY.md`

Use `fastlane/metadata/android/en-US/full_description.txt` as the long
description. Use the eight 1080x2400 PNGs in
`fastlane/metadata/android/en-US/images/phoneScreenshots/`. Tablet, DeX,
signed-release install, and live paired-Mac captures remain required review
assets; do not fabricate them.

## Data Safety draft

Public production defaults create no Codecks account and use no Codecks backend,
analytics SDK, advertising SDK, public database, or cloud sync. Core Deck,
Trackpad, Keyboard, Rules, and local backup data stay on the device. User-chosen
Mac commands, clipboard text, and Bluetooth HID reports are sent only to the
configured Mac/host for the requested feature. Optional AI prompts are sent
directly to the provider selected by the user; the provider's policy applies.

Before submission, regenerate a dependency/manifest/network audit for the exact
Play AAB. Console answers must match that artifact and current Google form
wording; this draft is not an accepted declaration.

## Account deletion declaration

Codecks creates no server account, so there is no remote account-deletion flow.
Users delete local data through Android Settings or uninstall, and separately
delete exported backups. If account creation is ever enabled, in-app and web
deletion must ship before any account-capable track.

## Policy and rollout draft

- Commercial sign-in, sync, Billing, premium enforcement, ads, and their public
  network startup pass the current-source static production-dark harness. Repeat
  artifact/runtime checks for the exact AAB before any upload.
- Upload only an exact source/checksum/signer-bound AAB after owner approval.
- Internal testing first; then closed testing; production remains unauthorized.
- Each promotion requires crash/ANR, policy, privacy, accessibility, migration,
  rollback, support, and exact-artifact review.
- Halt on P0/P1, signer/source mismatch, unexpected collection/network traffic,
  migration loss, or commercial-dark violation.
- No percentage, date, country, tester list, or production promotion is approved
  by this draft.
