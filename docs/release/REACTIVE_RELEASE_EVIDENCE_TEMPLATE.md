# Reactive Release Evidence Template

Release:
Commit:
Date:

## Evidence Labels

- `IMPLEMENTED`:
- `FOCUSED_GREEN`:
- `INTEGRATION_GREEN`:
- `EMULATOR_ACCEPTED`:
- `PHYSICAL_DEBUG_ACCEPTED`:
- `SIGNED_RELEASE_ACCEPTED`:
- `DEFERRED`:
- `BLOCKED`:

## Required Proof

- No-shrink proof: `scripts/verify_release_no_shrink.sh <release-apk>`
- Secret surface proof: `python3 tools/secret_surface_check.py`
- Protocol fixtures: `python3 tools/verify_protocol_fixtures.py`
- Shared JVM/iOS: `./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test`
- Android focused tests:
- Managed emulator:
- Signed release artifact path:
- Signed release SHA-256:
- Installed `app.codecks` signing certificate:
- Candidate APK signing certificate:
- Certificate comparison result:

## Physical Device Gate

- Protected package untouched: no uninstall, clear, downgrade, or differently signed replacement.
- Release build installed in place.
- HID pointer regression:
- SSH regression:
- Helper pair/reconnect/revoke:
- Reactive state/action/receipt/undo:
- Lockscreen guarded behavior:

## Deferred Or Blocked

List each unsupported feature honestly. Do not promote a lower evidence label.
