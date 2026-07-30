# Active Implementation Plan: Product trust and daily-use closure

Status: scheduled for parallel implementation on 2026-07-30

## Outcome

Make Deck creation/editing coherent, make AI artifacts durable and reusable,
replace misleading clipboard and automation claims with verified behavior, and
make Codecks reliable in secondary-display/DeX use without risking the installed
`app.codecks` release.

## Shared contracts frozen before parallel work

- Artifact lifecycle: `Draft -> Validated -> Tested -> Catalogued -> Placed/Enabled`.
- Saving to the catalog never requires an empty Deck slot.
- Placing an artifact always uses an explicit slot picker with replace/new-Deck
  choices.
- Automation `Validate`, `Preflight`, `Live test`, and `Enable` are distinct.
- Clipboard phone-to-Mac background reads are never promised; manual/share and
  visible-session sync are the supported contracts.
- No vertical may edit release versioning, signing, shrinking, GitHub release
  workflows, or production-package device state.

## Dependency graph

```text
Contract freeze
  |-- V1 Deck + AI artifact lifecycle
  |-- V2 Clipboard reliability + honest background behavior
  |-- V3 Automation validation + Mac preflight/test receipts
  `-- V4 DeX/adaptive + battery + cross-vertical regression harness
          |
          v
     Integration checkpoint
          |
          v
     Emulator + real-Mac acceptance
          |
          v
     Separate release decision
```

## V1: Deck and AI artifact lifecycle

**Ownership:** Deck/Home, Deck editor, AI workspace, artifact repository/view
model, and focused tests. Avoid clipboard, automation execution, HID service,
release, and CI files.

### V1.1: Replace redundant editing navigation

Acceptance:

- Populated button long-press owns per-button management.
- Empty-slot tap opens the catalog with search and `Create with AI`.
- Deck overflow owns rename, layout, reset, and bulk customization.
- The unexplained Deck-header pencil is removed.

Verification:

- Compose/unit tests cover long-press, empty-slot, overflow, Back, and rotation.
- Maestro covers create, reassign, move, remove, undo, and catalog forget.

### V1.2: Separate catalog persistence from placement

Acceptance:

- Successful generation creates a durable Draft without consuming a Deck slot.
- UI says `Saved to catalog` or `Place on Deck`; never uses ambiguous `Add`.
- Full Decks allow replace, pick another Deck, or create a new Deck.
- Delete/forget and regeneration never lose the last valid artifact revision.

Verification:

- Repository/view-model tests cover empty/full Decks, process recreation,
  replacement, duplicates, deletion, and failed regeneration.

### V1.3: Rebuild the AI workspace around artifact states

Acceptance:

- Phone flow is `Describe -> Review/Test -> Place/Enable`.
- Contextual AI entry exists from Deck, button management, and automation.
- Result preview renders the actual artifact; destructive/secondary actions use
  overflow.
- Refine preserves revision history and shows provider/model/error/cost state.

Verification:

- Screenshot/Compose tests at phone and expanded widths.
- AI corpus/evaluation gate remains green.

## V2: Clipboard reliability and system behavior

**Ownership:** Clipboard domain/data/UI, explicit Android share/quick-action
surfaces if required, and focused tests. Avoid Deck/AI, automation, HID, release,
and CI files.

### V2.1: Fix correctness and observability

Acceptance:

- Local clipboard refresh never requires a Mac connection.
- Manual Send/Get gives a terminal receipt with direction, time, target, retry,
  and exact failure class.
- Loop suppression/conflict resolution remains deterministic.
- No redundant app toast duplicates the Android/Samsung clipboard overlay.

Verification:

- View-model tests cover disconnected, reconnect, stale revision, empty content,
  conflict, duplicate, SSH failure, and process recreation.

### V2.2: Implement honest supported sync modes

Acceptance:

- UI distinguishes Manual, Visible live sync, and unavailable background read.
- Phone-to-Mac user-driven Share/quick action works without opening Clipboard UI.
- Reads happen only on explicit action or while the visible live-sync session is
  active.
- Sensitive clips use supported sensitive-preview metadata.

Verification:

- Emulator tests exercise share target, focus loss/resume, notification denial,
  Android clipboard overlay coexistence, and background restriction messaging.

### V2.3: Measure and cap resource use

Acceptance:

- No polling job survives after its advertised visible-sync lifecycle.
- Sync intervals/backoff are bounded and observable.
- A repeatable battery/CPU measurement protocol is documented.

Verification:

- Coroutine lifecycle tests and an emulator battery/process-state evidence file.

## V3: Automation proof and Mac capability testing

**Ownership:** Automation models/repository/engine/UI/worker, typed Mac
preflight/receipt contracts, Mac-helper capability checks, and focused tests.
Avoid Deck/AI workspace implementation, clipboard, HID, release, and CI files.

### V3.1: Split validation from execution proof

Acceptance:

- `Validate` performs local deterministic schema/policy checks only.
- `Preflight Mac` checks trusted identity, connection, provider/tool/app/target,
  permissions, and required paths without performing the final action.
- `Live test` is explicit, bounded, produces assertions and cleanup/undo.
- Labels never call a no-execution dry run a successful Mac test.

Verification:

- Tests cover offline Mac, identity mismatch, missing binary/app/Shortcut,
  permission denial, timeout, partial execution, cleanup failure, and success.

### V3.2: Bind enablement to a trustworthy receipt

Acceptance:

- Receipt binds recipe revision, Mac identity, capability snapshot, assertions,
  timestamp, and cleanup result.
- Editing, Mac change, capability/permission change, or expiry invalidates it.
- Dangerous actions remain confirmation-gated.

Verification:

- Unit tests prove every invalidation path and replay rejection.

### V3.3: Make scheduling explainable

Acceptance:

- UI shows next evaluation window, last evaluation, last result, retry/backoff,
  and why a trigger was missed.
- Trigger simulator explains whether and why a recipe would fire now.
- Periodic WorkManager timing is described as a window, never exact time.

Verification:

- Worker tests cover battery/network constraints, process death, duplicate
  suppression, revision changes, retries, and confirmation queues.

## V4: DeX/adaptive, battery, and regression harness

**Ownership:** adaptive app shell/navigation, secondary-display tests, HID
keepalive profiling/fix if evidence supports it, Maestro/instrumentation, and
evidence docs. Avoid feature-domain rewrites, release, and CI files.

### V4.1: Close secondary-display layout gaps

Acceptance:

- Navigation remains reachable at 1280x720 and 1920x1080 secondary displays.
- Phone, landscape, resizable/freeform, and expanded layouts preserve state.
- Keyboard/mouse focus, Back, display disconnect/reconnect, and window restore
  have explicit behavior.

Verification:

- Emulator secondary-display tests plus screenshots for both resolutions.
- Real Samsung DeX remains a named acceptance gate, not an emulator claim.

### V4.2: Profile HID service and battery behavior

Acceptance:

- Measure connected idle, active Trackpad, disconnected idle, visible clipboard,
  and overnight screen-off scenarios.
- Replace the 15-second HID restart loop only if tests prove a lower-wakeup
  lifecycle preserves reconnection and held-input release safety.
- Battery claims include interval, screen state, CPU/wakeup data, and device.

Verification:

- HID lifecycle/reconnect tests and before/after device or emulator evidence.

### V4.3: Cross-vertical black-box coverage

Acceptance:

- Maestro covers Deck-to-AI, generated artifact catalog/placement, clipboard
  receipts, automation stage labels, Back, rotation, and secondary display.
- Test data is isolated to `app.codecks.debug`.
- No instrumentation targets the physical production package.

Verification:

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug :app:assembleDebug`
- Maestro isolated-emulator suite

## Integration checkpoint

- Rebase each vertical on the frozen base and inspect ownership violations.
- Merge only verticals with focused tests and evidence.
- Resolve shared navigation/composition wiring in one integration branch.
- Run full Android unit/lint/debug gates, secret-surface check, no-shrink check,
  AI evaluation, Mac-helper Swift tests, and isolated-emulator Maestro.
- Perform explicit user-driven real-Mac/real-DeX acceptance later.
- Release/version/tag/push/install are separate approval and exact-artifact steps.

## Stop conditions

- Never uninstall, clear, downgrade, instrument, or differently sign
  `app.codecks`.
- Never enable release minification or resource shrinking.
- Never print, persist in tests, or commit private AI/SSH credentials.
- No worker publishes, merges to `main`, releases, or installs production.
- A passing emulator test is not real Samsung DeX, real-Mac permission, or
  physical battery proof.

---

# Active Implementation Plan: Preserve, fix, and consolidate v0.1.30

Status: approved for implementation on 2026-07-29

## Outcome

Preserve every unique dirty change, fix the confirmed Android/Mac-helper defects,
merge the result to `main`, and leave the primary repository checkout as the
canonical checkout. No worktree may be removed until its dirty state has a
named safety commit or an explicitly retained artifact location.

## Ordered work

### C1: Preserve dirty worktrees

- Commit the four current v0.1.30 deck-cleanup files on their existing branch.
- Create named preservation commits for the old foundation checkout and the
  dirty Shortcuts-handler checkout.
- Preserve `release-downloads/` outside source history; it is an artifact, not
  product code.
- Verify all other registered worktrees are clean before cleanup.

### C2: Repair navigation and deck editing

- Android Back from AI, Settings, Editor, and other More destinations returns to
  the prior in-app destination instead of exiting Codecks.
- Offline Mac buttons remain runnable-disabled but long-press management stays
  enabled.
- Deck changes use one contract: immediate persistence with undo; remove the
  contradictory staged `Apply layout` state.
- Keep management on the deck: reassign, move, duplicate, test, remove, and
  forget. Destructive catalog forgetting requires confirmation and undo.
- Replace the long editor catalog with search/category-driven bounded results
  so controls are not buried below dozens of rows.

### C3: Repair polish and adaptive behavior

- Keep labels and accessible names during move mode.
- Decouple template viewing from Favorite layout mutation.
- Fix landscape semantics, keyboard clipping, trackpad connection copy, and
  launcher/notification icon surfaces.
- Preserve portrait/landscape editor state and 48dp touch targets.

### C4: Unify Mac visual effects

- Built-in and AI-generated celebration buttons resolve through one reviewed
  Mac visual-effect catalog.
- Confetti/love/fire/focus/coffee/magic use a full-screen, click-through,
  multi-display animated overlay with bounded duration and cleanup.
- Tests assert catalog identity and reviewed-command recognition.

### C5: Preserve missing hardening

- Port Reactive provider exception isolation without reverting current policy,
  pinning, confidence, stale-state, or protocol contracts.
- Port the Shortcuts bounded runner, concurrent pipe draining, timeout kill,
  truncation reporting, safe catalog import, unknown-argument rejection, and
  capability advertisement onto the current Mac-helper runtime.
- Do not port obsolete unavailable Spotlight/SFTP stubs.

### C6: Verification and consolidation

- Focused Android unit tests, full release unit tests, lint, no-shrink, and
  debug/release assembly pass.
- Current Mac-helper Swift tests plus new hardening tests pass.
- Maestro emulator flows cover Back, offline long-press, deletion/undo,
  persistence, rotation, keyboard, and AI/Settings navigation.
- Merge the verified branch into `main`.
- Repoint the canonical `/Projects/codecks` checkout to updated `main`.
- Remove only clean or safety-committed obsolete worktrees.

## Stop conditions

- Never uninstall, clear, downgrade, or differently sign `app.codecks`.
- Never target the physical phone with instrumentation.
- Keep production minification and resource shrinking disabled.
- Stop consolidation if any dirty worktree lacks a recoverable named commit.

---

# Historical Implementation Plan: Close post-v0.1.21 gaps

Status: active remediation plan

Audit source:

- `docs/release/POST_V0.1.21_PLAN_CONFORMANCE_AUDIT.md`

Governing specifications:

- `docs/product/DESK_VALUE_LOCKSCREEN_AND_REACTIVE_EXPANSION_PLAN.md`
- `docs/reactive/REACTIVE_PLATFORM_IMPLEMENTATION_PLAN.md`
- `AGENTS.md`

## Outcome

Produce one evidence-backed release decision without risking the installed
production app.

Two checkpoints are intentionally separate:

1. **Android slice checkpoint**: deck/keyboard/deep-link/README/lockscreen and
   default-off Reactive foundation are safe enough for a bounded Android
   release.
2. **Full-plan checkpoint**: Mac helper, KMP, Reactive MVP, DeskDock, later
   integrations, iOS, and cross-platform release gates are complete.

No one may call the Android slice "the Reactive Platform complete."

## Standing invariants

- Never uninstall, clear, downgrade, or differently sign `app.codecks`.
- Never use physical-phone instrumentation without current explicit approval.
- Keep release minification and resource shrinking disabled.
- Do not publish a rebuilt APK after physical testing.
- State, proximity, Tasker, NFC, ranking, and visibility are not authorization.
- Lockscreen authority is pointer, scroll, mouse buttons, and release only.
- HID pointer callbacks must stay independent from SSH/helper/state polling.
- Unsupported capability means hidden control, not a dead control.
- Every completion claim states E0/E1/E2/E3/E4/E5 evidence.

## Dependency graph

```text
G0 freeze truth
  ├─> G1 Reactive safety repair
  ├─> G2 README evidence repair
  ├─> G3 Lockscreen lifecycle repair
  └─> G4 Release/privacy surface repair
          │
          v
      G5 UX closure
          │
          v
      G6 emulator gate
          │
          v
      G7 physical debug gate
          │
          v
      G8 exact signed Android candidate
          │
          v
      R0-R10 Reactive platform
          │
          v
      D0-D1 + I1-I5
          │
          v
      X0 full cross-platform release
```

## Phase G0: Freeze and partition the current worktree

### Task G0.1: Record source ownership and patch groups

Description: split the 72-file delta into reviewable ownership groups without
dropping user work.

Acceptance criteria:

- [ ] inventory matches `v0.1.21..HEAD` plus all untracked files;
- [ ] files are assigned to docs, UX, lockscreen, Reactive core, Reactive UI,
      tests, or release evidence;
- [ ] overlapping `MainActivity.kt` and `MouseScreen.kt` ownership is explicit;
- [ ] no reset, clean, stash, or deletion is used.

Verification:

- [ ] `git status --short`
- [ ] `git diff --check`
- [ ] `git diff --name-status v0.1.21`
- [ ] `git ls-files --others --exclude-standard`

Files:

- `tasks/todo.md`
- `docs/release/POST_V0.1.21_PLAN_CONFORMANCE_AUDIT.md`

Dependencies: none

### Task G0.2: Correct blueprint status and baseline language

Description: make the two plans distinguish historical `v0.1.19` assumptions,
current partial Android prototypes, and the no-shrink rule.

Acceptance criteria:

- [ ] no active release instruction says minified Android artifact;
- [ ] no private workstation/home path appears in public tracked docs;
- [ ] current branch/HEAD/worktree state is recorded;
- [ ] `PRE_LOCKSCREEN_BASELINE_COMMIT` remains explicitly unavailable until a
      clean commit exists;
- [ ] partial prototypes are not marked phase-complete.

Verification:

- [ ] `rg -n "signed/minified|minified APK|PLATFORM_BASELINE_COMMIT" docs/reactive docs/product`
- [ ] `python3 tools/secret_surface_check.py`
- [ ] manual link/status review

Files:

- `docs/reactive/REACTIVE_PLATFORM_IMPLEMENTATION_PLAN.md`
- `docs/product/DESK_VALUE_LOCKSCREEN_AND_REACTIVE_EXPANSION_PLAN.md`

Dependencies: G0.1

## Phase G1: Repair Reactive safety before wider testing

### Task G1.1: Bind action revision to the resolved execution contract

Description: replace static presentation-spec authorization with a canonical
revision derived from the actual resolved action, target selector, safety
fields, implementation kind, and arguments.

Acceptance criteria:

- [ ] confirmation prompt carries the actual resolved action revision;
- [ ] review prompt carries the actual command revision;
- [ ] edit/replacement between prompt and confirmation returns stale/review;
- [ ] confirmation for action A cannot authorize action B sharing a control ID;
- [ ] tests include dangerous-action swap and reviewed-command swap attacks.

Verification:

- [ ] focused executor adversarial tests
- [ ] existing `RawCommandPolicyTest`
- [ ] existing action-runner tests

Likely files:

- `app/src/main/java/io/codecks/domain/reactive/ReactiveControlModels.kt`
- `app/src/main/java/io/codecks/core/reactive/DefaultReactiveActionExecutor.kt`
- `app/src/test/java/io/codecks/core/reactive/DefaultReactiveActionExecutorTest.kt`

Dependencies: G0.1

### Task G1.2: Revalidate target, state, capability, and registry at execution

Description: execution must not trust a control merely because it was rendered.

Acceptance criteria:

- [ ] selected Mac must still equal the control target;
- [ ] state revision/TTL must still be acceptable;
- [ ] every required capability is rechecked;
- [ ] registry action/risk/revision parity is rechecked;
- [ ] stale, removed, hidden, unsupported, or contract-mismatched controls fail
      without a receipt.

Verification:

- [ ] target-switch test
- [ ] capability-loss test
- [ ] stale-state test
- [ ] registry-mismatch test
- [ ] no-receipt-on-denial test

Likely files:

- `app/src/main/java/io/codecks/domain/reactive/ReactiveExecutionModels.kt`
- `app/src/main/java/io/codecks/core/reactive/DefaultReactiveActionExecutor.kt`
- `app/src/main/java/io/codecks/data/reactive/LiveMacStateRepository.kt`
- focused tests

Dependencies: G1.1

### Task G1.3: Correct command semantics and registry coverage

Description: every displayed control must resolve to the intended reviewed
catalog action or exact HID report.

Acceptance criteria:

- [ ] Reload never maps to Command+Enter;
- [ ] all default control action IDs exist in the catalog;
- [ ] unsupported mappings are omitted before UI;
- [ ] Browser/Finder/Terminal mappings have exact expected actions;
- [ ] one contract test covers all shipped Reactive defaults.

Verification:

- [ ] default registry/catalog parity test
- [ ] HID command mapping test
- [ ] provider output tests

Likely files:

- `app/src/main/java/io/codecks/core/reactive/ReactiveTrackpadDefaults.kt`
- `app/src/main/java/io/codecks/core/reactive/DefaultReactiveActionExecutor.kt`
- `app/src/test/java/io/codecks/core/reactive/`

Dependencies: G1.2

## Phase G2: Repair README evidence

### Task G2.1: Recapture a stable manufacturer snapshot

Description: record named product IDs, visible price, regular price, promotion
state, dimensions, access timestamp, and source URL. Do not use anonymous
"visible row 01" entries.

Acceptance criteria:

- [ ] every CSV row identifies a product/SKU;
- [ ] sale inclusion policy is machine-checkable;
- [ ] current source values reproduce min/max/mean/median;
- [ ] unreachable source rows are removed or labeled unverified;
- [ ] README uses only verified calculations.

Verification:

- [ ] deterministic evidence checker reproduces every README number;
- [ ] manual open of every source;
- [ ] source access failures are recorded.

Likely files:

- `docs/marketing/desk_value_snapshot.csv`
- `docs/marketing/DESK_VALUE_EVIDENCE.md`
- `README.md`
- optional `tools/verify_desk_value_evidence.py`

Dependencies: G0.1

### Task G2.2: Make README citations directly usable

Acceptance criteria:

- [ ] each quantitative footnote includes a direct HTTPS link;
- [ ] study age and nonrepresentative limitations remain adjacent;
- [ ] manufacturer samples are never called market averages;
- [ ] space allocation is never called recovered cash;
- [ ] phone footprint caveat remains visible.

Verification:

- [ ] Markdown link check
- [ ] forbidden-claim grep from A1
- [ ] evidence checker

Files:

- `README.md`
- `docs/marketing/DESK_VALUE_EVIDENCE.md`

Dependencies: G2.1

## Phase G3: Close the lockscreen lifecycle boundary

### Task G3.1: Fail closed before credential-protected reads

Description: determine post-boot unlock/keyguard state before reading settings
or other credential-protected state.

Acceptance criteria:

- [ ] pre-first-unlock public URI exits or requires unlock without DataStore;
- [ ] no HID start/register/connect path is called;
- [ ] exceptions and unavailable services fail closed;
- [ ] forged extras cannot change the outcome.

Verification:

- [ ] pure truth-table tests
- [ ] direct-boot/locked instrumentation test where supported
- [ ] source reachability test for `hidRepository.start/connect`

Likely files:

- `TrackpadEntryActivity.kt`
- `LockscreenTrackpadPolicy.kt`
- focused tests

Dependencies: G0.1

### Task G3.2: Remove stale keyguard dispatch windows

Description: movement/click dispatch must stop immediately on lifecycle or
keyguard policy loss rather than wait for the next 750 ms poll.

Acceptance criteria:

- [ ] `onPause`, `onStop`, `onDestroy`, disconnect, and policy loss release all
      buttons;
- [ ] no pointer event dispatches after lock-state invalidation;
- [ ] screen-off/on cannot retain a held button;
- [ ] policy checks remain off the pointer hot path.

Verification:

- [ ] fake lifecycle tests
- [ ] disconnect-during-drag instrumentation test
- [ ] lock-transition instrumentation test

Likely files:

- `LockscreenTrackpadActivity.kt`
- `LockscreenTrackpadViewModel.kt`
- focused tests

Dependencies: G3.1

### Task G3.3: Add real Android route and semantics tests

Acceptance criteria:

- [ ] exact URI resolves only to `TrackpadEntryActivity`;
- [ ] launcher resolves only to `MainActivity`;
- [ ] locked allowed state renders only pointer surface and mouse buttons;
- [ ] locked denied state renders unlock/close only;
- [ ] forbidden labels/actions are absent from the Compose semantics tree;
- [ ] Back/Home/Recents reveal no protected content.

Verification:

- [ ] debug AndroidTest on managed Pixel 6 API 35
- [ ] instrumentation report contains named lockscreen test classes

Likely files:

- `app/src/androidTest/java/io/codecks/ui/mouse/lockscreen/**`
- test-only fakes/DI

Dependencies: G3.2

## Phase G4: Reconcile widget, notification, privacy, and release surfaces

### Task G4.1: Decide widget shipment state

Description: resolve the contradiction between an unconditional widget receiver
and the release ledger's disabled Widget incubator.

Acceptance criteria:

- [ ] widget is either intentionally public and documented or build/feature
      gated;
- [ ] receiver exposure and immutable explicit pending intent are documented;
- [ ] widget carries no host name or arbitrary destination;
- [ ] release-surface validation enforces the decision.

Verification:

- [ ] merged manifest inspection
- [ ] release-surface negative test
- [ ] widget pending-intent test

Likely files:

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `docs/release/CODECKS_RELEASE_LEDGER.md`
- release-surface tests

Dependencies: G3.1

### Task G4.2: Update permission/privacy and component ledgers

Acceptance criteria:

- [ ] exported Trackpad entry Activity is listed;
- [ ] exported widget receiver is listed if shipped;
- [ ] non-exported lockscreen Activity is listed;
- [ ] notification content/action behavior is documented;
- [ ] no component claim exceeds tested evidence.

Verification:

- [ ] `:app:validateReleaseSurface`
- [ ] manual merged-manifest-to-ledger comparison

Files:

- `docs/security/PERMISSION_PRIVACY_LEDGER.md`
- `docs/release/CODECKS_RELEASE_LEDGER.md`
- `app/build.gradle.kts`

Dependencies: G4.1

## Phase G5: Close user-visible papercuts

### Task G5.1: Reproduce and fix Trackpad click behavior

Acceptance criteria:

- [ ] real-finger single tap produces exactly one left click;
- [ ] movement below/above threshold is characterized;
- [ ] drag and double tap remain correct;
- [ ] ADB synthetic tap evidence is not substituted for finger evidence;
- [ ] regression test covers the root cause.

Verification:

- [ ] engine/unit test
- [ ] managed emulator gesture test
- [ ] approved physical debug test against a safe Mac target

Likely files:

- `TrackpadGestureEngine.kt`
- `MouseScreen.kt`
- focused tests

Dependencies: G3.3

### Task G5.2: Define Keyboard multi-touch behavior

Acceptance criteria:

- [ ] supported combinations are explicit;
- [ ] unsupported simultaneous taps fail predictably;
- [ ] no half-fired modifier or duplicate Enter;
- [ ] success clears text; failure preserves it;
- [ ] Mac-side target receives text and exactly one Enter.

Verification:

- [ ] Compose interaction test
- [ ] ViewModel state test
- [ ] approved physical debug test in a controlled text target

Likely files:

- `KeyboardScreen.kt`
- `KeyboardViewModel.kt`
- focused tests
- `docs/ux/PAPER_CUT_AUDIT.md`

Dependencies: G5.1

### Task G5.3: Complete accessibility/navigation matrix

Acceptance criteria:

- [ ] TalkBack labels, roles, focus order, and state wording pass;
- [ ] all touched controls meet 48 dp;
- [ ] Back behavior passes across modes/sheets/orientation;
- [ ] closed ledger rows contain test and physical evidence.

Verification:

- [ ] Compose semantics tests
- [ ] managed emulator rotation/navigation
- [ ] physical TalkBack spot check

Dependencies: G5.2

## Phase G6: Android emulator release-candidate gate

### Task G6.1: Run aggregate source gates on Java 17

Verification:

- [ ] `python3 tools/secret_surface_check.py`
- [ ] `scripts/verify_release_no_shrink.sh`
- [ ] `python3 tools/ai_creator_v2_eval.py`
- [ ] `:app:testReleaseUnitTest`
- [ ] `:app:lintDebug`
- [ ] `:app:check`
- [ ] `:app:assembleDebug`
- [ ] `python3 scripts/verify_mac_actions.py`

Dependencies: G1-G5

### Task G6.2: Run managed debug instrumentation

Acceptance criteria:

- [ ] startup, deep-link, lockscreen, widget, keyboard, Trackpad gesture, and
      Reactive-exclusion tests execute;
- [ ] report has zero failures/errors;
- [ ] APK contains dex;
- [ ] no physical device is selected.

Verification:

- [ ] `:app:pixel6Api35DebugAndroidTest`
- [ ] inspect XML report for required classes

Dependencies: G6.1

## Phase G7: Approved physical debug matrix

Description: test only `app.codecks.debug`; preserve `app.codecks`.

Rules:

- do not uninstall, clear, downgrade, overwrite, or instrument `app.codecks`;
- do not force-stop production merely to steal HID ownership;
- have the user disconnect/release production HID through normal UI;
- restore the user's preferred production state after testing.

Required matrix:

- [ ] Trackpad movement, tap, drag, right click, scroll;
- [ ] Keyboard text + exactly one Enter + clear success;
- [ ] keyboard failure preserves draft;
- [ ] keyboard multi-touch decision;
- [ ] locked connected pointer-only entry;
- [ ] locked disconnected no reconnect;
- [ ] Bluetooth off releases buttons and closes;
- [ ] pre-first-unlock fail closed;
- [ ] notification entry;
- [ ] widget unlocked entry;
- [ ] Back/Home/Recents;
- [ ] screen off/on and Mac sleep/wake;
- [ ] minimum 30-minute idle.

Dependencies: G6

## Phase G8: Exact signed Android candidate

This checkpoint is allowed only if the user accepts a bounded Android-slice
release before full Reactive Platform completion. Otherwise skip it and
continue to R0.

### Task G8.1: Prepare version and detailed notes

Acceptance criteria:

- [ ] version code/name are new and unique;
- [ ] notes distinguish completed Android features from disabled/partial
      platform work;
- [ ] evidence levels are explicit;
- [ ] no Mac helper, iOS, DeskDock-engine, or integration claim appears.

Likely files:

- `app/build.gradle.kts`
- `CHANGELOG.md`
- `README.md`
- `docs/release/RELEASE_NOTES_vX.Y.Z.md`
- `docs/release/CODECKS_RELEASE_LEDGER.md`

Dependencies: G7

### Task G8.2: Build and preserve exact signed unshrunk artifact

Acceptance criteria:

- [ ] real release signing variables are present without disclosure;
- [ ] minify/shrink remain false;
- [ ] SHA-256 recorded before device test;
- [ ] candidate cert equals installed `app.codecks` cert;
- [ ] candidate version is not a downgrade;
- [ ] candidate is never rebuilt after testing.

Verification:

- [ ] `apksigner verify --verbose --print-certs`
- [ ] certificate digest comparison
- [ ] `scripts/verify_release_no_shrink.sh <apk>`
- [ ] SHA-256 comparison

Dependencies: G8.1

### Task G8.3: Exact-artifact physical SSH/HID/lockscreen proof

Acceptance criteria:

- [ ] in-place update only after specific user approval;
- [ ] `adb install -r --no-streaming` succeeds;
- [ ] app data and selected Mac remain;
- [ ] real Mac SSH action succeeds;
- [ ] HID movement/click/keyboard succeeds;
- [ ] lockscreen matrix succeeds;
- [ ] tested hash equals publish candidate hash.

Dependencies: G8.2

### Task G8.4: Publish only after all evidence is saved

Acceptance criteria:

- [ ] clean release commit;
- [ ] signed/annotated unique tag;
- [ ] GitHub workflow passes;
- [ ] one production APK plus checksum only;
- [ ] downloaded GitHub asset hash equals tested hash.

Dependencies: G8.3

## Phase R0-R10: Complete the Reactive MVP

Execute the governing Reactive plan phase-by-phase after G8 or after a clean
non-release baseline.

### R0: ADRs, threat/privacy inventory, clean baseline

- [ ] ADR-001 through ADR-010
- [ ] exact `PLATFORM_BASELINE_COMMIT`
- [ ] tool versions and actual Gradle tasks
- [ ] platform threat model and privacy inventory
- [ ] phase progress ledger

### R1: KMP and Mac-helper scaffold

- [ ] `:shared` Android/iOS targets
- [ ] common smoke test
- [ ] Swift helper test target
- [ ] Android behavior unchanged

### R2: canonical models/schema/fixtures

- [ ] commonMain models
- [ ] canonical revisions
- [ ] valid/invalid fixtures
- [ ] Kotlin/Swift parity

### R3: authenticated protocol

- [ ] bounded framing/version negotiation
- [ ] P-256 identity and pairing transcript
- [ ] replay/sequence/deadline protection
- [ ] hostile loopback matrix

### R4: native Mac helper

- [ ] Bonjour `_codecks._tcp`
- [ ] pair/revoke UI
- [ ] front app/basic state
- [ ] permission denial/revocation
- [ ] signed local E3 proof

### R5: Android helper client

- [ ] discover/pair/auth/reconnect
- [ ] pinned helper identity
- [ ] capability status
- [ ] wrong-key/revoke/offline tests

### R6: unified Mac state

- [ ] helper backend
- [ ] bounded SSH fallback
- [ ] field-level source/freshness
- [ ] target switch isolation
- [ ] no arbitrary JXA gateway

### R7: provider suite

- [ ] Browser
- [ ] Terminal
- [ ] Finder
- [ ] Media
- [ ] Generic Window
- [ ] Undo
- [ ] deterministic max-six policy

### R8: executor/receipts/undo

- [ ] helper and bundled SSH adapters
- [ ] exact receipt store
- [ ] mute/tab/screenshot-safe undo where exact
- [ ] replay-safe execution
- [ ] revision-bound authorization

### R9: production Trackpad UI

- [ ] four visible + More
- [ ] undo first
- [ ] danger in More
- [ ] HUD/mode/result states
- [ ] no pointer-path regression
- [ ] instrumentation and performance trace

### R10: real Mac matrix

- [ ] Safari/Chrome
- [ ] Terminal/iTerm where supported
- [ ] Finder
- [ ] Music/Spotify where supported
- [ ] helper online/offline
- [ ] SSH fallback
- [ ] HID connected/disconnected

## Phase R11-R16: Complete platform breadth

Use the exact phase contracts in the governing Reactive plan:

- [ ] R11 modes, window, cursor, screenshots
- [ ] R12 clipboard and explicit selection
- [ ] R13 profiles and gestures
- [ ] R14 iOS controller
- [ ] R15 Smart reference integration
- [ ] R16 Android/Mac/iOS release hardening

Each subphase needs its own tests and physical evidence. Do not collapse them
into one implementation task.

## Phase D0-D1: DeskDock

- [ ] exact Tasker/phone recipe verified;
- [ ] NFC documented as deterministic user action;
- [ ] unified-state-dependent native score;
- [ ] hysteresis/cooldown/manual suppress;
- [ ] confidence never authorizes, unlocks, reconnects, or executes;
- [ ] measured accuracy wording.

Dependencies: R6, R8, R10

## Phase I1-I5: Later integrations

Order is fixed:

1. typed Apple Shortcuts adapter/provider;
2. reviewed provenance-bound app shortcut importer;
3. bounded Spotlight search and SFTP transfer;
4. brightness only with proven adapter;
5. Accessibility discovery last.

Every integration:

- uses typed actions;
- has strict arguments, timeouts, and output limits;
- hides when unsupported;
- never appears on lockscreen;
- never injects raw UI shell strings;
- has adapter-level and physical tests.

## Final checkpoint X0

Release-ready means:

- [ ] all governing final-definition-of-done checkboxes have evidence;
- [ ] Android signed unshrunk artifact passes exact-artifact gates;
- [ ] Mac helper is signed/notarized if shipped;
- [ ] iOS is signed and real-device tested if claimed;
- [ ] protocol compatibility/rollback/revoke pass;
- [ ] README and support matrix claim only proven features;
- [ ] tested hashes equal published hashes;
- [ ] production app was never uninstalled, cleared, downgraded, or
      differently signed.
