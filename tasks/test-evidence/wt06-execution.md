WT06 execution, receipts, and undo

Status: FOCUSED_GREEN

Implemented:
- Reactive invocation IDs and idempotency keys.
- Reactive receipts now carry operationId/idempotencyKey and mirror typed protocol receipt status/error.
- DefaultReactiveActionExecutor records idempotent successful HID/catalog executions.
- Same idempotency key and same action signature returns the stored receipt without re-executing.
- Same idempotency key with a different control/action signature is denied.
- Undo API scaffold returns typed unsupported/expired outcomes from the receipt store.

Validation:
- PASS: ./gradlew :app:testReleaseUnitTest --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
