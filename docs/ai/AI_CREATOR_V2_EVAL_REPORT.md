# AI Creator V2 Eval Report

Offline report schema: 1
Corpus SHA-256: `f33316f29094edd00619e5771dfc9607fcd035ab942700c36117003df2130477`
Generated-output bypass SHA-256: `833f4e13b91e5174e0c43ae7f756a72e3b7638b533fa599fb191124789659d2d`

## Corpus

- Total prompts: 120
- Action prompts: 40
- Deck prompts: 40
- Automation prompts: 40
- Generated-output bypass cases: 19

## Verified Static Facts

- Corpus has required 40/40/40 prompt split.
- Corpus files have the recorded hashes and required case counts.
- Unit gates listed below are requirements, not proven executions, unless `unitGateReceipt` is non-null in the JSON report.
- SHA-bound unit-gate receipt supplied: no.
- Live-provider scoring is available through the opt-in AiCreatorV2LiveEvalTest and writes docs/ai/AI_CREATOR_V2_LIVE_EVAL_REPORT.md.

## Pending Live Gates

- Run corpus against OpenAI, Anthropic, Gemini, and supported gateway models.
- Measure first-pass semantic validity.
- Measure validity after one bounded repair.
- Confirm zero generated actions bypass review or deterministic policy checks.
- Save provider metadata only; never store API keys or raw auth headers.
