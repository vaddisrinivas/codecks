# Codecks v0.1.23 release notes

Date: July 27, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.23

## Summary

Codecks v0.1.23 improves daily Trackpad and Keyboard use, adds a deliberately
restricted lockscreen pointer surface, and lands buildable cross-platform
Reactive protocol foundations without enabling an unfinished Mac-helper
product surface.

## Daily-use improvements

- Deck actions no longer leave persistent `completed:` text beside buttons.
- Keyboard Send now delivers the text followed by exactly one Enter.
- The Keyboard input clears only after successful delivery.
- Dedicated Enter and Command+Enter controls remain available below the
  composer.
- Trackpad overlays consume their own touches instead of leaking gestures to
  the pointer surface.
- Back closes the active Trackpad tray before leaving the Trackpad.
- Trackpad controls have clearer accessibility roles, labels, and selected
  states.

## Desk and lockscreen entry

- Added the exact public route `codecks://trackpad`.
- Added Tasker BT Near, charging, face-up, and NFC setup guidance.
- Added a home-screen widget and generic HID-notification entry.
- Added a restricted lockscreen Trackpad that permits only pointer movement,
  scrolling, mouse buttons, and button release.
- Keyboard, Deck actions, Reactive actions, clipboard, settings, pairing,
  reconnect, and disconnect remain guarded behind device unlock.
- Pre-first-unlock, missing permission, missing host, disconnected HID, and
  policy-loss paths fail closed.

## Reactive Android foundation

- Added typed Mac-state, capability, control, action, result, receipt, and
  provider contracts.
- Added a deterministic default-off Reactive Trackpad prototype.
- Confirmation and review are bound to the resolved action revision.
- Execution rechecks expiry, state revision, capability availability, selected
  target, and current catalog action revision.
- Browser Reload now emits Command+R rather than Command+Enter.

## Cross-platform protocol foundation

- Added the `:shared` Kotlin Multiplatform module.
- Added typed helper handshake, request, response, capability, basic-state, and
  action-receipt models.
- Added bounded four-byte framing and strict schema/token/body validation.
- Added role-separated client/server proof transcripts.
- Added ordered request/response replay guards that advance only after
  authentication succeeds.
- Added hostile tests for replay, sequence gaps, wrong sessions, bad
  authentication, expired deadlines, oversized frames, partial frames, and
  mismatched helper identity.
- Added an Android helper-client scaffold with pinned Mac identity checks,
  server-proof verification, authenticated response validation, typed client
  state, discovery/persistence contracts, and bounded reconnect policy.
- Added buildable iOS ARM64 and iOS Simulator ARM64 `CodecksShared`
  frameworks. This is shared infrastructure, not a released iOS application.

## README evidence

- Added sourced phone-placement, smartphone-ownership, pointer, mousepad, and
  desk examples with visible limitations.
- Added a checked-in evidence snapshot and deterministic arithmetic verifier.
- Hardware and desk-space figures are illustrative manufacturer samples, not
  market averages or promised cash savings.

## Release safety

- Production minification and resource shrinking remain disabled.
- `app.codecks.debug` remains separate from protected production
  `app.codecks`.
- The release workflow runs shared JVM tests, Android checks, signed unshrunk
  APK verification, and managed-emulator release/debug suites.
- Public assets remain one signed APK plus `SHA256SUMS.txt`.

## Deferred

This release does not ship the native Mac helper, a complete pairing UI,
DeskDock scoring, Shortcuts/SFTP/brightness/Accessibility integrations, or an
iOS application.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.21...v0.1.23
