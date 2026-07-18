# AppFunctions support (Android 16+)

## Scope

- Issue #10 adds `io.codecks.app.CodecksAppFunctionService` behind an
  Android 16+/feature-flag boundary.
- Exposed AppFunctions:
  - `listCommandDecks`
  - `previewDeckCommand`
  - `queueDeckCommand`
  - `runApprovedDeckCommand`

## ADB smoke test commands

Use an API 36+ device/emulator and verify the service is available:

```bash
adb shell cmd app_function help
```

List all registered app functions:

```bash
adb shell cmd app_function list-app-functions
```

Filter registrations for the package (replace `<package>`):

```bash
adb shell cmd app_function list-app-functions | grep --after-context 10 <package>
```

Invoke the read-only preview flow:

```bash
adb shell cmd app_function execute-app-function \
  --package <package> \
  --function previewDeckCommand \
  --parameters '{"deckId":"deck-main","buttonId":"button-lock"}'
```

Queue then run an approval-gated command:

```bash
QUEUE_ID=<queue-id-from-preview>
QUEUE_TOKEN=<approval-token-from-queue>

adb shell cmd app_function execute-app-function \
  --package <package> \
  --function queueDeckCommand \
  --parameters '{"deckId":"deck-main","buttonId":"button-lock"}'

adb shell cmd app_function execute-app-function \
  --package <package> \
  --function runApprovedDeckCommand \
  --parameters '{"queueId":"'$QUEUE_ID'","approvalToken":"'$QUEUE_TOKEN'"}'
```

Disable/enable a function for local testing:

```bash
adb shell cmd app_function set-enabled \
  --package <package> \
  --function queueDeckCommand \
  --state disable
```

## Notes

- The service is registered in manifest only when `codecksAppFunctionsEnabled` is
  true (Android API 36+ resource overlay).
- Current implementation executes only `ExecutorKind.SSH_MAC` actions from deck items.
  Local-only actions (trackpad/keyboard entries) are reported but intentionally not
  runnable through this API yet.
