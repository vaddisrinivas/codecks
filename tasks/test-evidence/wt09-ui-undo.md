# WT09 Reactive UI undo wiring

- Worktree: `reactive/ui`
- Branch: `codex/reactive-ui`
- Scope:
  - share one in-memory Reactive receipt store between Android executor and default Reactive engine
  - include receipt-owned undo provider in the default engine
  - recompute controls after execution so a newly available undo control can appear

## Focused validation

```text
./gradlew :app:testReleaseUnitTest --tests 'io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModelTest' --tests 'io.codecks.domain.reactive.providers.ReactiveStateControlProvidersTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: passed.

Notes:

- Dummy signing properties only; no APK signed or installed.
- Undo remains receipt-owned; no guessed undo was added.
