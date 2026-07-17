# Security Policy

## Supported versions

Only the latest GitHub release receives security fixes during the public-beta period.

## Report a vulnerability

Do not open a public issue for a vulnerability, exposed credential, or private-data leak. Send a private report through [GitHub Security Advisories](https://github.com/vaddisrinivas/codecks/security/advisories/new).

Include the affected version, Android/macOS versions, reproduction steps, and impact. Do not include real API keys, passwords, SSH private keys, notification content, or other personal data.

## Security boundaries

- Release builds are signed; checksums are attached to each GitHub release.
- Android backup and cleartext network traffic are disabled.
- AI credentials and SSH private-key material use Android Keystore-backed encryption.
- Custom and AI-generated commands pass a local safety policy; generated automations require review and a successful current test before enablement.
- Internal navigation intents use an app-private token. Optional externally visible Android components are disabled by default or protected by Android system permissions.

Codecks controls another computer. No command filter can make arbitrary user-authored shell commands risk-free. Use a non-admin Mac account and review commands before execution.
