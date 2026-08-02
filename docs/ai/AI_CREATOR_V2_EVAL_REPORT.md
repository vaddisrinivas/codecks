# AI Creator V2 Eval Report

Offline report schema: 1
Corpus SHA-256: `f33316f29094edd00619e5771dfc9607fcd035ab942700c36117003df2130477`
Generated-output bypass SHA-256: `1d0d6e5b943bae5b89ca17a7c6214fed7c92efa8343de7506187874a19f5df91`

## Corpus

- Total prompts: 120
- Action prompts: 40
- Deck prompts: 40
- Automation prompts: 40
- Generated-output bypass cases: 12

## Proven Local Gates

- Corpus has required 40/40/40 prompt split.
- Unit tests verify strict V2 schema shape.
- Unit tests verify parser success, refusal/needs-input handling, bounded repair, oversized deck rejection, missing-template rejection, dangerous-confirmation metadata, and adversarial command/URL rejection.
- Unit tests verify generated artifacts cannot be saved before dry run evidence.
- Unit tests require one deterministic assertion per normalized executable automation action.
- Unit tests reject the checked-in generated-output bypass corpus.
- Secret surface scan is required separately by release verification.
- Live-provider scoring is available through the opt-in AiCreatorV2LiveEvalTest and writes docs/ai/AI_CREATOR_V2_LIVE_EVAL_REPORT.md.

## Pending Live Gates

- Run corpus against OpenAI, Anthropic, Gemini, and supported gateway models.
- Measure first-pass semantic validity.
- Measure validity after one bounded repair.
- Confirm zero generated actions bypass review or deterministic policy checks.
- Save provider metadata only; never store API keys or raw auth headers.
