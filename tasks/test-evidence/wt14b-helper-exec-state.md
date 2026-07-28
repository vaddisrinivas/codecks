# WT14B Reactive helper execution seam

Branch: `codex/reactive-helper-exec-state`

Scope:

- Added `ReactiveHelperActionClient` seam.
- Added `ReactiveHelperClientActionClient` adapter over authenticated `ReactiveHelperClient`.
- Mapped `ReactiveAction.Helper` to typed `ReactiveHelperRequest.Execute`.
- Mapped helper response statuses to `ReactiveActionResult`.
- Wired `DefaultReactiveActionExecutor` to injected helper seam.
- Default helper remains unavailable without a connected authenticated helper session.
- Preserved idempotency replay and success-only receipts.

Safety:

- No raw shell execution.
- No real network side effect in executor tests.
- Failed/denied/unsupported/expired helper outcomes do not create receipts.
- Success metadata stores helper receipt id/result code, not raw output.

Verified:

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL in 4s`

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.platform.helper.ReactiveHelperClientTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL in 1s`

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

- No real helper session dependency injection from app UI/service.
- No native helper implementation of concrete actions.
- No physical Mac execution proof.
