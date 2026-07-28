# WT12 DeskDock Android signal/policy scaffold

Branch: `codex/reactive-deskdock-android`

Scope:

- Added pure Android launch-recommendation policy for DeskDock.
- Reuses `DeskDockScoringEngine`.
- Keeps native DeskDock side-effect free: policy returns recommendations only.
- Blocks background sensor launches.
- Keeps locked launches restricted to pointer-only lockscreen policy.
- Blocks launch when feature is disabled or HID is disconnected.

Files:

- `app/src/main/java/io/codecks/core/trackpad/DeskDockTrackpadLaunchPolicy.kt`
- `app/src/test/java/io/codecks/core/trackpad/DeskDockTrackpadLaunchPolicyTest.kt`

Verified:

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.core.trackpad.DeskDockTrackpadLaunchPolicyTest' \
  --tests 'io.codecks.domain.reactive.deskdock.DeskDockScoringEngineTest' \
  --tests 'io.codecks.core.trackpad.LockscreenTrackpadPolicyTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL in 23s`

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

- No native background activity launching.
- No sensor collector.
- No Tasker/NFC runtime automation change.
- No physical phone validation.
