# WT18 SFTP transfer execution

## Scope

- Added `ReactiveSftpTransferClient` seam.
- Wired `DefaultReactiveActionExecutor` to execute `ReactiveAction.SftpTransferRequest` instead of only recording the request.
- Added `ConnectionRepositoryReactiveSftpTransferClient`.
- Added real `DefaultConnectionRepository.runSftpTransferOnTarget` using JSch `ChannelSftp`.
- Supports:
  - Mac → phone via `channel.get(remotePath, localPath)`.
  - Phone → Mac via `channel.put(localPath, remotePath)`.
- Enforces existing allowlisted `SafeSftpTransferRequest`.
- Enforces `maxBytes` before transfer.
- Receipt metadata stores root IDs and path fingerprints only; no raw local/remote paths.

## Verification

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL`.

## Not verified

- No live Mac/phone SFTP transfer was run.
- No physical phone used.
