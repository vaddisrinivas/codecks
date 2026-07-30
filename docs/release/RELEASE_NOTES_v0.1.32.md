# Codecks v0.1.32 release notes

Date: July 30, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.32

## Summary

Codecks v0.1.32 closes several daily-use trust gaps in Deck editing, AI-created
artifacts, clipboard transfer, automation testing, and expanded-display use.
The release separates saving from placement, reports what clipboard and
automation operations actually proved, and keeps important navigation reachable
in compact landscape and secondary-display layouts.

## Deck and AI creation

- Removed the redundant Deck-header pencil. Per-button management remains on
  long-press, while Deck-wide customization is available from the overflow.
- Made empty Deck slots a direct entry into creation and placement instead of
  requiring a trip through Settings or More.
- Kept successfully generated artifacts durable even when the current Deck is
  full.
- Separated catalog persistence from Deck placement. Saving generated work no
  longer requires an empty Deck slot.
- Added a bounded placement flow that can explicitly replace selected Deck
  slots.
- Reworded generated Deck actions from the ambiguous `Add Deck` to
  `Place on Deck`.
- Added regression coverage for full-Deck generation, deferred placement,
  replacement, and invalid slot counts.

## Clipboard reliability

- Removed the incorrect Mac-connection dependency from local phone clipboard
  refresh.
- Added explicit manual transfer receipts with direction, destination, timing,
  retry state, failure category, and result.
- Distinguished manual transfer, visible-session live sync, and Android's
  unavailable background clipboard-read behavior.
- Added Android Share handling so text can be sent into Codecks through an
  explicit user action.
- Improved reconnect, stale revision, conflict, duplicate, and failure
  reporting.
- Avoided redundant Codecks copy notifications where Android or Samsung already
  presents the system clipboard overlay.

## Automation proof

- Split local validation, Mac preflight, bounded live test, and enablement into
  distinct states.
- Stopped presenting a local safety validation as proof that a Mac command ran.
- Added typed preflight results for trusted Mac identity, connectivity,
  capability availability, permissions, targets, tools, and paths.
- Added live-test receipts bound to the automation revision, Mac identity,
  capability snapshot, assertions, timestamp, and cleanup result.
- Invalidated stale proof when the recipe, Mac, permissions, capabilities, or
  proof lifetime changes.
- Preserved explicit confirmation for dangerous actions.
- Added scheduling diagnostics for evaluation windows, missed conditions,
  retries, and trigger simulation.
- Expanded tests for offline Macs, identity mismatch, missing capabilities,
  permission denial, timeouts, partial execution, cleanup failure, revision
  invalidation, and worker constraints.

## Expanded displays and lifecycle

- Preserved the current destination through supported orientation, screen-size,
  layout, and keyboard configuration changes.
- Kept navigation reachable at compact secondary-display heights by using the
  adaptive navigation policy instead of assuming a tall desktop window.
- Added explicit policy coverage for 1280×720 and 1920×1080 display classes.
- Added a cross-vertical Maestro flow for Deck-to-AI, Clipboard, Automations,
  Back, and adaptive navigation.
- Documented the boundary between emulator secondary-display evidence and real
  Samsung DeX acceptance.
- Kept the production HID keepalive unchanged pending evidence that a lower
  wakeup strategy preserves reconnection and held-input release safety.

## Validation

- Android release unit suite passed.
- Android lint and debug assembly passed.
- Gradle-managed emulator suite passed.
- Cross-vertical Maestro emulator flow passed.
- Mac-helper Swift suite passed.
- Public-source secret scanning and bundled Mac-action verification passed.
- Production minification and resource shrinking remain disabled.
- The final signed release APK is rebuilt and tested by GitHub Actions from the
  exact release tag before publication.
- The downloaded GitHub APK certificate and checksum are verified before the
  protected `app.codecks` installation is updated in place.

## Assets

- `codecks-release.apk`: signed, unshrunk Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.31...v0.1.32
