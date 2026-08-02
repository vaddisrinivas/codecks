# Codecks v0.1.36 release notes

Date: August 2, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.36

## Summary

Codecks v0.1.36 is a focused Bluetooth HID reliability hotfix. It fixes an
Android "Codecks isn't responding" failure that could occur while the app
automatically reconnected to a previously selected Mac.

## Fixed

- Moved Bluetooth HID profile opening, app registration, connection, and input
  transport work away from Android's main UI thread.
- Serialized repository control events on one background lane so refresh,
  connect, disconnect, lifecycle maintenance, and reconnect cannot race.
- Serialized Android Bluetooth HID callbacks and transport calls through the
  existing HID control executor.
- Prevented duplicate connection requests while one request is pending.
- Added a five-second connection watchdog. A stalled Android Bluetooth service
  now produces an explicit repair-required timeout instead of freezing the UI.
- Added a typed `hid_transport_timeout` diagnostic with Bluetooth reset and HID
  registration recovery guidance.
- Kept cross-thread HID profile, connection, registration, and status state
  visible safely.
- Preserved emergency input invalidation and release ordering during
  disconnection and transport shutdown.

## Root cause

The v0.1.35 reconnect hardening consumed Bluetooth registration callbacks on
the main thread and immediately called Android's synchronous
`BluetoothHidDevice.connect()` Binder method. On the tested Samsung device that
Binder transaction stalled, preventing touch dispatch for more than ten
seconds and triggering an ANR. Two saved physical-device ANR traces showed the
same stack.

## Validation

- Android release unit suite: 817 tests, 0 failures, 3 intentional skips.
- Android debug lint and APK assembly passed.
- Gradle-managed Pixel 6 API 35 debug suite: 20 tests, 0 failures, 1
  intentional live-SSH skip.
- Physical Samsung debug test used the separate `app.codecks.debug` package;
  production data was not cleared or replaced during development.
- Physical manual HID connection to the paired Mac completed.
- Physical cold start with the selected Mac persisted exercised the exact
  automatic reconnect path that froze v0.1.35.
- The Trackpad accepted movement after manual and cold reconnect.
- The debug process remained responsive beyond Android's ten-second ANR
  threshold, with no new ANR, crash, or HID timeout.
- Production release minification and resource shrinking remain disabled.

## Assets

- `codecks-release.apk`: production-signed, unshrunk Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.35...v0.1.36
