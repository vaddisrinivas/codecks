# WT07 Reactive provider suite

- Worktree: `reactive/providers`
- Branch: `codex/reactive-providers`
- Scope:
  - media-state provider
  - active-window provider
  - receipt-owned undo provider
  - default Android Reactive engine wiring for media/window providers

## Focused validation

```text
./gradlew :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.providers.*' --tests 'io.codecks.domain.reactive.ReactiveEngineTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: passed.

Notes:

- Dummy signing properties only; no APK signed or installed.
- Undo provider emits only receipt-owned, unexpired undo actions. It does not guess undo.
