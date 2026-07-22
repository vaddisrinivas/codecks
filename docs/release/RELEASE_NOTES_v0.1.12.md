# Codecks v0.1.12 release notes

Date: July 22, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.12

## Summary

Codecks v0.1.12 makes the Trackpad gestures and scroll rails dependable, repairs Mac Deck action execution, gives AI Builder freedom to create reviewable custom commands, and simplifies Mac connectivity settings.

## Trackpad

- Added configurable actions for two-finger double tap, three-finger double tap, three-finger hold, four-finger double tap, and four-finger hold.
- Added a precision scroll rail opposite the normal scroll rail, with adjustable speed and acceleration.
- Added visible fast/slow rail markers and temporary feedback for recognized taps, holds, and scrolling.
- Fixed two-finger double tap being treated as a right click.
- Fixed decorative Trackpad layers creating dead touch regions.
- Fixed small scroll movement being rounded away.
- Prevented scroll-rail release from producing an unintended left click.
- Preserved three-, four-, and five-finger Mac window gestures.

## Mac actions and Deck

- Fixed bundled Mac commands being routed through the custom-command path and failing despite a ready SSH connection.
- Fixed confirmed dangerous Deck actions so the explicit Run confirmation actually authorizes execution.
- Moved Play/Pause, Next Track, and Previous Track to Bluetooth HID media controls instead of unreliable key-code scripts.
- Repaired Focus, Stop Focus, coding setup, meeting setup, browser setup, and Notification Center commands.
- Stop Focus now terminates only the Codecks-started `caffeinate` process.
- Added a verifier for bundled action JSON, shell syntax, AppleScript compilation, and required macOS tools.
- Refreshed the untouched default Deck with confetti, sparkle, and screensaver controls; customized Decks remain unchanged.
- Limited inline success/failure state to the current run instead of leaving stale results on old tiles.

## AI Builder

- AI can now produce a reviewable custom command when typed actions and built-in templates are insufficient.
- Removed the misleading "approved actions" framing while retaining hard blocking for destructive and exfiltration patterns.
- Risky commands must be marked dangerous and explain their concrete consequence before validation succeeds.
- Unsupported requests must be reported honestly instead of being represented by no-op commands.
- Accepted AI artifacts remain disabled and unverified until the user tests and enables them.
- Simplified the bundled skill and schema around action, Deck, and Rule artifacts.

## Mac setup and targeting

- Simplified Settings into two clear connection entries: Mac actions for SSH-backed Deck/Clipboard/Rules, and Mac input for Bluetooth Trackpad/Text.
- Removed the duplicate readiness checklist.
- Improved current-device resolution so multi-Mac execution uses the matching saved target rather than inventing a device identifier.
- Kept SSH pairing and the existing Find → Trust → Authorize → Done flow.

## Verification

- Bundled Mac action verification: JSON, shell syntax, AppleScript compilation, and required tools.
- Emulator interaction tests for fast/slow rails; two-finger double tap; three/four-finger double taps and holds; and three/four/five-finger swipes.
- Android unit tests.
- Android lint.
- Architecture and release-surface validation.
- Signed release build and checksum through GitHub Actions.

## Release assets

- `codecks-v0.1.12.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.11...v0.1.12
