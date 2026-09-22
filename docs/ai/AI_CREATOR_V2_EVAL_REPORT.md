# AI Creator V2 Eval Report

Offline report schema: 2
Corpus SHA-256: `50184226d85c5a8930d775c233f2a6821a08b29dac54f6430b34dc65bb39595a`
Generated-output bypass SHA-256: `833f4e13b91e5174e0c43ae7f756a72e3b7638b533fa599fb191124789659d2d`
Coverage manifest SHA-256: `20bf96a4c82c4e6b7757c48c5ae9f28552e9fa60e7ae2dbc560619bd77d31196`

## Corpus

- Total prompts: 120
- Action prompts: 40
- Deck prompts: 40
- Automation prompts: 40
- Generated-output bypass cases: 19
- Required categories covered: 15

## Verified Static Facts

- Corpus has required 40/40/40 prompt split and versioned, exhaustive category assignments.
- Corpus files have the recorded hashes and required case counts.
- Unit gates listed below are requirements, not proven executions, unless `unitGateReceipt` is non-null in the JSON report.
- SHA-bound unit-gate receipt supplied: yes.
- Combined deterministic verdict: `PASS`.
- Current SHA-bound execution proof makes the combined deterministic metrics eligible for `PASS`/`FAIL`.

## Combined Metrics

- actionableFailure: `PASS` (48/48)
- artifactCodecRoundTrip: `PASS` (72/72)
- artifactConversion: `PASS` (72/72)
- bypassActionable: `PASS` (19/19)
- caseOutcome: `PASS` (120/120)
- disabledAutomation: `PASS` (24/24)
- parserConformance: `PASS` (112/112)
- policyBypass: `PASS` (bypasses=0/19)
- refineOnce: `PASS` (8/8)
- regenerate: `PASS` (8/8)
- reviewMetadata: `PASS` (72/72)
- safeSemanticValidity: `PASS` (120/120)

## Live Providers

- OpenAI-compatible: `NOT_RUN` — Local deterministic benchmark; credentials and network are intentionally unused.
- Anthropic: `NOT_RUN` — Local deterministic benchmark; credentials and network are intentionally unused.
- Gemini: `NOT_RUN` — Local deterministic benchmark; credentials and network are intentionally unused.
- gateway/custom: `NOT_RUN` — Local deterministic benchmark; credentials and network are intentionally unused.
