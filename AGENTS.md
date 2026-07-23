# Codecks agent rules

## Protect the installed release app

- Treat the production package `app.codecks` and all of its on-device data as protected.
- Never run `adb uninstall app.codecks`, `pm uninstall app.codecks`, `pm clear app.codecks`, or any equivalent removal/data-clear operation for testing.
- Never replace `app.codecks` with an APK signed by a different certificate.
- Test debug and instrumentation APKs only with their separate debug/test package IDs or Gradle-managed emulators.
- Before an in-place release update, verify the candidate signing certificate matches the installed `app.codecks` certificate. If it does not match, stop.
- Uninstalling, clearing data, downgrading, or accepting data loss requires the user's explicit approval in the current conversation after a specific warning. A general request to test or install is not approval.
- Do not target a physical phone with instrumentation unless the user explicitly requests it. Prefer Gradle-managed emulators.
