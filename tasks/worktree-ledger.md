# Reactive Platform Worktree Ledger

Status: active

## Baseline

- Recorded at: 2026-07-27
- Base branch: `main`
- Base SHA: `4da58b279c50fec182bf52f0a73861d9b3bc22fd`
- Remote SHA: `origin/main@4da58b279c50fec182bf52f0a73861d9b3bc22fd`
- Integration branch: `codex/reactive-platform-integration`
- Integration worktree: `/Users/srinivasvaddi/Projects/codecks-worktrees/reactive/integration`
- Contract freeze SHA: `92d02c22787f6a237c1205e03eb53ccd6fb819a7`
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
| WT00 | `codex/reactive-platform-integration` | `/Users/srinivasvaddi/Projects/codecks-worktrees/reactive/integration` | `4da58b279c50fec182bf52f0a73861d9b3bc22fd` | coordination, merges, evidence, hotspots | active |
| WT01 | `codex/reactive-contracts` | `/Users/srinivasvaddi/Projects/codecks-worktrees/reactive/contracts` | `4da58b279c50fec182bf52f0a73861d9b3bc22fd` | shared protocol, fixtures, verifier, threat model | merged at `92d02c2` |

## Evidence

- Baseline fetch: complete; `main` and `origin/main` match at base SHA.
- `./gradlew :shared:jvmTest`: pass.
- `./gradlew --no-daemon :app:lintDebug`: pass.
- `./gradlew --no-daemon :app:assembleDebug`: pass.
- `./gradlew :shared:jvmTest :app:testReleaseUnitTest`: blocked by missing release signing config: `releaseStoreFile`, `releaseKeyAlias`, `releaseStorePassword`, `releaseKeyPassword`.
- Combined `:app:lintDebug :app:assembleDebug` first hit a JVM C1 compiler crash during lint; rerun with `--no-daemon` passed.
- `python3 tools/verify_protocol_fixtures.py`: pass after WT01 merge.
- `./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test`: pass after WT01 merge.
- Contract freeze: merged and focused green at `92d02c22787f6a237c1205e03eb53ccd6fb819a7`.
