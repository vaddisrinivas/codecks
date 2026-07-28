# WT14C Mac capability adapters

Branch: `codex/reactive-mac-capability-adapters`

Scope:

- Added typed `AppleShortcutsCliRequest` argv builder.
- Added typed monitor brightness request model.
- Added typed Accessibility discovery request model.
- Added dedicated `CodecksCapability.MonitorBrightness`.
- Added dedicated `CodecksCapability.AccessibilityDiscovery`.
- Added brightness and Accessibility Reactive control providers.
- Wired Spotlight/SFTP, brightness, and Accessibility providers into default Reactive engine with empty request sources by default.

Safety:

- No shell string construction.
- Shortcuts runner exposes argv only and uses `--input-path`, not inline command text.
- Brightness and Accessibility controls are `ReactiveAction.Helper` requests only.
- Providers require Helper provenance and matching request provenance.
- No real SFTP transfer, brightness mutation, Shortcuts run, or Accessibility scan is performed.

Verified:

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.domain.reactive.MacCapabilityAdapterModelsTest' \
  --tests 'io.codecks.domain.reactive.providers.MacCapabilityReactiveControlProvidersTest' \
  --tests 'io.codecks.data.reactive.LiveMacStateRepositoryTest' \
  --tests 'io.codecks.domain.reactive.SpotlightSftpModelsTest' \
  --tests 'io.codecks.domain.reactive.providers.SpotlightSftpReactiveControlProviderTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL in 35s`

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

- No real helper-side Shortcuts CLI runner.
- No real SFTP transfer.
- No real monitor brightness mutation.
- No Accessibility tree scan.
