# WT27 V4 Adaptive + Harness Evidence

Status: integrated and emulator-verified

Branch: `codex/v4-dex-adaptive-harness`

Scope changed:

- `app/src/main/java/io/codecks/MainActivity.kt`
- `app/src/main/java/io/codecks/ui/app/CodecksAppShell.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/io/codecks/DexAdaptivePolicyTest.kt`
- `app/src/test/java/io/codecks/HidSessionServicePolicyTest.kt`
- `tasks/maestro/v4-cross-vertical.yaml`
- `tasks/maestro/v4-cross-vertical-expanded.yaml`
- `tasks/maestro/run-v4-cross-vertical-maestro.sh`

Implemented:

- Added saveable top-route state for adaptive shell restore across activity reconfiguration (`rememberSaveable` + `navRouteFromStateKey`/`routeStateKey`).
- Added explicit `configChanges` handling for orientation/screen size/screen layout/smallestScreenSize/keyboard on `MainActivity` to avoid activity recreation during freeform/expanded transitions.
- Externalized adaptive rail threshold for secondary/expanded windows at `840.dp` and made policy test coverage assert it.
- Added policy assertions for manifest state-handling and keepalive interval in HID policy tests.

Checks executed:

```text
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
./gradlew :app:testReleaseUnitTest --tests 'io.codecks.DexAdaptivePolicyTest' --tests 'io.codecks.HidSessionServicePolicyTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
./gradlew :app:lintDebug :app:assembleDebug
```

Result:

- `git diff --check` passed.
- Unit + lint/build passed after setting `ANDROID_HOME`/`ANDROID_SDK_ROOT`.

Maestro evidence:

- `tasks/maestro/v4-cross-vertical.yaml`
- `tasks/maestro/v4-cross-vertical-expanded.yaml`
- `tasks/maestro/run-v4-cross-vertical-maestro.sh`
- Secondary display smoke sizes: `1280x720`, `1920x1080`
- App package: `app.codecks.debug`
- No physical `app.codecks` package targeted.
- Compact 1280×720 flow passed.
- Expanded 1920×1080 flow passed.
- Identity-size compact flow passed.
- The harness now fails closed unless its explicit target is an emulator.

Notes:

- No code changes to 15-second HID keepalive behavior were made until runtime profiling evidence is captured.
- A Gradle-managed API 35 emulator completed 15 tests with zero failures and one intentional live-SSH skip.
- Android's overlay display on the standalone API 36 emulator reported `canHostTasks=false`; real Samsung DeX task hosting remains a separate physical acceptance boundary.
