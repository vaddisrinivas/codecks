# Smart system decisions

## Decision 1: Ranking remains local and deterministic

Smart suggestions do not call an LLM.

## Decision 2: Confidence is not authorization

A high-confidence suggestion has no additional permission to run, click, approve, test, or enable anything.

## Decision 3: Local action outcomes are typed

Local actions return `Succeeded`, `Navigated`, or `Failed` and are not assumed successful.

## Decision 3a: Action kind is not capability

`SmartActionKind` describes what a candidate does.
`SmartCapability` describes what the current environment can support.

Example: a media-key candidate can be `SmartActionKind.MacInput` and require `SmartCapability.MacInput`.

## Decision 4: Hide has three separate meanings

- Hide for now: memory-only.
- Don’t suggest here: persisted for the current surface/app/action context.
- Never suggest this button: persisted globally.

## Decision 5: Smart Mac identity uses an existing opaque target ID

Smart does not derive identity from username, hostname, port, IP address, or SSH host key.

## Decision 6: Transition learning is context-scoped

Transitions require the same `SmartSurface`, same `SmartAppKey`, same `SmartMacId`, and a maximum five-minute gap.

## Decision 7: Smart orchestration belongs outside `MainActivity`

`SmartDeckViewModel` owns Smart state, ranking, feedback and pending runs.
`MainActivity` only executes emitted effects and reports final results.

## Decision 8: Release gate is emulator-only

On 2026-07-23, the physical-phone/real-Mac Phase 7 gate was replaced by an
exact-artifact Gradle managed-emulator gate. Release publication depends on the
signed/minified app and release instrumentation artifacts passing emulator
startup/instrumentation with unchanged checksums.

This gate does not prove live SSH, real-Mac action execution, or physical-phone
behavior. Those require separate explicit authorization.
