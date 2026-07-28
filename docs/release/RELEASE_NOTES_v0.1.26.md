# Codecks v0.1.26 release notes

Date: July 28, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.26

## Summary

Codecks v0.1.26 makes AI setup useful instead of picky. The AI Creator screen now
has three visible choices — OpenAI-compatible, Anthropic, and OpenRouter — with a
free-text model/deployment field and a custom endpoint field for compatible
gateways.

## Changes since v0.1.25

- Simplified the AI provider picker to OpenAI-compatible, Anthropic, and
  OpenRouter.
- Replaced fixed model chips with a model/deployment text field.
- Switched OpenAI-compatible generation from the OpenAI Responses API to
  OpenAI-compatible chat completions.
- Added endpoint support for:
  - `https://api.openai.com`
  - `/v1` compatible gateways
  - Azure `/openai/v1`
  - Azure Foundry `/models?api-version=...`
  - local LiteLLM or router endpoints such as `http://127.0.0.1:4000`
- Sent both Bearer and `api-key` auth headers for compatible providers.
- Removed forced temperature from compatible requests for deployments that only
  accept provider defaults.
- Normalized nullable JSON-schema output for stricter gateways.
- Updated OpenRouter’s suggested model to a free model that passed a live
  Codecks draft smoke.
- Updated AI Creator docs and live-provider smoke defaults.
- Kept production minification and resource shrinking disabled.

## Validation

- Focused release unit tests passed.
- Debug APK build passed.
- Azure/OpenAI-compatible live Codecks eval passed using Azure Foundry
  `gpt-chat-latest`.
- OpenRouter live Codecks eval passed using
  `google/gemma-4-26b-a4b-it:free`.
- Local cc-litellm live Codecks eval passed through `http://127.0.0.1:4000`
  using model `gpt-5.5`.
- Local cc-litellm also returned valid schema JSON through the new
  `openrouter-gpt-oss-free` route.

## Assets

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.25...v0.1.26
