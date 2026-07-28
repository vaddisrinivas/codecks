# WT14A Reactive Mac helper service scaffold

Branch: `codex/reactive-mac-helper-real`

Scope:

- Added Android-compatible pin acknowledgement transcript on Mac side.
- Added Swift protocol-compatible execute request model.
- Added Swift helper action receipt model.
- Added handler-based execute routing in `ReactiveSessionCoordinator`.
- Added explicit undo/cancel denied stubs.
- Extended Swift receipt/error enums to match shared protocol statuses used by Android.

Safety:

- No `launchctl` install.
- No network listener opened.
- No raw shell execution.
- Concrete action execution is behind typed `ReactiveHelperActionHandler`.
- Unsupported execute requests are denied.

Verified:

```text
cd macHelper && swift test
```

Result: 7 tests passed, 0 failures.

```text
git diff --check
```

Result: passed.

```text
scripts/verify_release_no_shrink.sh
```

Result:

```text
verified app/build.gradle.kts:218:             isMinifyEnabled = false
verified app/build.gradle.kts:219:             isShrinkResources = false
Release no-shrink invariant verified.
```

Not done:

- No daemon installer.
- No Bonjour/listener transport.
- No real Shortcuts/SFTP/brightness/Accessibility handlers.
- No physical Mac helper run from Android.
