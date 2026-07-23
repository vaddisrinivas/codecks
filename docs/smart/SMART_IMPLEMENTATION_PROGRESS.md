# Smart implementation progress

Baseline: `v0.1.19`
Baseline commit: `2d73fe4fc1b3504f475d7e5a774010e4d3fd189d`
Working branch: `codex/smart-hardening-finish`
Implementation/evidence commit: `148364aac33b3d1b72b03533f4d612d89be4b905`
Evidence snapshot: 2026-07-23

## Status contract

Status values are `Not started`, `In progress`, `Blocked`, and `Complete`.

A phase may be marked `Complete` only after:

1. Required code and documentation are committed.
2. Required tests pass from the committed tree.
3. Verification evidence is recorded here.
4. Remaining blockers are documented.
5. No unrelated changes are included.

The implementation and evidence baseline are committed at
`148364aac33b3d1b72b03533f4d612d89be4b905`, and the required gates were
independently rerun against that implementation. This later docs-only change
closes the ledger; it does not claim the implementation commit already contains
the final `Complete` status text.

| Phase | Description | Status | Evidence | Commit/PR |
|---|---|---|---|---|
| 0 | Baseline and tracking | Complete | [Phase 0](#phase-0--baseline-and-tracking) | `148364aac33b3d1b72b03533f4d612d89be4b905` |
| 1 | Typed Smart models | Complete | [Phase 1](#phase-1--typed-smart-models) | `148364aac33b3d1b72b03533f4d612d89be4b905` |
| 2 | Typed local results and MacInput | Complete | [Phase 2](#phase-2--typed-local-results-and-macinput) | `148364aac33b3d1b72b03533f4d612d89be4b905` |
| 3 | Learning and suppression semantics | Complete | [Phase 3](#phase-3--learning-and-suppression-semantics) | `148364aac33b3d1b72b03533f4d612d89be4b905` |
| 4 | Opaque Mac identity and privacy | Complete | [Phase 4](#phase-4--opaque-mac-identity-and-privacy) | `148364aac33b3d1b72b03533f4d612d89be4b905` |
| 5 | SmartDeckViewModel extraction | Complete | [Phase 5](#phase-5--smartdeckviewmodel-extraction) | `148364aac33b3d1b72b03533f4d612d89be4b905` |
| 6 | Candidate provider architecture | Complete | [Phase 6](#phase-6--candidate-provider-architecture) | `148364aac33b3d1b72b03533f4d612d89be4b905` |
| 7 | Signed release emulator gate | Complete | [Phase 7](#phase-7--signed-release-emulator-gate) | `148364aac33b3d1b72b03533f4d612d89be4b905` |
| 8 | Final integration and audit | Complete | [Phase 8](#phase-8--final-integration-and-audit) | `148364aac33b3d1b72b03533f4d612d89be4b905` |

## Verified test snapshot

The following independently verified reports and artifacts correspond to the
implementation commit:

- Release unit tests: 341 tests, 0 failures, 0 errors, 2 skipped.
- Release managed emulator:
  `MainActivityStartupInstrumentedTest#coldStartReachesResumedActivityAndProcessSurvives`,
  1 test, 0 failures.
- Debug managed emulator: 12 tests, 0 failures, 1 skipped.
- Release app APK: 4,755,457 bytes; one 4,151,344-byte `classes.dex`.
- Release instrumentation APK: 1,778,954 bytes; one 1,605,968-byte
  `classes.dex`.
- Debug app APK: 78,936,810 bytes.
- R8 release outputs:
  - `mapping.txt`: 56,167,319 bytes, 407,115 lines.
  - `usage.txt`: 5,525,168 bytes, 82,751 lines.
  - `seeds.txt`: 132,045 bytes, 1,504 lines.
  - `configuration.txt`: 68,387 bytes, 1,088 lines.
- R8 release-test outputs:
  - `mapping.txt`: 2,867,059 bytes, 29,462 lines.
  - `usage.txt`: 8,525 bytes, 180 lines.

These are committed-tree local results, not evidence of a published release.

## Phase evidence

### Phase 0 — Baseline and tracking

Implemented:

- Added the Smart hardening plan, decision log, privacy updates, release-gate
  documentation, and this evidence ledger.
- Replaced nonexistent phase branch names with the actual integration branch
  and implementation commit.

Verified:

- Baseline tag and commit are recorded.
- `git diff --check` passes.
- The implementation is committed at the recorded implementation commit.

Remaining:

- None.

### Phase 1 — Typed Smart models

Implemented:

- Smart action kind, capability, surface, feedback, IDs, app key, Mac ID, and
  candidate fields use typed enums/value classes.
- Codecs fail safely on unknown typed values.

Verified:

- `SmartModelsTest`, `SmartLearningCodecTest`, and
  `DeterministicSmartEngineTest` pass in the release unit suite.

Remaining:

- None.

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

- None.

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

- None.

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

- None.

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

- None.

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

- None.

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
  :app:pixel6Api35DebugAndroidTest`: 12 tests, 0 failures, 1 skipped.
- Release APK and release test APK both contain dex code.
- Release and release-test R8 mapping/usage reports contain real output; exact
  metrics are recorded in the verified snapshot above.
- `shellcheck`, `bash -n`, and `actionlint` pass.
- `signed_release_emulator_smoke_test.sh` parser fixtures pass for window-focus,
  resumed-activity, relative activity, and no-focus inputs.
- `python3 tools/secret_surface_check.py` passes after placeholder-only emulator
  documentation.

Remaining:

- None within the approved emulator-only implementation scope. The workflow
  remains a release-time gate; no release publication is claimed here.

### Phase 8 — Final integration and audit

Verified locally:

- All named build, test, lint, policy, script, and emulator gates passed.
- Full release unit report: 341 tests, 0 failures, 0 errors, 2 skipped.
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
- No release publication.

Remaining:

- None within the approved scope. Live Mac/SSH and physical-phone verification
  remain explicitly outside the approved emulator-only Phase 7 contract.
