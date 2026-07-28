# WT04 Unified Mac State Evidence

Status: focused green

Branch: `codex/reactive-state`

Scope changed:

- `app/src/main/java/io/codecks/domain/reactive/MacStateModels.kt`
- `app/src/main/java/io/codecks/data/reactive/LiveMacStateRepository.kt`
- `app/src/main/java/io/codecks/data/reactive/state/MacStateSources.kt`
- `app/src/test/java/io/codecks/data/reactive/LiveMacStateRepositoryTest.kt`

Implemented:

- `MacStateSnapshot` can carry helper, SSH, local-cache, stale, freshness, warning, display, window, clipboard, media, meeting, and screenshot state.
- `ReactiveHelperBasicState` maps into domain state with provenance and freshness.
- `LiveMacStateRepository` prefers connected helper state, falls back to SSH state, never blocks HID callbacks, and marks stale/degraded on source failures.
- Refresh revisions stay monotonic even when source revisions move backward.
- Offline/local-cache failure semantics are explicit.

Validation:

- `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.data.reactive.LiveMacStateRepositoryTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit` -> `BUILD SUCCESSFUL`
- `git diff --check` -> clean

Notes:

- Dummy signing properties were used only to satisfy the release unit-test Gradle gate. No APK was signed, installed, or released.
- DI/root wiring intentionally deferred to coordinator-owned hotspots.
