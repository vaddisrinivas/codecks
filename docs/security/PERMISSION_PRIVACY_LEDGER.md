# Permission and privacy ledger

Updated: July 17, 2026

| Permission or access | Purpose | Trigger | Data touched | Stored | Shared |
| --- | --- | --- | --- | --- | --- |
| `android.permission.INTERNET` | SSH Mac control and optional direct AI-provider requests | Connect/run action or explicit AI request | Mac command; AI prompt and action schema | Connection metadata and saved drafts only | Configured Mac or selected AI provider |
| `android.permission.BLUETOOTH_CONNECT` | Android Bluetooth HID keyboard/mouse profile | Trackpad/HID setup | Paired device name and address | Last target may be remembered locally | Paired host only |
| `android.permission.VIBRATE` | Interaction haptics | Deck or Trackpad interaction | None | No | No |
| `android.permission.WAKE_LOCK` | Let WorkManager finish an enabled automation trigger evaluation | User enables a scheduled automation | Trigger timing and local recipe ID | WorkManager state | Not shared |
| `android.permission.ACCESS_NETWORK_STATE` | Let WorkManager respect network state and report offline Mac/AI conditions | Background automation evaluation | Connected/disconnected state only | No | Not shared |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Restore enabled WorkManager schedules after restart | Device boot after user enabled an automation | Local schedule metadata | WorkManager state | Not shared |
| `android.permission.FOREGROUND_SERVICE` | WorkManager compatibility for bounded background work | Android promotes qualifying worker execution | Local worker status | No | Android system only |
| `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep the Bluetooth HID mouse/keyboard session registered while Codecks is active in background | User has granted Bluetooth permission and opens Codecks HID/Trackpad setup | Paired target connection state only | Last HID target address may be remembered locally | Paired host only |
| `android.permission.USE_BIOMETRIC` | Allow the selected Android credential provider to authenticate access to a saved Mac password | User taps Save password or Use saved password | Authentication result only; Codecks never receives biometric data | No biometric data | Selected system credential provider |
| `android.permission.USE_FINGERPRINT` | Compatibility permission for credential providers on older Android versions | Same as above | Authentication result only | No biometric data | Selected system credential provider |
| Notification listener special access | Incubator Context Deck input | No trigger in default release; Android Settings opt-in only in incubator builds | App identity and notification preview fields | Privacy preferences; live previews in memory | Not uploaded by the default build |

## Sensitive local data

| Data | Protection | Deletion |
| --- | --- | --- |
| SSH connection metadata and pinned host key | App-private DataStore; host-key verification | Reset connection or clear app storage |
| SSH private key | Android Keystore-backed AES-GCM; legacy plaintext migrates and is removed | Reset connection or clear app storage |
| AI provider API key | Android Keystore-backed AES-GCM; password-style UI; excluded from diagnostics and backup | Delete provider key or clear app storage |
| Clipboard text | Only read or written from explicit Clipboard screen actions or visible opt-in sync modes; history stores hashes, not contents | Turn Clipboard sync off, overwrite clipboard, or clear app storage |
| Decks, automations, AI drafts, and run history | App-private local storage | Delete item or clear app storage |
| User-exported backup | User-selected Storage Access Framework destination; credentials excluded | Delete exported file manually |

Release builds disable Android backup, cleartext networking, and debug-bundle sharing. The application contains no analytics or advertising SDK.
