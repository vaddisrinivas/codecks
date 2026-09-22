# Codecks Mac Helper

The package contains two compatible entry points:

- `Codecks Mac Helper`: the menu-bar app and installed launchd service owner.
- `codecks-mac-helper`: the compatible diagnostic/development CLI.

## Local checks

```sh
swift test --package-path macHelper
macHelper/scripts/build-local-app.sh
```

The build script creates an unsigned local app at
`macHelper/.build/local-app/Codecks Mac Helper.app`. The bundle includes both
executables. It does not install, sign, notarize, or start the service.

Signing is an explicit operator step. Required values are loaded only at the
execution boundary; the repository script never sources or prints them:

```sh
~/.codex/skills/agent-env/scripts/run-with-agent-env.sh \
  macHelper/scripts/sign-local-app.sh
```

Missing signing inputs reports `SIGNING_NOT_RUN`. Success requires exact app
and team entitlements plus a signed-process app-private Keychain probe.

Existing service installation remains explicit:

```sh
macHelper/scripts/install-launchd.sh
```

The service config may contain a legacy global shared secret for known phones.
New public/manual imports reject it. CLI export requires
`print-pairing-json --unsafe-legacy` and prints a warning. Never distribute
`helper.json`.

New pairing uses a 120-second one-use offer, HKDF-SHA256 per-phone credential,
and matching-code confirmation on both screens. HMAC provides authentication
and integrity, not TCP confidentiality. Per-phone credentials use the signed
app's private, ThisDeviceOnly Keychain items; no access group is configured.
Unsigned local builds report pairing not ready; no plaintext fallback exists.
