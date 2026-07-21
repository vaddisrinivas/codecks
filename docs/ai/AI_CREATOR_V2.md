# AI Creator V2

AI Creator V2 turns natural-language requests into typed proposals, not runnable commands.

Pipeline:

```text
User request
-> strict provider schema
-> V2 envelope
-> semantic validation
-> deterministic compile
-> dry run
-> explicit save
```

## Current Contract

New Action, Deck, and Automation generation must return:

```json
{
  "schemaVersion": 2,
  "status": "ready",
  "message": "Ready",
  "questions": [],
  "assumptions": [],
  "proposal": {}
}
```

Allowed statuses:

- `ready`
- `needs_input`
- `unsupported`
- `refused`

`needs_input`, `unsupported`, and `refused` do not compile into runnable artifacts.

## Safety Rules

- Models cannot emit raw shell commands, SSH commands, scripts, or AppleScript in V2 proposals.
- Models can emit only typed steps: `open_url`, `clipboard_text`, `delay`, or `template`.
- Template steps must use IDs from `ApprovedAiActionCatalog`.
- Codecks compiles typed steps into existing `ActionDefinition` models after parsing.
- Existing semantic validation still checks capabilities, targets, URLs, retry/delay bounds, dangerous confirmation metadata, and unsupported commands.
- Semantic validation also checks required step arguments, duplicate variable/template IDs, duplicate deck action IDs, and deck size limits.
- If the first model response is structurally valid but semantically invalid, Codecks makes exactly one repair request with the exact validation errors.
- Generated artifacts are not stored immediately after generation.
- Dry run must pass before Save appears.
- Dangerous-but-valid artifacts can be saved only after dry run marks them `RequiresConfirmation`.
- Refinement creates a complete replacement proposal. It does not mutate the source artifact.

## Provider Behavior

OpenAI generation uses the Responses API with strict `text.format` JSON schema.

Anthropic generation uses a forced strict tool result named `emit_ai_creator_v2_draft`.

Gemini generation uses native `responseJsonSchema` with `responseMimeType=application/json`.

OpenRouter and LiteLLM remain on OpenAI-compatible chat completions, but generation is allowed only for catalog models marked `supportsStructuredDrafts=true`.

OpenRouter Auto and unverified gateway models currently fail closed for Action, Deck, and Automation drafts until strict structured output contracts are added and tested.

Context app ranking keeps its existing context-app schema path.

## Local History

Codecks keeps an encrypted local generation history with:

- provider ID and label
- model ID and label
- draft kind
- generation status
- validation messages
- linked artifact ID when one exists

History records do not include API keys, authentication headers, SSH credentials, notification text, app usage, or full transmitted context.

## Refinement Flow

Users can choose Refine on a generated artifact, describe the requested change, and receive a new proposal. The UI keeps the previous artifact intact, shows a before/after comparison, and still requires dry run before Save.

The refinement prompt includes only the previous artifact title, kind, description, action titles, and generated command previews. It does not include API keys, credentials, phone context, or notification content.

## Proposal Review

Generated artifacts persist a review object alongside runnable actions. The review shows:

- risk level and whether confirmation is required
- target Mac/device scope
- automation trigger state
- model assumptions
- required capabilities
- runtime parameters
- every compiled step with typed summary
- every resulting button/action command preview

This review survives app restarts and is displayed before dry run and Save.

## Eval Harness

The checked-in corpus lives at `app/src/test/resources/ai/ai_creator_v2_eval_corpus.tsv`.

Run:

```bash
python3 tools/ai_creator_v2_eval.py --write-report
```

This verifies the 120-prompt 40/40/40 split and writes `docs/ai/AI_CREATOR_V2_EVAL_REPORT.md`. Live-provider scoring is intentionally separate; local tooling must not fake provider pass rates.

Live-provider release-gate scoring is opt-in because it uses real provider keys and network calls:

```bash
CODECKS_AI_V2_LIVE_EVAL=true \
CODECKS_AI_V2_LIVE_PROVIDERS=openai,anthropic,gemini \
./gradlew testDebugUnitTest --tests io.codecks.data.ai.AiCreatorV2LiveEvalTest
```

Use `CODECKS_AI_V2_LIVE_LIMIT=3` for smoke checks. Full stable gating should omit the limit and leave `CODECKS_AI_V2_LIVE_ASSERT_GATES=true`.

The live test writes `docs/ai/AI_CREATOR_V2_LIVE_EVAL_REPORT.md` with aggregate rates only. It does not write API keys, auth headers, or raw model outputs.

## Provider Transmission

Before sending, the AI screen shows:

- selected provider
- selected model
- draft type
- user prompt
- high-level payload contents
- explicit note that API keys are authentication headers and are never inserted into prompts

Codecks does not include notification text, app usage, SSH credentials, or API keys in AI Creator prompts.

## Remaining Before Stable V2

- Run the 120-prompt eval corpus against live providers and store aggregate results.
- Add more live-provider adversarial fixtures for provider-specific malformed bodies, gateway quirks, and injection attempts.
