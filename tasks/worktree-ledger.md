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
| WT03 | `codex/reactive-android-helper` | `<codecks-worktrees>/reactive/android-helper` | `eab91f0952facf40570e054ac3109768d18a09df` | Android helper client scaffold | merged at `be26db8` |
| WT18 | `codex/reactive-release-evidence` | `<codecks-worktrees>/reactive/release-evidence` | `cdd147d69e7b0de807ad2996aecc151055b7f8c9` | release evidence/no-shrink/secret harness | merged |
| WT04 | `codex/reactive-state` | `<codecks-worktrees>/reactive/state` | `cdd147d69e7b0de807ad2996aecc151055b7f8c9` | unified helper/SSH Mac state | merged |

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
