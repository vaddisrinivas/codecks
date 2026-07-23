# Codecks local-only release ledger

Updated: July 23, 2026

| Contract | Public-release value |
| --- | --- |
| Product | Codecks |
| Application ID | `app.codecks` |
| Version | `0.1.18` (`versionCode` 19) |
| Minimum Android | 9 / API 28 |
| Target Android | API 37 |
| Default mode | Local-only; no account, billing, hosted backend, database, analytics, or cloud sync |
| Core surfaces | Deck, Trackpad, Keyboard, Clipboard bridge, Rules, Settings, Deck editor |
| Optional core tool | AI-assisted drafting through a user-selected provider |
| Disabled incubators | Smart suggestions, Smart Deck, Smart Keyboard, Smart Clipboard, Smart Rules, Smart Settings, Smart Trackpad, Smart OCR, Widget, Activity, Devices, Premium, Paywall, Advanced, Appearance, Labs |

## Release invariants

- `BuildConfig.LOCAL_ONLY_V1` defaults to `true`.
- Optional context and Quick Settings manifest components default disabled.
- Android backup and cleartext traffic stay disabled.
- Release builds use a private signing key, R8 minification, and resource shrinking.
- Debug builds use the separate `app.codecks.debug` application ID.
- No signing key, credential, workstation path, device serial, or private QA artifact enters Git.
- AI generation is user initiated, shows provider/model/context, and produces reviewable drafts.
- Generated buttons and Rules remain disabled/unverified until the current revision passes a test.
- Codecks ships as one signed APK; debug or prototype builds must not be attached to public releases.

## Public Android components

| Component | Exposure | Protection |
| --- | --- | --- |
| `MainActivity` | Exported | Launcher only; internal destination extras require an app-private token |
| `HidSessionService` | Not exported | Foreground connected-device service; starts only after Bluetooth permission |
| `CodecksNotificationListenerService` | Exported when optional feature is compiled on | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`; off by default |

Any change to these values requires a privacy-ledger update and release test.
