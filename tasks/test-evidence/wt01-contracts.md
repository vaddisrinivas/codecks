# WT01 Reactive Contracts Evidence

Status: focused green

Branch: `codex/reactive-contracts`

Scope changed:

- `shared/src/commonMain/kotlin/io/codecks/shared/protocol/ReactiveProtocol.kt`
- `shared/src/commonTest/kotlin/io/codecks/shared/protocol/ReactiveProtocolTest.kt`
- `protocol/schemas/reactive-contract-v1.schema.json`
- `protocol/fixtures/reactive-challenge.json`
- `protocol/fixtures/reactive-capabilities.json`
- `protocol/fixtures/reactive-state-snapshot.json`
- `protocol/fixtures/reactive-state-delta.json`
- `protocol/fixtures/reactive-provider-candidate.json`
- `protocol/fixtures/reactive-execute.json`
- `protocol/fixtures/reactive-transfer-metadata.json`
- `protocol/fixtures/hostile/*.json`
- `tools/verify_protocol_fixtures.py`

Implemented contract coverage:

- Protocol version negotiation and compatibility window.
- Pairing helper identity pin, persisted pairing record, revocation, and re-pairing reason.
- Replay window, strict sequence guard, request deadlines, and clock-skew validation.
- Typed errors and retryability.
- Capability advertisement.
- State snapshot, state delta, revision advance, freshness, provenance, and stale-state rules.
- Provider candidate confidence, policy decision, and conflict model.
- Typed execute preconditions, operation ID, idempotency key, receipt, partial failure, undo token, and undo result.
- Transfer metadata bounds, path traversal rejection, SHA-256 shape, and cleanup flag.
- Redaction rules for log-safe fields.
- Valid and hostile JSON fixtures plus local verifier.

Validation:

- `python3 tools/verify_protocol_fixtures.py` -> `protocol fixtures ok`
- `./gradlew :shared:jvmTest` -> `BUILD SUCCESSFUL`
- `./gradlew :shared:iosSimulatorArm64Test` -> `BUILD SUCCESSFUL`
- `git diff --check` -> clean

Gaps / risks:

- `redactedFieldValue(Hash)` is deterministic placeholder hashing, not cryptographic SHA-256. Crypto adapters must provide real hashing before logging secrets.
- JSON verifier is stdlib shape validation, not full JSON Schema draft 2020-12 execution.
- Swift parity is proven by KMP iOS simulator compile/test only; no native Swift consumer fixture test exists yet.
