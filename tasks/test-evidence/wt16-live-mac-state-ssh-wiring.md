# WT16 live Mac state helper/SSH wiring

## Scope

- Wired release UI construction to create `LiveMacStateRepository` with SSH fallback source.
- Added `ConnectionRepositorySshMacStateSource`.
- Uses one fixed bundled SSH command to ask macOS System Events for the frontmost app.
- Parses bounded two-line output: bundle id then app name.
- Rejects unsafe bundle ids and unsafe app names.
- Publishes SSH-sourced `ReactiveHelperBasicState` with front-app, execute, Spotlight, and SFTP capabilities only.

## Verification

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.data.reactive.LiveMacStateRepositoryTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL`.

## Device safety

- No phone used.
- No `app.codecks` uninstall/data clear/replacement.
- Release no-shrink settings untouched.
