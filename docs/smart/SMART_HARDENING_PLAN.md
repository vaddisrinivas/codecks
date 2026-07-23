# Smart hardening implementation plan

Baseline: `v0.1.19`
Baseline commit: `2d73fe4fc1b3504f475d7e5a774010e4d3fd189d`

## Phases

### Phase 0 — Baseline and tracking
- Create progress and decision tracking artifacts.
- Establish baseline green checks before code changes.
- Do not modify app behavior.

### Phase 1 — Typed Smart models
- Replace free-form smart strings with typed enums/value classes.
- Update codecs and persistence to fail safe on unknown enum values.
- Keep action type and environment capability separate:
  - `SmartActionKind` describes what a candidate does.
  - `SmartCapability` describes what the current device/Mac environment can support.

### Phase 2 — Typed local-action results and MacInput
- Introduce typed local action outcomes.
- Add `SmartCapability.MacInput` for Bluetooth HID/Mac input readiness.
- Use `SmartActionKind.MacInput` for HID/media candidates that need Mac input.
- Gate HID/media actions on `SmartCapability.MacInput`.
- Record local success/failure accurately.

### Phase 3 — Correct learning and suppression semantics
- Separate temporary and persisted suppression.
- Constrain transition learning by surface/app/Mac and time window.
- Use canonical context-aware keys.

### Phase 4 — Opaque Mac identity and privacy cleanup
- Remove username/host/port/IP/SSH host key from Smart persistence.
- Remove notification source key persistence.

### Phase 5 — SmartDeckViewModel extraction
- Move Smart orchestration out of `MainActivity` into dedicated VM.
- Emit effects for UI-facing actions only.

### Phase 6 — Candidate provider architecture
- Add provider-based candidate generation for extensibility and determinism.
- Introduce app-action mapping asset.

### Phase 7 — Signed release emulator gate
- Build the signed/minified release APK and release instrumentation APK before testing.
- Run release instrumentation, including app startup, on the configured Gradle managed emulator.
- Verify the candidate checksums before and after the managed-emulator run.
- Publish only the exact tested artifacts.
- Do not test on a physical phone or claim live SSH/Mac proof.

### Phase 8 — Final integration and release audit
- Run full checks.
- Validate Smart behavior and privacy constraints.

## Evidence and completion criteria
- Each phase updates `SMART_IMPLEMENTATION_PROGRESS.md` with command evidence and manual checks.
- No phase is marked complete without tests and required checks.
- No unrelated source changes are included.

## Final behavior contract

- Smart Suggestions remain default-off.
- Smart Suggestions never grant execution permission.
- Local Smart actions report real success or failure.
- Bluetooth HID/media actions require `SmartCapability.MacInput`.
- Transition learning only learns relevant, recent sequences.
- Temporary hide is not persisted.
- Context-specific suppression and global suppression are separate.
- Smart identity uses the existing opaque target ID.
- Smart does not persist notification source labels.
- `MainActivity` does not own Smart ranking or feedback details.
- Smart fields use enums/value classes instead of arbitrary strings.
- Candidate generation is provider-based and deterministic.
- Releases are blocked until the exact signed/minified APK passes release instrumentation on the configured managed emulator.
- This gate proves Android startup/instrumentation only; it does not prove live SSH, real-Mac actions, or physical-phone behavior.

## Non-goals

- Smart Trackpad pointer snapping
- OCR
- Screenshot capture
- Automatic clicking
- LLM-based suggestion ranking
- Smart Keyboard text generation
- Automatic Rule creation
- Automatic setting changes
- New cloud services
- Remote analytics
- Automatic enablement of Smart flags
- Automatic Rule enablement
- Automatic command approval
