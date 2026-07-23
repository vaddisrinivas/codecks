# Signed release emulator smoke gate

This gate validates the exact signed/minified release APK and its release
instrumentation APK on an Android emulator. It does not claim real-Mac SSH
coverage and must never select a physical phone.

## Controls

- `ADB_SERIAL` may select an emulator explicitly.
- `EMULATOR_ONLY` must remain `true`.
- The script rejects serials that do not start with `emulator-`.
- CI uses the configured `pixel6Api35` Gradle managed device; no physical runner
  or repository variable can enable phone testing.
- Manual dispatch must run from the default branch and name an existing
  `refs/tags/<release-tag>`. Verifier and build jobs prove checkout `HEAD`
  matches the peeled tag commit before signing.
- Workflow permissions default to repository-content read access. Only the
  publish job receives content write access.

## Local connected-emulator helper behavior

1. Build a signed/minified release APK once.
2. Build `assembleReleaseAndroidTest`; the test APK must contain `classes.dex`.
3. Back up the current installed APK/package state before installing.
4. Install both exact artifacts on an emulator with `adb install -r`.
5. Run
   `MainActivityStartupInstrumentedTest#coldStartReachesResumedActivityAndProcessSurvives`.
6. Archive instrumentation, logcat, and package-state output.
7. Restore the previous foreground component after the test.
8. Publish only from the exact tested release APK.

CI performs the same startup/instrumentation contract with
`:app:pixel6Api35ReleaseAndroidTest`. It runs only the release-safe,
non-Compose startup test. It checks the release APK and CI-internal test APK
hashes before and after the managed-device task. Only the unchanged product APK
and its checksum are uploaded or published; the signed test APK is never a
release candidate. The local script is for an already-running emulator.

The shared `app/src/androidTest/` source set contains only the release-safe
startup smoke. Functional tests live in `app/src/androidTestDebug/`.
`app/src/androidTestRelease/` is intentionally empty.

## Release and debug coverage

- Release instrumentation proves the minified product cold-starts, reaches a
  resumed `MainActivity`, and survives process initialization.
- Internal Smart/domain contracts run as local unit tests. Release
  instrumentation must not retain internal app class names merely so a
  separately shrunk test APK can reference them.
- Identity migration, Smart context, physical-SSH opt-in, and Compose
  gesture/UI tests stay in `app/src/androidTestDebug/`. They must not force
  app-internal, Kotlin, coroutines, Compose, or lifecycle blanket keeps into
  the production R8 configuration.
- Run the release startup gate with:

```bash
./gradlew --no-daemon \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  :app:pixel6Api35ReleaseAndroidTest
```

- Run debug functional coverage with:

```bash
./gradlew --no-daemon \
  -PcodecksInstrumentedTestBuildType=debug \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  :app:pixel6Api35DebugAndroidTest
```

## Current local evidence

Working-tree snapshot from 2026-07-23:

- Release managed emulator: 1 cold-start/process-survival test passed.
- Debug managed emulator: 12 tests discovered; 11 passed and the
  physical-Mac SSH test was explicitly skipped.
- Release app APK: 4,755,457 bytes with one 4,151,344-byte `classes.dex`.
- Release test APK: 1,778,954 bytes with one 1,605,968-byte `classes.dex`.
- R8 product mapping: 56,167,319 bytes; removed-code report:
  5,525,168 bytes.
- R8 release-test mapping: 2,867,059 bytes; removed-code report: 8,525
  bytes.

These measurements prove local emulator execution and real R8 output only.
They do not prove the GitHub workflow has run from the final commit. Detailed
evidence is in
[Smart implementation progress](../smart/SMART_IMPLEMENTATION_PROGRESS.md).

## Build and verify

Use the configured release signing environment:

```bash
./gradlew --no-daemon :app:assembleRelease :app:assembleReleaseAndroidTest
unzip -Z1 app/build/outputs/apk/androidTest/release/app-release-androidTest.apk \
  | grep -E '^classes([0-9]+)?\.dex$'
```

Run against a specific emulator:

```bash
ADB_SERIAL=emulator-SERIAL EMULATOR_ONLY=true \
  ./scripts/signed_release_emulator_smoke.sh \
  app/build/outputs/apk/release/app-release.apk \
  app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
```

Pass criteria:

```bash
grep -q "OK (" app/build/outputs/apk/release/emulator-instrumentation.txt
grep -q "Signed-release emulator smoke passed" \
  app/build/outputs/apk/release/emulator-instrumentation.txt
```

- `emulator-instrumentation.txt`
- `emulator-logcat.txt`
- `package-state.txt`

## Excluded proof

This gate does not prove:

- a live SSH connection to a Mac;
- a bundled Finder action on a Mac;
- physical-phone behavior.

Those require a separately authorized real-device/real-Mac gate.
