# Codecks v0.1.30 release notes

Date: July 30, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.30

## Summary

Codecks v0.1.30 fixes the AI Creator → Deck workflow so generated buttons,
decks, and rules behave like reusable things instead of one-off previews. It
also adds deterministic AI templates for Mac visual effects, including love and
confetti buttons, and adds a public `codecks://ai` app-open link.

## Changes since v0.1.29

- Added deterministic AI Creator templates for Codecks Mac visual effects:
  Confetti, Sparkle, Love, Fire, Focus, Coffee, and Magic.
- Fixed generated visual-effect buttons so Deck Test can preview known Mac
  visual effects instead of only syntax-checking them.
- Changed built-in decorative Deck actions so their Test action runs the same
  Mac curtain effect users see when pressing the button.
- Fixed AI-created buttons and decks so saved artifacts appear in the Deck
  catalog and can be assigned later, even before they have been placed on the
  current Deck.
- Fixed saved AI draft history cards so they expose Check, Add Button/Add Deck
  or Save Rule, Refine, and Remove.
- Renamed AI preview testing to Safety Check and clarified that it does not run
  Mac commands or spend more AI credits.
- Fixed local AI command routing so prompts like “create a love emoji confetti
  button” go to AI generation instead of just opening Deck.
- Added the guarded public `codecks://ai` deep link for opening AI Creator.
- Kept production minification and resource shrinking disabled.

## Validation

- Shared JVM tests passed.
- Release unit tests passed.
- Release no-shrink verifier passed.
- Release APK build passed locally with disposable local signing for validation
  only.
- Debug APK installed side-by-side on the physical phone as `app.codecks.debug`.
- Physical-phone debug smoke passed for `codecks://trackpad` and `codecks://ai`
  without uninstalling or clearing the protected release app.
- Final signed release APK is built and published by the GitHub release
  workflow using the stored release signing key.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.29...v0.1.30
