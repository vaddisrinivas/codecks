# WT21 monitor brightness handler

## Scope

- Implemented `monitor_brightness.set` execution for the BetterDisplay CLI adapter.
- Uses fixed executable candidates:
  - `/opt/homebrew/bin/betterdisplaycli`
  - `/usr/local/bin/betterdisplaycli`
- Uses argv execution only; no shell command string.
- Sends:
  - `set`
  - `-namelike=<displayId>`
  - `-brightness=<percent>%`
- Timeout is bounded and retryable.
- Nonzero exit is a failed receipt, not success.

## Verification

```text
cd macHelper && swift test
```

Result: 24 tests passed, 0 failures.

## Not verified

- BetterDisplay CLI was not installed/run on this Mac in this gate.
- No real monitor brightness was changed.
