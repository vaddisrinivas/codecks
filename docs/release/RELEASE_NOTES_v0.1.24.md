# Codecks v0.1.24 release notes

Date: July 28, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.24

## Summary

Codecks v0.1.24 takes the Reactive Platform from typed foundation into a real
helper-backed integration checkpoint. It adds a native Swift Mac helper runtime,
authenticated helper transport, Android helper discovery/session wiring, typed
helper execution, and experimental Mac capability handlers while preserving the
protected signed Android release path.

## Reactive helper platform

- Added a native Swift Mac helper package and CLI.
- Added authenticated framed TCP transport with pinned helper identity,
  shared-secret proof, bounded frames, ordered request handling, and replay
  rejection.
- Added helper runtime configuration through environment or local helper config.
- Added Bonjour advertising for `_codecks-reactive._tcp`.
- Added launchd install/uninstall scaffold for the Mac helper.
- Added Android mDNS helper discovery.
- Added Android helper credential storage, pinned identity checks, and helper
  session management.
- Wired helper-backed clients into Reactive action execution.
- Wired helper state alongside the existing SSH Mac-state source.
- Added manual helper pairing import in Settings.
- Added helper pairing deep link:
  `codecks://helper-pair?payload=<urlencoded-json>`.
- Added Mac helper pairing JSON export through the helper CLI.

## Mac capability handlers

- Added Apple Shortcuts CLI handler with argv-only execution, strict name/input
  validation, bounded timeout, and typed receipt handling.
- Added Spotlight search execution through `mdfind`, with bounded result count
  and shell-shaped query rejection.
- Added SFTP transfer execution over the existing Android JSch path, with
  traversal and metadata validation.
- Added monitor brightness execution through BetterDisplay CLI when installed.
- Added bounded, permission-aware Accessibility discovery scanning.

## Safety and validation

- Production release minification and resource shrinking remain disabled.
- `app.codecks.debug` remains separate from protected production `app.codecks`.
- Android release unit tests passed on the merged main release state.
- Mac helper Swift tests passed on the merged main release state.
- Protocol fixtures passed.
- Release no-shrink verifier passed.

## Still experimental

- Helper pairing is JSON/deep-link/manual import, not a polished QR flow.
- The helper and capability handlers are code/test validated, but not yet
  proven as a full live phone-to-Mac field setup.
- Brightness requires BetterDisplay CLI and was validated at the handler/argv
  layer, not against every monitor.
- Shortcuts, Spotlight, SFTP, brightness, and Accessibility still need product
  UX polish before they should be sold as beginner-safe features.
- This is not an iOS app release.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.23...v0.1.24
