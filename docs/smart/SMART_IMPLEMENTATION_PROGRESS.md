# Smart implementation progress

Baseline: `v0.1.19`
Baseline commit: `2d73fe4fc1b3504f475d7e5a774010e4d3fd189d`
Working branch: `codex/smart-hardening-finish`
Evidence snapshot: 2026-07-23

## Status contract

Status values are `Not started`, `In progress`, `Blocked`, and `Complete`.

A phase may be marked `Complete` only after:

1. Required code and documentation are committed.
2. Required tests pass from the committed tree.
3. Verification evidence is recorded here.
4. Remaining blockers are documented.
5. No unrelated changes are included.

The current implementation and evidence are not committed. Therefore every
phase remains `In progress`, even where its implementation and focused checks
are green. The final integration commit is the remaining completion gate.

| Phase | Description | Status | Evidence | Commit/PR |
|---|---|---|---|---|
| 0 | Baseline and tracking | In progress | [Phase 0](#phase-0--baseline-and-tracking) | Pending final commit |
| 1 | Typed Smart models | In progress | [Phase 1](#phase-1--typed-smart-models) | Pending final commit |
| 2 | Typed local results and MacInput | In progress | [Phase 2](#phase-2--typed-local-results-and-macinput) | Pending final commit |
| 3 | Learning and suppression semantics | In progress | [Phase 3](#phase-3--learning-and-suppression-semantics) | Pending final commit |
| 4 | Opaque Mac identity and privacy | In progress | [Phase 4](#phase-4--opaque-mac-identity-and-privacy) | Pending final commit |
| 5 | SmartDeckViewModel extraction | In progress | [Phase 5](#phase-5--smartdeckviewmodel-extraction) | Pending final commit |
| 6 | Candidate provider architecture | In progress | [Phase 6](#phase-6--candidate-provider-architecture) | Pending final commit |
| 7 | Signed release emulator gate | In progress | [Phase 7](#phase-7--signed-release-emulator-gate) | Pending final commit |
| 8 | Final integration and audit | In progress | [Phase 8](#phase-8--final-integration-and-audit) | Pending final commit |

## Verified test snapshot

The following reports and artifacts exist from the current working tree:

- Release unit tests: 337 tests, 0 failures, 0 errors, 2 skipped.
- Release managed emulator:
  `MainActivityStartupInstrumentedTest#coldStartReachesResumedActivityAndProcessSurvives`,
  1 test, 0 failures.
- Debug managed emulator: `MouseScreenGestureInstrumentedTest`, 7 tests,
  0 failures.
- Release app APK: 4,739,073 bytes; one 4,143,884-byte `classes.dex`.
- Release instrumentation APK: 1,793,770 bytes; one 1,620,784-byte
  `classes.dex`.
- Debug app APK: 78,904,042 bytes.
- R8 release outputs:
  - `mapping.txt`: 56,127,804 bytes, 406,773 lines.
  - `usage.txt`: 5,521,850 bytes, 82,695 lines.
  - `seeds.txt`: 132,045 bytes, 1,504 lines.
  - `configuration.txt`: 68,387 bytes, 1,088 lines.
- R8 release-test outputs:
  - `mapping.txt`: 2,903,571 bytes, 29,826 lines.
  - `usage.txt`: 8,687 bytes, 184 lines.

These are local working-tree results, not evidence of a completed GitHub release
workflow or a published release.

## Phase evidence

### Phase 0 — Baseline and tracking

Implemented:

- Added the Smart hardening plan, decision log, privacy updates, release-gate
  documentation, and this evidence ledger.
- Replaced nonexistent phase branch names with the actual integration branch
  and a pending final commit.

Verified:

- Baseline tag and commit are recorded.
- `git diff --check` passes.
- The working tree is intentionally dirty and uncommitted.

Remaining:

- Commit the reviewed integrated change set.

### Phase 1 — Typed Smart models

Implemented:

- Smart action kind, capability, surface, feedback, IDs, app key, Mac ID, and
  candidate fields use typed enums/value classes.
- Codecs fail safely on unknown typed values.

Verified:

- `SmartModelsTest`, `SmartLearningCodecTest`, and
  `DeterministicSmartEngineTest` pass in the release unit suite.

Remaining:

- Commit and rerun from the committed tree.

### Phase 2 — Typed local results and MacInput

Implemented:

- Local actions return `Succeeded`, `Navigated`, or `Failed`.
- `SmartCapability.MacInput` is separate from action kind and Mac command
  capability.
- HID/media execution fails explicitly when Mac input is unavailable.

Verified:

- `LocalActionCapabilitiesTest`, `LocalActionResultTest`, and
  `LocalActionDispatcherTest` pass.
- Smart behavior tests cover local success and failure feedback.

Remaining:

- Commit and rerun from the committed tree.

### Phase 3 — Learning and suppression semantics

Implemented:

- Temporary hide is memory-only.
- Context suppression and global suppression are separate.
- Null app context is canonicalized as `any`, so context suppression survives
  restart.
- Transition learning is scoped by surface, app, opaque Mac ID, and time.
- Duplicate history scoring was removed.
- Learning storage writes schema v2 and migrates schema v1 records.

Verified:

- `SmartLearningCodecTest`: 19 tests, 0 failures.
- `DeterministicSmartEngineTest`: 12 tests, 0 failures.
- Tests cover schema-v1 migration, unknown-schema fail-closed behavior,
  context/global suppression, null-app persistence, transition scope, and
  non-duplicated history scoring.

Remaining:

- Commit and rerun from the committed tree.

### Phase 4 — Opaque Mac identity and privacy

Implemented:

- New connection targets receive persisted random UUID identities.
- Endpoint-derived legacy IDs migrate to opaque UUIDs.
- Legacy aliases are migration-only and no longer remain runtime selector
  compatibility.
- Deck and Rule/automation `SpecificDevice` selectors migrate persistently to
  the opaque ID.
- Undecodable connection, Deck, and automation payloads are quarantined before
  defaults or migrated data are written.
- Smart persistence excludes username, hostname, port, IP, SSH host key, and
  notification source labels.

Verified:

- `ConnectionTargetIdentityMigrationTest`: 10 tests, 0 failures.
- `PersistedTargetSelectorMigrationTest`: 4 tests, 0 failures.
- `ExecutionPlannerTargetAliasTest`: 1 test, 0 failures.
- Instrumentation coverage exists for connection identity and Smart context
  repository behavior.

Remaining:

- Commit and rerun migration tests from the committed tree.

### Phase 5 — SmartDeckViewModel extraction

Implemented:

- `SmartDeckViewModel` owns context refresh, ranking, suppression, run state,
  and feedback.
- `MainActivity` executes emitted effects and reports acceptance, rejection,
  and completion.
- A Smart run is pending only after execution acceptance.
- Busy rejection clears the request without false success feedback.
- Each accepted run retains the immutable execution-time context snapshot, so
  later app/Mac context changes cannot corrupt learning.
- Unknown or stale run IDs cannot complete another run.

Verified:

- `SmartDeckViewModelBehaviorTest`: 15 tests, 0 failures.
- `SmartDeckViewModelSourcePolicyTest` passes.
- `HomeViewModelTest` and `HomeScreenBehaviorTest` pass in the full unit suite.

Remaining:

- Commit and rerun from the committed tree.

### Phase 6 — Candidate provider architecture

Implemented:

- Candidate generation is provider-based and deterministically merged.
- Recent-action, transition, active-app, and connection-repair providers are
  wired through the engine.
- App-action mapping asset loading requires numeric `schemaVersion == 1`;
  missing, nonnumeric, malformed, and unsupported schemas fail closed.

Verified:

- `SmartCandidateProviderTest`: 10 tests, 0 failures.
- `SmartAppActionMappingsTest`: 6 tests, 0 failures.
- Provider ranking, merge, capability filtering, schema validation, and invalid
  entry filtering are covered.

Remaining:

- Commit and rerun from the committed tree.

### Phase 7 — Signed release emulator gate

Approved scope:

- The original physical-phone/real-Mac SSH gate was replaced by an
  exact-artifact Android emulator gate.
- This phase does not claim live SSH, bundled Mac action, real-Mac, or
  physical-phone proof.

Implemented:

- CI builds the signed/minified release app and release instrumentation APK
  before testing.
- Manual dispatch is accepted only from the default branch, resolves the input
  as `refs/tags/<release-tag>`, and proves checkout `HEAD` equals the peeled tag
  commit before signing.
- Workflow permissions default to `contents: read`; only the publish job receives
  `contents: write`, and verifier/build checkouts do not persist credentials.
- CI rejects a release test APK without `classes.dex`.
- The product APK and CI-internal test APK hashes are checked before and after
  the managed-device run.
- Only the product APK and `SHA256SUMS.txt` enter the release candidate and
  GitHub release. The signed test APK and managed-device reports remain
  short-lived CI-internal artifacts.
- KVM availability is explicitly checked on the hosted runner.
- Release instrumentation runs only the release-safe cold-start/process test.
- Compose gesture/UI tests live in `app/src/androidTestDebug/` and run against
  the debug build with `-PcodecksInstrumentedTestBuildType=debug`.
- Production R8 rules remain narrow; test-only keeps live in
  `app/proguard-android-test-rules.pro`.
- `scripts/signed_release_emulator_smoke.sh` rejects phone serials. The old
  `physical_release_ssh_smoke.sh` path is a deprecated compatibility wrapper.
- Foreground restoration extracts a validated `package/activity` component from
  window or resumed-activity output before calling `am start`.

Verified:

- `:app:pixel6Api35ReleaseAndroidTest`: 1 test, 0 failures.
- `-PcodecksInstrumentedTestBuildType=debug
  :app:pixel6Api35DebugAndroidTest`: 7 tests, 0 failures.
- Release APK and release test APK both contain dex code.
- Release and release-test R8 mapping/usage reports contain real output; exact
  metrics are recorded in the verified snapshot above.
- `shellcheck`, `bash -n`, and `actionlint` pass.
- `signed_release_emulator_smoke_test.sh` parser fixtures pass for window-focus,
  resumed-activity, relative activity, and no-focus inputs.
- `python3 tools/secret_surface_check.py` passes after placeholder-only emulator
  documentation.

Remaining:

- GitHub Actions has not run this workflow from the final committed tree.
- Commit and rerun the release workflow before publication.

### Phase 8 — Final integration and audit

Verified locally:

- Full release unit report: 337 tests, 0 failures, 0 errors, 2 skipped.
- `:app:check` passed with disposable local signing configuration.
- Debug and release Kotlin compilation passed.
- Debug, release, and release-instrumentation APK assembly passed.
- Release and debug managed-emulator suites passed as recorded above.
- `python3 tools/secret_surface_check.py` passed.
- `python3 tools/ai_creator_v2_eval.py` passed.
- `python3 scripts/verify_mac_actions.py` passed.
- Release candidate checksum creation and verification passed locally.
- `git diff --check` passes.

Not claimed:

- No physical phone run.
- No live SSH or real-Mac bundled action run.
- No GitHub release workflow run from the final commit.
- No release publication.

Remaining:

1. Finish final review.
2. Commit the integrated change set.
3. Rerun required gates from the committed tree.
4. Only then mark phases 0–8 `Complete`.
