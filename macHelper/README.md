# Codecks Mac Helper

The package contains two separate, compatible entry points:

- `codecks-mac-helper`: the existing command-line and launchd service.
- `Codecks Mac Helper`: a menu-bar setup and status app.

## Local checks

```sh
swift test --package-path macHelper
macHelper/scripts/build-local-app.sh
```

The build script creates an unsigned local app at
`macHelper/.build/local-app/Codecks Mac Helper.app`. It does not install,
sign, notarize, start, or replace the launchd service.

Existing service installation remains explicit:

```sh
macHelper/scripts/install-launchd.sh
```

The current service config contains a legacy global shared secret. The UI
detects that boundary but never reads it into diagnostics or offers new-phone
pairing. Do not distribute `helper.json`. Safer per-phone pairing requires a
coordinated protocol change in both the Mac helper and Android app.
