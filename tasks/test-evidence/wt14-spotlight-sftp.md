# WT14 Spotlight/SFTP scaffold evidence

## Scope

- Added typed Spotlight preview and SFTP transfer-request actions.
- Added bounded request models and validators for Spotlight query and SFTP paths.
- Added provider gating on live Helper/SSH source plus available capability.
- Added receipt metadata that records operation kind, provenance, limits, root ids, and fingerprints only.
- No real Spotlight execution, shell command construction, SFTP transfer, APK build, phone install, or physical-device action.

## Validation

```bash
ANDROID_HOME=<local Android SDK> ./gradlew :shared:jvmTest :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.SpotlightSftpModelsTest' --tests 'io.codecks.domain.reactive.providers.SpotlightSftpReactiveControlProviderTest' --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest.spotlightPreviewRecordsReceiptWithoutRawQueryOrHidSideEffect' --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest.sftpTransferRequestReceiptStoresRootIdsAndFingerprintsOnly' --tests 'io.codecks.data.reactive.LiveMacStateRepositoryTest.helperTransferAndSpotlightCapabilitiesMapToDedicatedReactiveCapabilities' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: pass.

```bash
ANDROID_HOME=<local Android SDK> ./gradlew :shared:iosSimulatorArm64Test -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: pass.

```bash
git diff --check
python3 tools/secret_surface_check.py
```

Result: pass.

## Coverage

- hostile Spotlight query rejection: traversal, path-looking input, shell metacharacters, control characters, overlength, over-cap result count.
- hostile SFTP path rejection: outside allowlisted root and traversal.
- provider hiding when source is local-cache or capability is not available.
- provider caps Spotlight controls and rejects stale/mismatched provenance.
- executor receipts do not include raw query, local path, or remote path.
- `transfer.sftp` maps to `CodecksCapability.SftpTransfer`; `spotlight.search` maps to `CodecksCapability.SpotlightSearch`.

## Residual gaps

- No native Mac helper Spotlight implementation.
- No real SFTP transport implementation.
- No UI picker/import flow for user-approved file transfer.
