# WT24 helper pairing deep link

## Scope

- Added explicit Android deep-link import:
  - `codecks://helper-pair?payload=<urlencoded-json>`
- Import path:
  - parses payload only for `codecks://helper-pair`.
  - imports pinned helper identity and encrypted shared secret.
  - navigates to Settings/pairing.
  - shows snackbar success/failure.
- Added `ReactiveHelperPairingStore` so importer depends on a minimal save-pairing boundary.

## Verification

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.data.reactive.helper.ReactiveHelperPairingImporterTest' \
  --tests 'io.codecks.platform.helper.ReactiveHelperSessionManagerTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL`.

```text
cd macHelper && swift test
```

Result: 25 tests passed, 0 failures.

## Not verified

- No actual Android intent was fired on device/emulator in this gate.
- No QR scanner UI yet.
