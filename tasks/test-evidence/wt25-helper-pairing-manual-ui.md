# WT25 helper pairing manual UI

## Scope

- Added Settings → Connect a Mac → Advanced Mac controls manual helper pairing import.
- User can paste JSON from:
  - `codecks-mac-helper print-pairing-json`
- Import path:
  - validates payload.
  - saves pinned helper identity.
  - stores shared secret through Android secure storage.
  - shows snackbar success/failure.

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

## Not verified

- UI was not clicked in emulator/device in this gate.
- QR/camera pairing still not implemented.
