# Codecks v0.1.29 release notes

Date: July 30, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.29

## Summary

Codecks v0.1.29 is a small cleanup release. It removes the noisy Trackpad face
overlays from v0.1.28 and changes bundled celebration actions to a native
Mac-wide visual overlay instead of opening a browser page.

## Changes since v0.1.28

- Removed the visible Trackpad stats dashboard: sample rate, jitter, dispatch,
  last gesture, HID link state, and touch count no longer sit on the touch
  surface.
- Removed the visible Trackpad Controls button and bottom gesture strip from
  the touch surface. Trackpad controls remain available from Settings and
  existing gestures remain wired.
- Simplified the hidden-guard copy to only say Back returns to Deck.
- Changed bundled decorative actions such as Confetti/Sparkle/Emoji/Magic to
  run a native click-through Mac overlay over every screen through SSH.
- Kept production minification and resource shrinking disabled.

## Validation

- Release Kotlin compile passed.
- Release unit tests passed.
- Release no-shrink verifier passed.
- Mac celebration JXA primitive was locally smoke-tested with Cocoa windows.
- Release publication is built by the GitHub release workflow from this tag.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.28...v0.1.29
