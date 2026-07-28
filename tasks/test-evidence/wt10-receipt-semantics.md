# WT10 Reactive receipt semantics

- Worktree: `reactive/receipt-semantics`
- Branch: `codex/reactive-receipt-semantics`
- Scope:
  - align executor with plan contract: receipts are created only for successful execution
  - denied, failed, unsupported, confirmation, review, timeout, stale-state, and idempotency-conflict outcomes return no receipt
  - receipt-backed undo remains available only from successful reversible receipts

## Focused validation

```text
./gradlew :app:testReleaseUnitTest --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' --tests 'io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModelTest' --tests 'io.codecks.domain.reactive.providers.ReactiveStateControlProvidersTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: passed.

Notes:

- Dummy signing properties only; no APK signed or installed.
