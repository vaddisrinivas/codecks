# Implementation Plan: Context Deck

Updated: August 23, 2026

## Implementation status

All ten product slices are implemented. Full OSS JVM, release lint, no-shrink,
and two focused managed-emulator UI tests pass. Camera/recording truth remains
unavailable without a trusted source; the public Mac adapter exposes the current
Space only. Tablet/RTL/200% text, live Mac transfer/state, physical-device,
install, provider, push, and release lanes remain `NOT_RUN`.

## Outcome

Turn the existing Deck into a contextual Mac control surface while preserving
local-first operation, explicit execution, reviewed commands, protected release
data, and the independent HID/SSH/helper transport boundaries.

## Architecture decisions

- One Context Deck model powers all ten features; no duplicate mini-apps.
- Bluetooth HID remains the immediate media/input path. SSH/helper refreshes run
  from lifecycle-owned coroutines, never pointer, drag, draw, or composition callbacks.
- App context and local ranking can offer controls, but never reorder or execute
  the user's Deck automatically.
- Mac/helper-dependent UI is capability-gated and shows unavailable/stale truth.
- File Drop uses the existing pinned-host SFTP path and Android URI grants; no
  raw `scp`, shell transfer, broad filesystem access, or credential export.
- Lock-screen controls are opt-in, HID-only, fixed to four safe media commands,
  and cannot reconnect, type, run SSH, open settings, or inspect state.

## Tasks

### Phase 1: Context foundation

#### Task 1: Closed Context Deck contracts

**Acceptance criteria:**

- [ ] Bounded models cover live state, analog controls, app offers, modifier
  layers, window/space entries, File Drop, workflow recording, target handoff,
  mini Deck admission, and a maximum-three context strip.
- [ ] Pure reducers reject stale state, unsafe lock-screen actions, duplicate
  targets, automatic context execution, unbounded drafts, and invalid ranges.

**Verification:** focused JVM contract tests.

**Dependencies:** none.

#### Task 2: App-follow offer and context strip

**Acceptance criteria:**

- [ ] Active-app matches create a dismissible/apply-only Deck offer.
- [ ] Exactly zero to three locally ranked suggestions render without moving
  persistent Deck slots.
- [ ] Existing Smart privacy and run/test gates remain intact.

**Verification:** Home reducer/UI tests and managed semantics smoke.

**Dependencies:** Task 1.

#### Task 3: Modifier layer

**Acceptance criteria:**

- [ ] Holding an admitted modifier reveals a bounded secondary layer; release
  or cancellation restores the base layer.
- [ ] Modifier activation never executes its own action and survives no process
  boundary unless the user explicitly saves the layer.

**Verification:** reducer, gesture cancellation, and Compose semantics tests.

**Dependencies:** Task 1.

### Phase 2: Live and analog controls

#### Task 4: Live-state rail

**Acceptance criteria:**

- [ ] Mute, camera, music, recording, and VPN states use typed fresh/stale/
  unavailable values with source and observation time.
- [ ] Refresh is lifecycle-owned, bounded, cancellable, and never runs in an
  HID or Compose rendering callback.

**Verification:** parser/repository freshness tests plus UI state semantics.

**Dependencies:** Task 1.

#### Task 5: Analog buttons

**Acceptance criteria:**

- [ ] Volume, brightness, and timeline controls expose 0..100 values, keyboard/
  TalkBack adjustments, drag preview, and a single bounded commit on release.
- [ ] Unsupported controls remain visible but disabled with a reason.

**Verification:** reducer/coalescing tests and managed 48dp/semantics checks.

**Dependencies:** Tasks 1 and 4.

### Phase 3: Mac canvas and execution tools

#### Task 6: Window and Space map

**Acceptance criteria:**

- [ ] A bounded snapshot shows known displays/spaces/windows with one focused
  item and stale/unavailable truth.
- [ ] Focus requires an opaque validated ID and explicit tap; titles never enter
  a shell command.

**Verification:** protocol/parser/policy tests and adaptive UI test.

**Dependencies:** Tasks 1 and 4.

#### Task 7: File Drop shelf

**Acceptance criteria:**

- [ ] User-selected document URIs become a bounded queue with name/size only;
  each item requires a chosen ready Mac and reviewed destination root.
- [ ] Transfer uses the typed SFTP request, reports per-file receipts, and does
  not persist URI contents or SSH secrets.

**Verification:** URI/size/root/target/failure tests; live Mac transfer NOT_RUN
until separately authorized and configured.

**Dependencies:** Task 1.

#### Task 8: Workflow recorder

**Acceptance criteria:**

- [ ] Explicit recording captures successful Codecks action identities only,
  caps steps, and produces an editable disabled draft.
- [ ] It never records secrets, raw output, failed actions, passive Mac activity,
  or auto-enables the resulting workflow.

**Verification:** session/recreation/bounds/redaction tests.

**Dependencies:** Task 1.

#### Task 9: Multi-Mac handoff

**Acceptance criteria:**

- [ ] An action can be routed to current, one chosen ready Mac, or an explicitly
  confirmed compatible set using existing target selectors.
- [ ] Partial failures remain per-target and no unavailable/incompatible target
  is silently treated as success.

**Verification:** target resolution, confirmation, partial-result, and UI tests.

**Dependencies:** Task 1.

### Phase 4: Safe glance surface

#### Task 10: Lock-screen Mini Deck

**Acceptance criteria:**

- [ ] Opt-in surface exposes exactly four admitted HID media controls while an
  existing HID session and current lock policy are valid.
- [ ] Keyboard, arbitrary Deck, SSH/helper, clipboard, settings, pairing,
  reconnect, and dangerous actions remain denied at dispatch time.

**Verification:** policy/ViewModel/instrumented lock-screen tests.

**Dependencies:** Task 1.

## Checkpoints

- Foundation: focused pure JVM suite and source diff pass.
- Context UI: phone/tablet, RTL, 200% text, semantics, and 48dp targets.
- Transport features: typed failures, cancellation, privacy, and no HID latency regression.
- Complete: release compile, no-shrink gate, focused managed suite, and clean diff.

## Stop conditions

- Stop before any physical-device instrumentation, protected-package mutation,
  Mac-helper installation, provider call, public release, or push.
- Stop on signer/no-shrink drift, raw-command/SFTP policy weakening, unsafe
  lock-screen reachability, or an unbounded persistence/transfer surface.
- Report live Mac/helper/device lanes as `NOT_RUN` unless separately executed.
