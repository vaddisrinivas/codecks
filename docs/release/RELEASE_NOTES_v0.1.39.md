# Codecks v0.1.39 release notes

Date: September 22, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.39

## Summary

Codecks v0.1.39 makes the Android control deck faster to navigate, more
personal, and more context-aware. It adds adaptive drawer navigation, Quick
Deck, deeper theme and icon customization, and a broad local-first Context Deck
toolkit.

## Navigation and personalization

- Added an adaptive navigation drawer with direct access to destinations and a
  compact Quick Deck.
- Added Theme Studio, custom themes, a theme library, and wider system-surface
  theme propagation.
- Added selectable launcher icon packs.
- Hardened large-screen, compact-screen, and accessibility behavior.

## Context Deck

- Added app-follow offers and a local context strip without silently
  rearranging the user's Deck.
- Added modifier layers, live-state controls, and analog controls for values
  such as volume and brightness.
- Added a current-Space window map, bounded File Drop, and an editable workflow
  recorder.
- Added explicit Multi-Mac handoff and an opt-in four-control lock-screen Mini
  Deck.

## Mac pairing and resilience

- Added an experimental native Mac helper and one-use QR pairing flow.
- Added bounded persistence, action assurance, connection recovery, and
  privacy-safe support diagnostics.
- Kept free-form and bundled command safety paths distinct.

## Validation

- Unit tests and Android lint passed in CI.
- The managed Android matrix passed on compact, standard, and tablet profiles
  across API 31 through API 36: 18 shards, 0 failures.
- The adaptive drawer and Quick Deck managed-emulator suite passed.
- The full release-flavor Pixel 6 API 35 suite passed: 46 tests, 0 failures,
  with one intentional live-Mac skip.
- Release privacy, documentation, commercial-boundary, bundled Mac action, and
  no-shrink guardrails passed.
- Production code minification and resource shrinking remain disabled.

## Public beta limits

- No physical-phone, live-Mac SSH, or real DeX validation was run for this
  release cycle.
- The long M16 168-hour soak is not complete.
- Commercial features remain disabled.

## Assets

- `codecks-release.apk`: production-signed, unshrunk Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.37...v0.1.39
