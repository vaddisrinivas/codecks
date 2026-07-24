# Physical signed-release SSH gate

This opt-in gate proves Mac SSH through the exact signed, unshrunk production APK on an explicitly approved physical phone.

## Safety

- Requires an explicit physical `ADB_SERIAL`; emulator serials are rejected.
- Requires `app.codecks` to already be installed.
- Verifies the candidate and installed signing certificates match before updating.
- Uses only `adb install -r`; it never uninstalls or clears `app.codecks`.
- Restores Codecks to the foreground after the run.
- Does not enter passwords or approve host fingerprints. The phone must already have an authorized SSH profile.

## Run

```bash
./gradlew --no-daemon :app:assembleRelease :app:assembleReleaseAndroidTest

ADB_SERIAL=<approved-physical-serial> \
  ./scripts/physical_release_ssh_smoke.sh \
  app/build/outputs/apk/release/app-release.apk \
  app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
```

The test opens one enabled bundled Mac action and requires a successful result. It also rejects JSch class-loading, access, and connection exceptions in device logs.

## Required result

```text
Physical signed-release SSH smoke passed without uninstalling or clearing app.codecks.
```

The instrumentation and logcat evidence are written beside the release APK.
