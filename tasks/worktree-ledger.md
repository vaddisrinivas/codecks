# Reactive Platform Worktree Ledger

Status: active

## Baseline

- Recorded at: 2026-07-27
- Base branch: `main`
- Base SHA: `4da58b279c50fec182bf52f0a73861d9b3bc22fd`
- Remote SHA: `origin/main@4da58b279c50fec182bf52f0a73861d9b3bc22fd`
- Integration branch: `codex/reactive-platform-integration`
- Integration worktree: `<codecks-worktrees>/reactive/integration`
- Contract freeze SHA: `eab91f0952facf40570e054ac3109768d18a09df`
- Planning checkout note: primary checkout had uncommitted docs-only edits in `tasks/plan.md` and `tasks/todo.md`; implementation branches are based on the clean recorded base SHA.

## Boundaries

- Protected Android package: `app.codecks`; never uninstall, clear, downgrade, or differently sign.
- Release builds keep minification and resource shrinking disabled.
- Physical phone instrumentation requires explicit current user request.
- Shared protocol paths freeze after WT01 merges.
- Root/build hotspots stay coordinator-owned after freeze.

## Worktrees

| ID | Branch | Worktree | Base SHA | Scope | State |
|---|---|---|---|---|---|
| WT00 | `codex/reactive-platform-integration` | `<codecks-worktrees>/reactive/integration` | `4da58b279c50fec182bf52f0a73861d9b3bc22fd` | coordination, merges, evidence, hotspots | active |
| WT01 | `codex/reactive-contracts` | `<codecks-worktrees>/reactive/contracts` | `4da58b279c50fec182bf52f0a73861d9b3bc22fd` | shared protocol, fixtures, verifier, threat model | merged at `eab91f0` |
| WT02 | `codex/reactive-mac-helper` | `<codecks-worktrees>/reactive/mac-helper` | `cdd147d69e7b0de807ad2996aecc151055b7f8c9` | native Mac helper scaffold | merged |
| WT03 | `codex/reactive-android-helper` | `<codecks-worktrees>/reactive/android-helper` | `eab91f0952facf40570e054ac3109768d18a09df` | Android helper client scaffold | merged at `be26db8` |
| WT18 | `codex/reactive-release-evidence` | `<codecks-worktrees>/reactive/release-evidence` | `cdd147d69e7b0de807ad2996aecc151055b7f8c9` | release evidence/no-shrink/secret harness | merged |
| WT04 | `codex/reactive-state` | `<codecks-worktrees>/reactive/state` | `cdd147d69e7b0de807ad2996aecc151055b7f8c9` | unified helper/SSH Mac state | merged |
| WT05 | `codex/reactive-engine` | `<codecks-worktrees>/reactive/engine` | `70c91135d45a20e3647f3014cb3ee1f56b23363c` | policy, ranking, conflicts, stale-state handling, default app controls | merged at `06c0d8e` |
| WT06 | `codex/reactive-execution` | `<codecks-worktrees>/reactive/execution` | `70c91135d45a20e3647f3014cb3ee1f56b23363c` | receipts, idempotency, timeout, undo scaffold | merged at `4e945ba` |
| WT07 | `codex/reactive-providers` | `<codecks-worktrees>/reactive/providers` | `bdee9bbb3c746c2a0bddb5de3ae13eaab77f1b0c` | media, window, and receipt-owned undo providers | merged at `7d5880a` |
| WT08 | `codex/reactive-profiles` | `<codecks-worktrees>/reactive/profiles` | `70c91135d45a20e3647f3014cb3ee1f56b23363c` | profile resolver, pinned/hidden/disabled/max-control preferences | merged at `bb43127` |
| WT09 | `codex/reactive-ui` | `<codecks-worktrees>/reactive/ui` | `1dc489a91cf81136914440adfe4af58acbb0d036` | Android receipt-store sharing and undo-control refresh | merged at `143401c` |
| WT10 | `codex/reactive-receipt-semantics` | `<codecks-worktrees>/reactive/receipt-semantics` | `73eba1fbaf033a1f3ed480ca2d1b09ac8d3724aa` | success-only receipt semantics | merged at `dedbfce` |
| WT11 | `codex/reactive-deskdock` | `<codecks-worktrees>/reactive/deskdock` | `bdee9bbb3c746c2a0bddb5de3ae13eaab77f1b0c` | pure DeskDock scoring, hysteresis, dwell, cooldown, manual override | merged at `8c69d7f` |

## Evidence

- Baseline fetch: complete; `main` and `origin/main` match at base SHA.
- `./gradlew :shared:jvmTest`: pass.
- `./gradlew --no-daemon :app:lintDebug`: pass.
- `./gradlew --no-daemon :app:assembleDebug`: pass.
- `./gradlew :shared:jvmTest :app:testReleaseUnitTest`: blocked by missing release signing config: `releaseStoreFile`, `releaseKeyAlias`, `releaseStorePassword`, `releaseKeyPassword`.
- Combined `:app:lintDebug :app:assembleDebug` first hit a JVM C1 compiler crash during lint; rerun with `--no-daemon` passed.
- `python3 tools/verify_protocol_fixtures.py`: pass after WT01 merge.
- `./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test`: pass after WT01 merge.
- Contract freeze: merged and focused green at `eab91f0952facf40570e054ac3109768d18a09df`.
- WT03 Android helper focused test: `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.platform.helper.*' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass. Dummy signing properties only; no APK signed or installed.
- WT18 release evidence gates: `python3 tools/secret_surface_check.py` pass; `scripts/verify_release_no_shrink.sh` pass with exact release Gradle lines.
- WT04 state focused test: `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.data.reactive.LiveMacStateRepositoryTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass. Dummy signing properties only; no APK signed or installed.
- WT02 Mac helper test: `cd macHelper && swift test`: pass, 5 tests, 0 failures.
- Integration checkpoint after WT02/WT03/WT04/WT18: protocol fixtures pass; shared JVM/iOS pass; focused Android helper/state release unit tests pass with dummy signing props; secret surface pass; no-shrink pass; Mac helper Swift tests pass.
- WT05 engine focused test: `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.*Reactive*' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass.
- WT06 execution focused test: `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' --tests 'io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModelTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass.
- WT07 providers focused test: `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.providers.*' --tests 'io.codecks.domain.reactive.ReactiveEngineTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass. Dummy signing properties only; no APK signed or installed.
- WT08 profiles focused test: `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.*Profile*' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass.
- WT09 UI undo focused test: `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModelTest' --tests 'io.codecks.domain.reactive.providers.ReactiveStateControlProvidersTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass. Dummy signing properties only; no APK signed or installed.
- WT10 receipt semantics focused test: `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' --tests 'io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModelTest' --tests 'io.codecks.domain.reactive.providers.ReactiveStateControlProvidersTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass. Dummy signing properties only; no APK signed or installed.
- Integration checkpoint after WT05/WT06/WT08: protocol fixtures pass; shared JVM/iOS pass; focused Android Reactive/helper/state release unit tests pass with dummy signing props; secret surface pass; no-shrink pass; Mac helper Swift tests pass; `git diff --check` pass.
- WT11 DeskDock focused test: `./gradlew :app:testReleaseUnitTest --tests '*DeskDock*' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`: pass. Dummy signing properties only; no APK signed or installed.
- Integration checkpoint after WT11: focused DeskDock release unit tests pass; secret surface pass; no-shrink pass; `git diff --check` pass.
- Integration checkpoint after WT07/WT11: protocol fixtures pass; shared JVM/iOS pass; focused Android Reactive/provider/DeskDock/helper/state release unit tests pass with dummy signing props; secret surface pass; no-shrink pass; Mac helper Swift tests pass; `git diff --check` pass.
- Integration checkpoint after WT09: focused Android Reactive UI/provider/core release unit tests pass with dummy signing props; secret surface pass; no-shrink pass; `git diff --check` pass.
- Integration checkpoint after WT10: protocol fixtures pass; shared JVM/iOS pass; focused Android Reactive/provider/DeskDock/helper/state release unit tests pass with dummy signing props; secret surface pass; no-shrink pass; Mac helper Swift tests pass; `git diff --check` pass.
