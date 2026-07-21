# Codecks local-only release ledger

Updated: July 21, 2026

| Contract | Public-release value |
| --- | --- |
| Product | Codecks |
| Application ID | `app.codecks` |
| Version | `0.1.6` (`versionCode` 7) |
| Minimum Android | 9 / API 28 |
| Target Android | API 37 |
| Default mode | Local-only; no account, billing, hosted backend, database, analytics, or cloud sync |
| Core surfaces | Deck, Trackpad, Keyboard/Text, Clipboard bridge, Automations, Settings, Deck editor |
| Optional core tool | AI-assisted drafting through a user-selected provider |
| Preview artifact | `codecks-v2-cockpit-preview-v0.1.6.apk` (`io.codecks.app.debug`, `0.1.6-preview`, debug-signed preview only) |
| Disabled incubators | Context Deck, Widget, Activity, Devices, Premium, Paywall, Advanced, Appearance, Labs |

## Release invariants

- `BuildConfig.LOCAL_ONLY_V1` defaults to `true`.
- Optional context and Quick Settings manifest components default disabled.
- Android backup and cleartext traffic stay disabled.
- Release builds use a private signing key, R8 minification, and resource shrinking.
- Debug builds use the separate `app.codecks.debug` application ID.
- No signing key, credential, workstation path, device serial, or private QA artifact enters Git.
- AI generation is user initiated, shows provider/model/context, and produces reviewable drafts.
- Generated automations remain disabled until the current revision passes a test.
- The v2 Codex Cockpit preview is a separate debug artifact; it is not the signed root release APK and must keep local bridge actions guarded.

## Public Android components

| Component | Exposure | Protection |
| --- | --- | --- |
| `MainActivity` | Exported | Launcher only; internal destination extras require an app-private token |
| `HidSessionService` | Not exported | Foreground connected-device service; starts only after Bluetooth permission |
| `DeckWidgetProvider` | Exported when optional feature is compiled on | Receives protected AppWidget system broadcasts; off by default |
| `DeckTileService` | Exported when feature is compiled on | `android.permission.BIND_QUICK_SETTINGS_TILE`; off by default |
| `DeckBridgeNotificationListenerService` | Exported when optional feature is compiled on | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`; off by default |
| `WidgetLaunchActivity` | Not exported | Explicit immutable `PendingIntent` only; off by default |

Any change to these values requires a privacy-ledger update and release test.
