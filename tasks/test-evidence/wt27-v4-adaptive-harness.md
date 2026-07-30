# WT27 V4 Adaptive + Harness Evidence

Status: completed (execution blockers noted)

Branch: `codex/v4-dex-adaptive-harness`

Scope changed:

- `app/src/main/java/io/codecks/MainActivity.kt`
- `app/src/main/java/io/codecks/ui/app/CodecksAppShell.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/io/codecks/DexAdaptivePolicyTest.kt`
- `app/src/test/java/io/codecks/HidSessionServicePolicyTest.kt`
- `tasks/maestro/v4-cross-vertical.yaml`
- `tasks/maestro/run-v4-cross-vertical-maestro.sh`

Implemented:

- Added saveable top-route state for adaptive shell restore across activity reconfiguration (`rememberSaveable` + `navRouteFromStateKey`/`routeStateKey`).
- Added explicit `configChanges` handling for orientation/screen size/screen layout/smallestScreenSize/keyboard on `MainActivity` to avoid activity recreation during freeform/expanded transitions.
- Externalized adaptive rail threshold for secondary/expanded windows at `840.dp` and made policy test coverage assert it.
- Added policy assertions for manifest state-handling and keepalive interval in HID policy tests.

Checks executed:

```text
export ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk
export ANDROID_SDK_ROOT=/Users/srinivasvaddi/Library/Android/sdk
./gradlew :app:testReleaseUnitTest --tests 'io.codecks.DexAdaptivePolicyTest' --tests 'io.codecks.HidSessionServicePolicyTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
./gradlew :app:lintDebug :app:assembleDebug
```

Result:

- `git diff --check` passed.
- Unit + lint/build passed after setting `ANDROID_HOME`/`ANDROID_SDK_ROOT`.

Maestro plan:

- `tasks/maestro/v4-cross-vertical.yaml`
- `tasks/maestro/run-v4-cross-vertical-maestro.sh`
- Secondary display smoke sizes: `1280x720`, `1920x1080`
- App package: `app.codecks.debug`
- No physical `app.codecks` package targeted.

Notes:

- No code changes to 15-second HID keepalive behavior were made until runtime profiling evidence is captured.
- Back/keyboard/mouse/display reconnect restore steps are added to the Maestro harness entry flow and must be executed where emulator support is available.
- Execution blocker: `app.codecks.debug` was not installed in this environment. Install was skipped per instruction.
