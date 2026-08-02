# Codecks v0.1.33 release notes

Date: August 1, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.33

## Summary

Codecks v0.1.33 removes the separate Deck editor from the normal workflow and
makes button management happen directly on the live Deck. It also makes
AI-created work catalog-first, adds explicit placement choices, restores access
to hidden Trackpad controls through a five-finger hold, and clarifies Clipboard
and adaptive-navigation status.

## In-place Deck customization

- Replaced normal navigation to the separate Deck editor with a temporary
  **Customize on Deck** mode.
- Kept normal button execution as a tap and button management as a long-press.
- Added long-press actions for run, reassign, move, resize, duplicate, test,
  run history, remove from Deck, and delete from the catalog when allowed.
- Added an empty-slot choice between the existing button catalog and
  **Create with AI**.
- Made move and resize operations persist immediately.
- Added Undo snackbars that restore the complete previous Deck layout after a
  move, resize, removal, or catalog deletion.
- Retained the legacy editor route only for compatibility and future bulk
  administration; it is no longer part of normal navigation.

## AI Builder and catalog behavior

- Persisted every valid AI preview as a durable catalog artifact before Deck
  placement.
- Added explicit **Save only** behavior that leaves the Deck unchanged.
- Added **Place in slot N** when AI Builder was opened from a specific empty
  Deck slot.
- Added **Choose Deck slot** when AI Builder was opened without a slot context
  or produced multiple buttons.
- Preserved placement intent across process/controller restoration.
- Cleared stale slot context after saving an AI-generated Rule.
- Kept generated buttons disabled until their current command revision passes
  the existing safety and live-test gates.
- Moved AI Builder to the first position in the phone **More** sheet and named
  it consistently across compact and expanded layouts.

## Trackpad controls

- Added a five-finger hold to open the hidden Trackpad controls and settings
  tray.
- Preserved the four-finger hold as the direct return-to-Deck gesture.
- Kept Trackpad performance diagnostics out of the normal surface.
- Preserved existing pointer, scrolling, drag-lock, lockscreen, HID, and
  release-SSH behavior.

## Clipboard and adaptive layouts

- Replaced ambiguous Clipboard status copy with the shared Codecks vocabulary:
  **Ready**, **Offline**, **Setup needed**, **Checking…**, and **Failed**.
- Added actionable explanations for conflicts, unavailable Macs, missing setup,
  manual-only mode, visible-session sync, stale state, and failed transfers.
- Stacked phone/Mac Clipboard previews on narrow phones and retained the
  side-by-side view on wider and DeX-style displays.
- Kept the five compact destinations: Deck, Trackpad, Keyboard, Clipboard, and
  More.
- Preserved the navigation rail for expanded and secondary-display layouts.

## Validation

- Android release unit suite: 556 tests, 0 failures, 3 skipped.
- Android debug lint, debug assembly, release compilation, and architecture
  boundary checks passed.
- Gradle-managed Pixel 6 API 35 release suite: 4 tests, 0 failures, 1
  intentional live-SSH skip.
- Maestro compact 1280×720, expanded 1920×1080, repeat-identity, and dedicated
  long-press/in-place Deck customization flows passed.
- Production code minification and resource shrinking remain disabled.
- The protected production phone app was not uninstalled, cleared, downgraded,
  instrumented, or replaced during development verification.
- GitHub Actions rebuilds and tests the exact signed tag artifact before
  publication.

## Known evidence boundaries

- Managed-emulator expanded-display coverage is not real Samsung DeX
  acceptance.
- The managed release test intentionally skips live SSH because no real Mac is
  attached to the GitHub runner.
- Physical-phone installation and real-Mac SSH acceptance are separate
  post-publication gates.

## Assets

- `codecks-release.apk`: signed, unshrunk Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.32...v0.1.33
