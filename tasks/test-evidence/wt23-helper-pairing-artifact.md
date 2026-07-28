# WT23 helper pairing artifact/import

## Scope

- Added Mac helper `print-pairing-json [host]` command.
- Pairing JSON includes:
  - `macId`
  - `displayName`
  - `helperId`
  - `publicKeyFingerprint`
  - `sharedSecretHex`
  - `port`
  - optional `host`
- Added Android `ReactiveHelperPairingPayload` parser/validator.
- Added `ReactiveHelperPairingImporter` to persist pinned identity + encrypted shared secret.

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

## Remaining

- No Android UI/QR scanner/deep link surface yet.
- Pairing JSON contains the shared secret and should only be shown by explicit user action.
