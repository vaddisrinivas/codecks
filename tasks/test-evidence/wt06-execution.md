# WT06 Reactive execution receipts / undo

- Worktree: `reactive/execution`
- Branch: `codex/reactive-execution`
- Scope:
  - idempotent action invocation model consumption in Android executor
  - replay returns stored receipt without repeated side effects
  - idempotency-key conflict creates denied audit receipt
  - timeout/expired controls produce receipts without side effects
  - HID receipt undo scaffold for reversible browser back/forward
  - composite controls derive child idempotency keys

## Focused validation

```text
./gradlew :app:testReleaseUnitTest --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: passed.
