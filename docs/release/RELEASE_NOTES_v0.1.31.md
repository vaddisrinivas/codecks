# Codecks v0.1.31 release notes

Date: July 30, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.31

## Summary

Codecks v0.1.31 repairs Deck management and navigation, makes catalog changes
immediate and recoverable, improves keyboard controls in compact layouts, and
hardens the experimental Reactive Mac helper and Apple Shortcuts runner.

## Deck and catalog

- Added in-place long-press management for Deck buttons, including offline
  buttons that cannot currently run.
- Made removal and resize operations persist immediately. The separate
  `Apply layout` state is gone.
- Added confirmation before forgetting an item from the catalog.
- Added complete undo for catalog removal, including restoration of its saved
  AI artifact.
- Prevented editing an active built-in template from silently mutating a hidden
  favorite. Editing now creates a visible Custom Deck first.
- Preserved actual button labels during move mode and moved layout instructions
  to the Deck header.
- Limited the editor catalog to 30 visible results and added search/category
  guidance instead of rendering an unbounded list.
- Replaced noisy inline completion text with the existing concise feedback
  behavior.

## Navigation and keyboard

- Fixed Back from AI Creator and Settings so it returns to the previous Codecks
  surface instead of exiting the app.
- Kept primary destinations stable while secondary screens use normal back-stack
  navigation.
- Made keyboard delivery controls horizontally scrollable and touch-safe in
  narrow layouts.
- Kept Auto, Bluetooth, Pasteboard, Enter, and Command-Enter reachable in
  portrait and landscape.
- Switched compact landscape layouts to the side navigation rail at 600 dp,
  preventing the bottom navigation bar from covering controls.
- Corrected setup and clipboard guidance text.
- Added a dedicated monochrome foreground-service notification icon.

## Mac effects and Reactive helper

- Unified built-in and AI-generated Mac visual effects through one catalog.
- Standardized full-screen, multi-display, click-through Mac effects with a
  3.6-second duration.
- Isolated Reactive provider failures so one provider cannot suppress every
  other provider.
- Reported duplicate Reactive action conflicts instead of silently accepting
  ambiguous actions.
- Added a bounded Apple Shortcuts catalog reader/importer.
- Hardened Shortcuts execution with an absolute executable path, argument-only
  invocation, closed stdin, concurrent output draining, output limits, strict
  timeouts, termination escalation, and explicit rejection of unknown input.
- Added typed capability advertising for registered Mac-helper action handlers.

## Regression coverage

- Added Deck regression coverage for Back navigation, offline management,
  immediate removal, and persistence after relaunch.
- Added portrait and landscape keyboard-control coverage.
- Added Android unit coverage for Deck persistence, undo, feedback, Mac effects,
  notification policy, and Reactive provider isolation.
- Added Mac-helper tests for Shortcuts discovery, validation, bounded output,
  timeouts, failures, and receipts.

## Validation

- Android release unit suite passed.
- Android release lint passed.
- Android debug APK assembly passed.
- Gradle-managed emulator suite passed: 15 tests, zero failures; the live SSH
  test remained intentionally skipped without a real Mac endpoint.
- Maestro Deck and orientation flows passed on an API 34 emulator.
- Mac helper Swift suite passed: 30 tests, zero failures.
- Production minification and resource shrinking remain disabled.
- The final signed release APK is rebuilt and tested by GitHub Actions from the
  exact release tag before publication.

## Assets

- `codecks-release.apk`: signed, unshrunk Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.30...v0.1.31
