# Codecks Privacy Policy

Last updated: July 17, 2026

Codecks is a local-only Android application. This release does not create a Codecks account and does not use a Codecks backend, public database, analytics service, advertising SDK, or cloud-sync service.

## Data stored on the device

Codecks may store:

- Mac host, port, username presence, SSH key material, and pinned host key.
- Deck layouts, custom buttons, automations, test results, and run history.
- Bluetooth HID target and control preferences.
- Theme, feature, clipboard, notification-display, and AI-provider settings.
- AI drafts that the user chooses to save.

SSH private-key material and AI-provider credentials are encrypted using Android Keystore-backed encryption. Android backup is disabled for the application.

## Data sent from the device

- **Mac control:** commands selected by the user are sent to the configured Mac over SSH on the chosen network.
- **Optional AI:** when the user explicitly requests AI generation or testing, the prompt and Codecks action schema are sent directly to the selected third-party provider. The provider's terms and privacy policy apply.
- **Bluetooth HID:** pointer and keyboard reports are sent to the paired host selected by the user.

Codecks does not send deck data, Mac details, clipboard contents, notifications, API keys, or usage analytics to a Codecks-operated server.

## Optional and disabled features

Context Deck, phone-notification access, widgets, clipboard sync, account, billing, and advanced diagnostics are disabled in the default public build. Enabling an incubator build may change what local data is processed; no such feature may upload data without an explicit preview and user action.

## Deletion

Codecks does not create a server account. Delete all local application data using **Android Settings → Apps → Codecks → Storage → Clear storage**, or uninstall Codecks. Delete exported backup files separately from the location where they were saved.

## Diagnostics

Release builds do not expose debug-bundle sharing. Debug builds redact secrets, email addresses, IP addresses, URLs, and local filesystem paths before creating a user-initiated diagnostic bundle.

## Questions

Use the repository's GitHub Discussions for general privacy questions. Report sensitive security or privacy issues through a private [GitHub security advisory](https://github.com/vaddisrinivas/codecks/security/advisories/new).
