# Post-v0.1.21 Plan Conformance Audit

Date: July 27, 2026

Historical decision at audit time: **NOT READY FOR RELEASE**

Superseded later on July 27, 2026: the user explicitly accepted a bounded
Android `v0.1.23` release, limited completion scope to the KMP shared module,
typed/authenticated/replay-protected protocol scaffold, Android helper-client
scaffold, and iOS shared build targets, and deferred the remaining full
Reactive Platform plan.

No version bump, release commit, tag, push, GitHub release, production-package
install, or production-package data mutation was performed by this audit.

## 1. Scope and authority

Public release baseline:

- GitHub latest release: `v0.1.21`
- tag commit: `c453da7`
- Android version: `0.1.21`
- Android version code: `22`

Current implementation checkout:

- branch: `codex/ux-polish-desk-launch`
- HEAD: `c581890` (`Polish deck and keyboard interactions`)
- committed distance from `v0.1.21`: one commit
- current public package on the approved phone: `app.codecks` `0.1.21`
- current side-by-side debug package: `app.codecks.debug` `0.1.21-debug`

Plans audited:

1. `docs/reactive/REACTIVE_PLATFORM_IMPLEMENTATION_PLAN.md`
2. `docs/product/DESK_VALUE_LOCKSCREEN_AND_REACTIVE_EXPANSION_PLAN.md`
3. the release invariants in `AGENTS.md`

The newer desk/lockscreen addendum controls where it explicitly supersedes the
older Reactive blueprint.

## 2. Change inventory since v0.1.21

Tracked Git delta against `v0.1.21`:

- 20 tracked files changed
- 468 insertions
- 121 deletions

Untracked worktree delta:

- 52 files
- 10,183 lines
- 2,977 lines of new main Kotlin source
- 1,849 lines of new tests
- 5,322 lines of new docs and task plans
- remaining lines are widget resources

Total unique files changed or added since `v0.1.21`: 72.

The worktree is not a releasable source snapshot because most new production
code and tests are still untracked.

## 3. Work completed since v0.1.21

### 3.1 Committed UX and entry patch

Commit `c581890` adds:

- removal of permanent duplicate Deck result text such as
  `Completed: completed`;
- Keyboard `Send + Enter`;
- explicit `Enter`;
- explicit `Command + Enter`;
- clear-after-success state handling;
- failure-preserves-draft behavior;
- `codecks://trackpad`;
- signed internal destination routing;
- Tasker launch documentation;
- focused policy/state tests.

Physical debug evidence already obtained:

- release and debug packages remained installed side by side;
- the debug HID service ran under `app.codecks.debug`;
- a phone Trackpad swipe moved the real Mac cursor;
- the Keyboard surface showed `Send + Enter`, `Enter`, and `Command + Enter`;
- a successful phone-side send reported `Enter sent`;
- the phone-side text field cleared after success.

Not proven:

- reliable left-click from a real finger;
- controlled Mac-side landing of keyboard text and Enter;
- keyboard multi-touch or near-simultaneous button behavior.

### 3.2 README value evidence

Implemented:

- README value/tradeoff section;
- dated evidence note;
- CSV snapshot;
- explicit distinction between hardware cost, desk-space allocation, and cash;
- square-inch calculations;
- setup, accuracy, battery, availability, privacy, and ergonomic caveats.

Gap found during live source refresh:

- the current Logitech page still shows 18 visible products, but its visible
  products/prices do not match the CSV rows;
- the current visible-price mean is therefore not reproducibly the README's
  `$65.55`;
- README footnotes name sources but do not link directly to them;
- Razer SKU pages timed out during this audit, so their price claims were not
  independently refreshed;
- IKEA and Pew claims remained visible and consistent with the cited figures.

Result: A1 is partial, not complete.

### 3.3 Lockscreen Trackpad foundation

Implemented:

- pure capability and entry-origin policy;
- user opt-in setting;
- exported exact-URI entry router;
- non-exported restricted lockscreen activity;
- `FLAG_SECURE`;
- excluded-from-Recents activity;
- no automatic screen wake;
- pointer-only ViewModel port;
- movement, scroll, left/right/middle buttons;
- command gesture callbacks structurally removed;
- release-buttons calls on stop and policy loss;
- widget entry;
- generic notification entry;
- notification Stop action removal;
- immutable explicit pending intents;
- threat-model draft;
- policy and source-structure tests.

Not proven:

- full truth-table coverage;
- Activity/Compose behavior in instrumentation;
- pre-first-unlock runtime behavior;
- lock/unlock race behavior;
- disconnect while a button is held;
- Bluetooth-off behavior;
- Back/Home/Recents behavior on the Samsung phone;
- exact URI behavior while the real phone is locked;
- 30-minute idle and Mac sleep/wake;
- exact signed-artifact lockscreen behavior.

Important implementation risk:

- the router reads Trackpad settings before it checks `UserManager.isUserUnlocked`;
  pre-first-unlock fail-closed behavior is therefore not yet demonstrated;
- keyguard state is polled every 750 ms, so dispatch uses a cached policy
  snapshot during lock-state transitions;
- several "activity policy" tests inspect source text rather than execute the
  Android lifecycle.

Result: L0-L2 are partial; L3 is not started.

### 3.4 Trackpad and accessibility papercuts

Implemented:

- expanded controls consume dead-space touches;
- Back closes the open controls surface before broader navigation;
- menu icons expose button role, label, and selected state;
- 48 dp control targets in the touched Trackpad chrome;
- papercut evidence ledger.

Not completed:

- left-click reliability;
- drag/right-click/scroll physical matrix;
- tray overlap on the phone;
- keyboard multi-touch characterization;
- full TalkBack focus order and wording;
- rotation and window-mode checks.

Result: A2 is partial.

### 3.5 Reactive Android slice

Implemented:

- typed Android-local Mac-state models;
- `MacStateRepository` interface;
- local-cache-backed `LiveMacStateRepository`;
- deterministic provider/engine core;
- one active-app mapping provider;
- typed action/result/receipt models;
- in-memory receipt stores;
- JSON action protocol codec;
- Android action executor adapter;
- default-off `ReactiveTrackpad` feature flag;
- Trackpad card;
- four visible controls plus More;
- confirmation dialog;
- loading/disconnected/empty copy;
- lockscreen exclusion;
- unit tests for models, engine, provider, repository, executor, codec,
  presentation, and ViewModel.

This is not the planned platform architecture:

- models live in `:app`, not KMP `:shared`;
- there is no schema/fixture directory shared with Swift;
- there is no authenticated protocol state machine;
- there is no native Mac helper;
- there is no Android helper client;
- there is no helper/SSH merged Mac-state repository;
- only front-app/local-cache state is populated;
- display, clipboard, media, selection, window, cursor, meeting, and screenshot
  refreshes return `not_implemented`;
- helper and bundled SSH executor actions return `Unsupported`;
- there is no undo execution;
- there is no replay/idempotency implementation;
- there is no Browser/Terminal/Finder/Media provider suite matching R7;
- there is no physical Reactive matrix.

Safety gaps:

- control revision is derived from the static UI spec, not the resolved current
  `DeckAction` revision;
- a confirmation or review can therefore be bound to the control spec while
  the repository action changes between prompt and execution;
- executor does not recheck selected Mac, snapshot revision, required
  capabilities, or provider registry contract at execution time;
- `SharedHidCommand.Reload` maps to `HidCommand.CommandEnter`, which is not
  reload semantics;
- success receipt metadata includes human title/message content although the
  planned physical log contract is code-only.

Result: R2, R6, R7, R8, and R9 have partial Android-local prototypes. They do
not satisfy their phase exit gates.

## 4. Phase conformance matrix

| Plan phase | Status | Evidence / missing exit gate |
|---|---|---|
| Hardening checkpoint | Partial | Opaque target-ID migration exists; current worktree is not clean or committed |
| R0 docs/ADRs/baseline | Partial | Plans copied; no recorded baseline commit, ADR set, platform threat/privacy inventory, or progress ledger |
| R1 KMP scaffold | Not started | `settings.gradle.kts` includes only `:app`; no `:shared` |
| R2 shared models/codecs | Partial prototype | Android-local models/codec only; no commonMain, schemas, fixtures, or Swift parity |
| R3 protocol/security | Not started | No framing, pairing transcript, P-256 auth, replay cache, sequence/deadline tests |
| R4 Mac helper | Not started | No Swift helper, Bonjour helper service, pairing UI, revoke, helper state APIs, signing |
| R5 Android helper client | Not started | No helper discovery/pair/auth/reconnect client |
| R6 unified Mac state | Partial prototype | Local cache facade only; no helper backend, SSH backend, merger, freshness lifecycle, bounded probes |
| R7 providers/engine | Partial prototype | One provider; missing full MVP provider and reviewed registry suite |
| R8 executor/undo | Partial prototype | Android adapters and receipts only; no helper/SSH, undo, or replay; revision/state/capability/target revalidation is now covered by focused tests |
| R9 Reactive UI | Partial prototype | Default-off card and four/More exist; no undo, modes, instrumentation, or performance trace |
| R10 physical MVP | Not started | No Browser/Terminal/Finder/Media matrix |
| R11 rich modes/APIs | Not started | No precision/travel/window/cursor/screenshot phases |
| R12 clipboard/selection | Not started | No Reactive manual clipboard/selection flow |
| R13 profiles/gestures | Not started | No versioned Reactive profiles or resolver |
| R14 iOS controller | Not started | No iOS target or app |
| R15 Smart integration | Not started | No policy-filtered Reactive control reference adapter |
| R16 cross-platform release | Not started | No helper/iOS artifacts, compatibility, notarization, TestFlight, or exact hashes |
| A0 baseline reconciliation | Partial | Current truth inspected and release/no-shrink wording reconciled; no clean baseline or ADR/progress ledger |
| A1 README evidence | Partial | Named snapshots, CSV provenance, arithmetic verifier, and direct README links now exist; claims remain illustrative and time-sensitive |
| A2 papercuts | Partial | Several code fixes; physical/multi-touch/accessibility closure missing |
| L0 lockscreen policy | Partial | Policy exists; exhaustive truth table and reviewed threat-model exit missing |
| L1 restricted Activity | Partial | Code exists; runtime instrumentation and physical proof missing |
| L2 widget/notification/Tasker | Partial | Code/docs and ledger entries exist; runtime checks and exact Tasker/phone verification incomplete |
| L3 protected release | Not started | No exact signed artifact, cert comparison, hash, SSH/HID/lockscreen matrix |
| D0 Tasker/NFC | Partial | Documentation exists; exact Tasker/phone verification missing |
| D1 DeskDock engine | Not started | No score, hysteresis, cooldown, or manual suppress |
| I1 Shortcuts | Not started | No typed Shortcuts adapter/provider |
| I2 app shortcut importer | Not started | No importer, provenance, or review flow |
| I3 Spotlight/SFTP | Not started | Existing Spotlight button is not the planned bounded search/transfer platform |
| I4 brightness | Not started | No supported adapter or capability probe |
| I5 Accessibility discovery | Not started | No helper permission/traversal/import implementation |
| Final integration/release | Not started | No cross-platform exact-artifact release evidence |

## 5. Verification results

Passed on Java 17 with a disposable local CI signing key:

- `git diff --check`
- `scripts/verify_release_no_shrink.sh`
- `:app:testReleaseUnitTest`: 416 tests, 0 failures, 0 errors, 2 skipped
- `:app:lintDebug`
- `:app:check`
- `:app:assembleDebug`
- `tools/ai_creator_v2_eval.py`: 120/120 corpus cases
- `scripts/verify_mac_actions.py`: 46 bundled Mac actions

The first aggregate attempt on Java 20 crashed during lint. Rerunning on the
CI-matching Java 17 runtime passed.

Managed Pixel 6 API 35 debug instrumentation:

- Gradle task passed;
- XML report: 12 tests, 0 failures, 0 errors, 1 skipped;
- startup and seven existing Trackpad gesture tests ran;
- the signed-release SSH smoke was skipped as expected in the debug lane;
- no new lockscreen Activity/Compose instrumentation tests exist, so this pass
  does not prove the lockscreen boundary.

Static privacy gate:

- `tools/secret_surface_check.py`: passed after replacing private home paths in
  both public plan documents with repository-relative/documented placeholders.

Physical phone, current read-only state:

- Samsung `SM-S918U1` is connected by ADB;
- protected `app.codecks` remains installed at `0.1.21`;
- `app.codecks.debug` remains installed at `0.1.21-debug`;
- debug HID service is active;
- production data was not cleared or uninstalled.

## 6. Release blockers recorded at audit time

### P0: blocks any release claim

1. Most new production code and tests are untracked.
2. Full plan is far from complete.
3. No exact signed unshrunk candidate exists for this tree.
4. No candidate certificate/hash comparison exists.
5. No exact-artifact SSH, HID, click, lockscreen, or disconnect proof exists.
6. Android version remains `0.1.21` / `22`.
7. No current release notes exist.

### P1: blocks the intended Android lockscreen/UX slice

1. README quantitative snapshot is reproducible from the checked-in CSV and
   verifier, but remains a small, time-sensitive manufacturer sample rather
   than a market average.
2. Lockscreen tests are mainly pure/source-policy checks, not runtime lifecycle
   or Compose tests.
3. Pre-first-unlock behavior is guarded in code; runtime and physical proof are absent.
4. Runtime checks and physical verification for the new exported entry Activity
   and widget receiver are absent.
6. Left-click remains suspect from physical debug testing.
7. Keyboard multi-touch remains uncharacterized.
8. Physical Samsung lockscreen matrix is absent.

## 7. Superseding release decision

The original decision above applied to the audited worktree before versioning,
shared-module completion, protocol hardening, iOS target verification, release
notes, and final gates. The user later authorized the bounded `v0.1.23`
checkpoint and explicitly deferred the rest of the full-plan backlog.
