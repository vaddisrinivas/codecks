# WT13 Apple Shortcuts provider scaffold

Branch: `codex/reactive-platform-integration`

Scope:

- Added typed Apple Shortcuts catalog/parser models.
- Added `AppleShortcutsReactiveControlProvider`.
- Wired provider into the default Reactive Trackpad engine with an empty catalog by default.
- Generated controls are `ReactiveAction.Helper("apple_shortcuts.run", typed arguments)`.
- Controls require review by default.
- No shell command strings are executed.
- No helper transport execution is implemented in this slice.

Files:

- `app/src/main/java/io/codecks/domain/reactive/providers/AppleShortcutsReactiveControlProvider.kt`
- `app/src/main/java/io/codecks/domain/reactive/ReactiveControlModels.kt`
- `app/src/main/java/io/codecks/core/reactive/ReactiveTrackpadDefaults.kt`
- `app/src/test/java/io/codecks/domain/reactive/providers/ReactiveStateControlProvidersTest.kt`

Verified:

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.domain.reactive.providers.ReactiveStateControlProvidersTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL in 22s`

```text
git diff --check
```

Result: passed.

```text
scripts/verify_release_no_shrink.sh
```

Result:

```text
verified app/build.gradle.kts:218:             isMinifyEnabled = false
verified app/build.gradle.kts:219:             isShrinkResources = false
Release no-shrink invariant verified.
```

Not done:

- No `shortcuts` CLI runner.
- No helper-side shortcut importer.
- No real execution of `ReactiveAction.Helper`.
- No timeout/output-limit enforcement beyond typed control surface.
