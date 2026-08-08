# Codecks v0.1.37 release notes

Date: August 8, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.37

## Summary

Codecks v0.1.37 improves Trackpad gesture reliability and introduces a
production-dark commercial foundation. The public app remains local-first:
sign-in, cloud sync, Play Billing, premium restrictions, ads, and commercial
SDK/network startup are all disabled.

## Trackpad fixes

- Added a rightward browser-back gesture consistent with revealing the previous
  page on macOS.
- Deferred tap-drag activation until movement or hold, preventing accidental
  button presses.
- Suppressed stale clicks during held second-touch and canceled drag sequences.
- Released held input exactly once when Trackpad input becomes disabled or an
  Android gesture is canceled.
- Fixed the delayed second-tap path that could emit three clicks instead of a
  double-click.

## Production-dark commercial foundation

- Added immutable, monotonic commercial policy decisions. Less-trusted remote,
  preference, entitlement, or rollout data cannot override build, owner, or
  emergency-deny policy.
- Added separate OSS, public Play, and internal Play build boundaries. The
  public Play build is permanently production-dark for this release; internal
  test overrides are isolated to `app.codecks.internal`.
- Added typed, default-off feature metadata and fail-closed preference/config
  migration without exposing commercial release controls as user flags.
- Added portable snapshot contracts that allow only bounded safe catalog,
  layout, theme, and routine data. Raw commands, credentials, clipboard data,
  execution history, and diagnostics are rejected.
- Added backend-side contracts for opaque account identity, session rotation,
  deletion ordering, server-authoritative entitlement state, reconciliation,
  and transaction-bound integrity checks. No live backend is contacted by the
  public app.
- Added inert public auth, sync, billing, privacy, and ad adapters. Their SDKs
  and network clients do not initialize in the public release.
- Added an internal-only lab UI for testing account, deletion, sync, restore,
  billing, privacy, and ad state machines without exposing those surfaces in
  the public app.

## Local product foundations

- Added typed offline catalogs for routines, themes, and safe SSH action packs.
- Catalog imports are bounded, signed-payload aware, conflict checked, and
  rollback capable.
- SSH catalog entries bridge only to the existing strict command allowlist;
  they do not expose raw shell execution.
- Premium metadata is display-only and cannot restrict public functionality.

## Validation

- Android OSS debug APK built, installed as the separate
  `app.codecks.debug` package, cold-launched on an emulator, remained foreground,
  exposed a valid UI tree, and produced no crash or ANR marker.
- Managed Pixel 6 API 35 internal lab suite: 9 tests, 0 failures; one intentional
  physical-Mac SSH skip.
- Managed Pixel 6 API 35 production-dark suite: 7 tests, 0 failures; one
  intentional physical-Mac SSH skip. Its receipt recorded 52 passes, 3 explicit
  `NOT_RUN` checks, and no failures.
- Bundletool universal and split inspection passed: one universal APK and 258
  split APKs validated for package identity, minimum SDK, namespace isolation,
  routes, endpoints, and compiled policy.
- Public reachability attacks passed 10/10.
- Online and confirmed-offline cold-start receipts passed; no commercial work
  appeared in jobs, alarms, services, providers, WorkManager, Binder, logs, or
  UID network statistics.
- Static commercial, backup, architecture, release-surface, no-shrink, secret,
  and artifact checks passed.
- Production code minification and resource shrinking remain disabled.

## Intentionally disabled

- Sign-in and account creation.
- Cloud backup and sync.
- Play Billing and premium enforcement.
- Ads and consent SDK startup.
- Commercial remote configuration or backend traffic.

These capabilities require a later explicit owner activation decision. Keeping
them disabled indefinitely remains supported.

## Assets

- `codecks-release.apk`: production-signed, unshrunk Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.36...v0.1.37
