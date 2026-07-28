# Reactive Wave 1C integration checkpoint

- Worktree: `reactive/integration`
- Branch: `codex/reactive-platform-integration`
- Checkpoint date: 2026-07-27
- Scope:
  - WT07 Reactive provider suite
  - WT11 pure DeskDock scoring core

## Merged slices

- WT07: `7d5880a Add Reactive state control providers`
- WT11: `8c69d7f Add Reactive DeskDock scoring core`

## Aggregate validation

```text
./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.*' --tests 'io.codecks.domain.reactive.providers.*' --tests 'io.codecks.domain.reactive.deskdock.*' --tests 'io.codecks.core.reactive.*' --tests 'io.codecks.platform.helper.*' --tests 'io.codecks.data.reactive.LiveMacStateRepositoryTest' --tests 'io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModelTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: passed. Dummy signing properties only; no APK signed or installed.

```text
python3 tools/verify_protocol_fixtures.py
python3 tools/secret_surface_check.py
scripts/verify_release_no_shrink.sh
git diff --check
```

Result: passed.

```text
cd macHelper
swift test
```

Result: passed, 5 tests, 0 failures.

## Not proven by this checkpoint

- Physical phone install.
- Real Mac HID/SSH proof.
- DeskDock Android signal collection or launch policy.
- Full iOS app.
- Shortcuts, Spotlight/SFTP, brightness, or Accessibility integrations.
