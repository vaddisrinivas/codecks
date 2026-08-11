# Codecks support codes

Codecks support bundles contain categories and stable codes, never endpoint names, credentials, fingerprints, clipboard text, prompts, responses, raw commands, output, or device identifiers.

## Bluetooth input

| Code | Meaning | Repair |
|---|---|---|
| `CX-HID-PERM` | Bluetooth permission is missing | Open Codecks app permissions and allow Nearby devices. |
| `CX-HID-BT-OFF` | Bluetooth is unavailable | Turn Bluetooth on, return to Codecks, then retry. |
| `CX-HID-NO-MAC` | No Mac is selected | Pair or choose the Mac in Connection settings. |
| `CX-HID-FAIL` | Bluetooth input registration failed | Restart Bluetooth input. If it repeats, export a redacted support bundle. |
| `CX-HID-RETRY` | Codecks is reconnecting | Keep Codecks in the foreground or use Retry now. |
| `CX-HID-STOP` | Bluetooth input is stopped | Start Bluetooth input. |
| `CX-HID-CONNECT` | Bluetooth input is connecting | Wait for the current check to finish. |
| `CX-HID-READY` | Bluetooth input is ready without a live link | Use Retry now. |
| `CX-HID-OK` | Bluetooth input is connected | No repair is needed. |

## Mac controls

| Code | Meaning | Repair |
|---|---|---|
| `CX-SSH-SETUP` | Mac controls are not configured | Open Mac setup and complete the guided checks. |
| `CX-SSH-NO-PIN` | Mac identity is not confirmed | Verify the shown identity before saving credentials. |
| `CX-SSH-NO-KEY` | The control key is missing | Install the Codecks control key. |
| `CX-SSH-CHECK` | Mac controls are being checked | Wait for the current check to finish. |
| `CX-SSH-OK` | Mac controls are ready | No repair is needed. |
| `CX-SSH-SLEEP` | The Mac is offline or asleep | Wake the Mac, verify Wi-Fi and Remote Login, then retry. |
| `CX-SSH-AUTH` | Authentication failed | Re-enter credentials or reinstall the Codecks control key. |
| `CX-SSH-HOSTKEY` | The saved Mac identity changed | Stop. Confirm it is the same Mac before resetting trust. |
| `CX-SSH-TOOL` | A required Mac tool is missing | Install the tool named by the related feature, then test again. |
| `CX-SSH-FAIL` | Mac controls need attention | Retry once, then export a redacted support bundle. |
| `CX-SSH-RETRY` | Codecks is waiting before reconnecting | Use Retry now or wait for the bounded retry. |

## Mac helper

| Code | Meaning | Repair |
|---|---|---|
| `CX-HLP-SETUP` | Helper pairing is missing | Import pairing from the Codecks Mac helper. |
| `CX-HLP-IDLE` | The paired helper is disconnected | Open the helper and reconnect. |
| `CX-HLP-NO-ENDPOINT` | The helper endpoint is unavailable | Open Codecks helper on the Mac. |
| `CX-HLP-CONNECT` | The helper is connecting | Wait for authentication to finish. |
| `CX-HLP-OK` | The helper is connected | No repair is needed. |
| `CX-HLP-AUTH` | Helper authentication failed | Pair the helper again. |
| `CX-HLP-IDENTITY` | The pinned helper identity changed | Stop and verify the Mac helper identity before reconnecting. |
| `CX-HLP-RETRY` | The helper disconnected | Open the helper on the Mac, then retry. |
| `CX-HLP-FAIL` | The helper needs attention | Retry once, then export a redacted support bundle. |

## Clipboard

| Code | Meaning | Repair |
|---|---|---|
| `CX-CLP-SETUP` | Mac setup is incomplete | Finish Mac setup before transferring clipboard text. |
| `CX-CLP-CHECK` | Clipboard state is being checked | Wait for the current check to finish. |
| `CX-CLP-SLEEP` | The Mac clipboard is unreachable | Wake the Mac, then retry. |
| `CX-CLP-CONFLICT` | Both clipboards changed | Choose which copy to keep. |
| `CX-CLP-FAIL` | The last transfer failed | Retry or review Mac setup. |
| `CX-CLP-OK` | Clipboard transfer is ready | No repair is needed. |

## Bundle safety

- Preview before generation.
- The archive is created only after explicit confirmation.
- Codecks opens Android's share picker; it does not upload the archive.
- Operation receipts omit support codes until a typed M06 diagnosis is captured at the operation boundary.
- Pending archives expire after 24 hours and can be explicitly deleted.
