# WT15B Android Reactive helper session manager

## Scope

- Added app-level `ReactiveHelperSessionManager`.
- Loads pinned helper identity from `ReactiveHelperIdentityStore`.
- Loads session secret bytes by alias through `ReactiveHelperSecretStore`.
- Connects through injectable `ReactiveHelperTransportFactory`.
- Authenticates with existing pinned/HMAC/replay-protected `ReactiveHelperClient`.
- Exposes `ReactiveHelperClientActionClient` only after successful authentication.
- Keeps helper actions unavailable for missing identity, missing secret, or identity mismatch.

## Verification

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.platform.helper.ReactiveHelperSessionManagerTest' \
  --tests 'io.codecks.platform.helper.ReactiveHelperClientTest' \
  --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL` with 38 actionable tasks.

```text
git diff --check
scripts/verify_release_no_shrink.sh
```

Result: clean diff check; release no-shrink invariant verified.

## Device safety

- No physical phone used.
- No uninstall, data clear, downgrade, or release app replacement.
- No raw secret values logged or persisted by the session manager.
