# AI Creator V2 Eval Report

Generated: 2026-07-17

## Corpus

- Total prompts: 120
- Action prompts: 40
- Deck prompts: 40
- Automation prompts: 40

## Proven Local Gates

- Corpus has required 40/40/40 prompt split.
- Unit tests verify strict V2 schema shape.
- Unit tests verify parser success, refusal/needs-input handling, bounded repair, oversized deck rejection, missing-template rejection, dangerous-confirmation metadata, and adversarial command/URL rejection.
- Unit tests verify generated artifacts cannot be saved before dry run evidence.
- Secret surface scan is required separately by release verification.
- Live-provider scoring is available through the opt-in AiCreatorV2LiveEvalTest and writes docs/ai/AI_CREATOR_V2_LIVE_EVAL_REPORT.md.

## Pending Live Gates

- Run corpus against OpenAI, Anthropic, Gemini, and supported gateway models.
- Measure first-pass semantic validity.
- Measure validity after one bounded repair.
- Confirm zero generated actions bypass review or deterministic policy checks.
- Save provider metadata only; never store API keys or raw auth headers.
