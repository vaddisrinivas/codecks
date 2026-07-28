# WT15C Apple Shortcuts helper handler

Branch: `codex/reactive-shortcuts-handler`

Scope:

- Added Swift `AppleShortcutsActionHandler`.
- Added `ProcessCommandRunner` using executable URL + argv, not shell strings.
- Wired CLI coordinator with `"apple_shortcuts.run"` handler.
- Added fake-runner tests for argv construction, unsafe input denial, timeout, and nonzero exit.

Safety:

- No real Shortcuts execution in tests.
- Handler validates shortcut name and optional input path.
- Handler caps timeout to 30 seconds and output capture to 64 KiB.
- Uses `/usr/bin/shortcuts` executable with `["run", name, "--input-path", path]` args.

Verified:

```text
cd macHelper && swift test
```

Result: 10 tests passed, 0 failures.

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

- No physical Mac Shortcuts execution proof.
- No Android UI/session wiring to provide live shortcut catalog.
