# GitHub release install and support

Codecks public beta is distributed only through the repository's
[GitHub Releases](https://github.com/vaddisrinivas/codecks/releases). The current
public release is `v0.1.37`. This repository does not authorize Google Play
publication; external Play Console state is `NOT_VERIFIED` here.

## Requirements

- Android 9 / API 28 or newer.
- macOS with Remote Login for Deck, Rules, clipboard, and SSH-backed actions.
- A compatible Bluetooth HID host for Trackpad and short keyboard input.

## Fresh install

1. Open the exact `v0.1.37` release page. Download only
   `codecks-release.apk` and `SHA256SUMS.txt`.
2. Verify the APK SHA-256 is
   `8c8eca1b3e4b0f56a2128185c42a062687011e68a9d3fd16fe24851616baa9f2`.
   On macOS: `shasum -a 256 -c SHA256SUMS.txt`.
3. The expected signing-certificate SHA-256 is
   `07a642e758f394b6aeaecfe35c64ca84d891ca4e6de4b6cc010702c0e52e2df6`.
   Advanced users can verify it with Android SDK `apksigner verify --verbose --print-certs codecks-release.apk`.
4. Allow installs from the browser or file manager only for this install, then
   open the APK. Revoke that permission afterward if desired.

Stop if the checksum, filename, package, or certificate differs. Do not install
an APK from an issue, discussion, mirror, or chat attachment.

## Safe update

Use Codecks Settings' manual update check or the Releases page. Download and
verify the new APK before opening it. Android must offer an in-place update that
preserves data. A certificate mismatch, downgrade warning, unexpected package,
or request to uninstall first is a hard stop. Export the local Deck/Rules backup
before a major update; it intentionally excludes secrets.

Never uninstall or clear Codecks to force an update. If Android rejects a
verified update, keep the installed version and file a redacted bug report.

## Network and partial-download recovery

Delete `.part`, `.crdownload`, zero-byte, duplicate-renamed, or checksum-failing
files. Retry from the exact release page on a stable connection. Never resume an
unverified partial APK or bypass the checksum because GitHub was unavailable.
The app remains usable offline while release metadata is unavailable; update
check failure must not claim that the installed version is current.

## Rollback

Codecks does not support installing an older APK over `app.codecks`. A bad
release is withdrawn and replaced by a greater-version forward fix. Keep the
current installed app and data until that verified fix exists. Do not uninstall,
clear storage, or accept a downgrade as routine recovery.

## Support

- Normal bugs: use the GitHub bug template and attach only the app's redacted
  support export.
- Feature requests: use the feature template.
- Vulnerabilities or private-data leaks: use a private GitHub Security Advisory,
  never a public issue.
- Never post credentials, SSH keys, hostnames, IP addresses, clipboard text,
  notification content, raw commands, device serials, or private screenshots.

Known limitations and support codes are in the
[feature guide](../product/FEATURE_GUIDE.md) and
[troubleshooting guide](../support/TROUBLESHOOTING.md).
