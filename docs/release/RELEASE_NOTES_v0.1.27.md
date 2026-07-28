# Codecks v0.1.27 release notes

Date: July 28, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.27

## Summary

Codecks v0.1.27 fixes AI generation after configuring Azure/OpenAI-compatible
providers. The app now remembers the custom model or deployment name, keeps AI
settings editable after setup, and lets saved-key users change model/endpoint
settings without pasting the key again.

## Changes since v0.1.26

- Persisted the selected AI model/deployment name per provider.
- Added a saved-key settings save path for model and endpoint updates.
- Kept the AI settings button visible after the provider is ready.
- Cleared blank OpenAI-compatible endpoint overrides instead of silently keeping
  stale saved URLs.
- Preserved encrypted API keys and existing app data during update.
- Kept production minification and resource shrinking disabled.

## Validation

- Focused release unit tests passed for AI provider settings.
- Azure/OpenAI-compatible live Codecks eval passed with Azure Foundry
  `gpt-chat-latest`.
- Release surface validation passed through the focused unit-test build.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.26...v0.1.27
