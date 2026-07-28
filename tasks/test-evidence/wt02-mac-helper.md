# WT02 Mac Helper Evidence

Status: focused green

Branch: `codex/reactive-mac-helper`

Scope changed:

- `macHelper/Package.swift`
- `macHelper/Sources/CodecksMacHelper/**`
- `macHelper/Sources/CodecksMacHelperCLI/main.swift`
- `macHelper/Tests/CodecksMacHelperTests/ReactiveMacHelperTests.swift`
- `tasks/test-evidence/wt02-mac-helper.md`

Implemented:

- Native Swift Package scaffold with library and executable targets.
- Length-prefixed JSON frame codec compatible with `ReactiveFrameCodec`.
- HMAC-SHA256 helper, transcript helpers, and constant-time comparison.
- Helper identity pin and pairing record persistence boundary under Application Support.
- In-memory pairing store for tests.
- Session coordinator covering hello, challenge, proof, auth result, ping, capabilities, basic state, and denied execute stubs.
- Session sequence guard, duplicate request rejection, deadline validation, proof mismatch rejection, revoked identity rejection.
- Execute routing is allowlist-only; unsupported actions are denied. No arbitrary shell execution.

Validation:

- `cd macHelper && swift test` -> `5 tests, 0 failures`
- `git diff --check` -> clean

Notes:

- This is a scaffold, not a production daemon. Bonjour, launchd, signing/notarization, UI, permission probes, and real provider execution remain future WT work.
