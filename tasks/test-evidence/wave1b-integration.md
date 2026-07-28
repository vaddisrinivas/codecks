# Reactive Wave 1B integration checkpoint

- Worktree: `reactive/integration`
- Branch: `codex/reactive-platform-integration`
- Checkpoint date: 2026-07-27
- Scope:
  - WT05 Reactive engine policy/ranking
  - WT06 Reactive execution receipts/idempotency/timeout/undo
  - WT08 Reactive profiles

## Merged slices

- WT05: `06c0d8e Add Reactive engine policy and ranking`
- WT06: `4e945ba Validate reactive execution receipts`
- WT08: `bb43127 Add reactive profile resolver`

## Aggregate validation

```text
python3 tools/verify_protocol_fixtures.py
```

Result: passed, `protocol fixtures ok`.

```text
./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.*' --tests 'io.codecks.domain.reactive.providers.*' --tests 'io.codecks.core.reactive.*' --tests 'io.codecks.platform.helper.*' --tests 'io.codecks.data.reactive.LiveMacStateRepositoryTest' --tests 'io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModelTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: passed. Dummy signing properties only; no APK signed or installed.

```text
python3 tools/secret_surface_check.py
scripts/verify_release_no_shrink.sh
git diff --check
```

Result: passed. Release no-shrink invariant verified at `app/build.gradle.kts` release lines for `isMinifyEnabled = false` and `isShrinkResources = false`.

```text
cd macHelper
swift test
```

Result: passed, 5 tests, 0 failures.

## Not proven by this checkpoint

- Physical phone install.
- Real Mac HID/SSH proof.
- Full iOS app.
- DeskDock, Shortcuts, SFTP, brightness, or Accessibility integrations.
