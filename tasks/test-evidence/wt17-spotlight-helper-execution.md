# WT17 Spotlight helper execution

## Scope

- Changed Spotlight reactive controls from local receipt-only preview to helper execution.
- `SpotlightSftpReactiveControlProvider` now emits `ReactiveAction.Helper("spotlight.search")`.
- Query and max-result cap are sent as typed helper arguments.
- Mac helper already handles `spotlight.search` through bounded `/usr/bin/mdfind` argv execution.

## Verification

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.domain.reactive.providers.SpotlightSftpReactiveControlProviderTest' \
  --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL`.

## Remaining

- SFTP still has safe request/receipt scaffolding only; real file transfer executor remains.
