# M10 managed Android matrix foundation

Evidence level: `AUTONOMOUS_PROXY` only. This matrix is not Samsung, vendor,
physical-phone, real DeX, release-signer, or human-acceptance evidence.

## Declared matrix

- APIs: 31, 32, 33, 34, 35, 36.
- Hardware profiles per API: Small Phone (`compact`), Pixel 6 (`standard`),
  Pixel Tablet (`tablet`).
- Image source: exact managed-device `aosp` lane.
- App identity: `app.codecks.debug`; managed-device tasks cannot select a connected
  physical target and the harness rejects `app.codecks`.
- CI sharding: one profile per job, 18 independent jobs, fail-fast disabled.
- Each successful shard records the installed system-image package path and
  package metadata SHA-256 beside its AndroidTest results.

## Exercised checks

- isolated clean debug install and installed APK metadata;
- startup, activity recreation/process-recreation proxy, portrait/landscape;
- persisted marker/update-migration proxy;
- API 33+ app-locale changes; API 31-32 localized-resource compatibility;
- dark/light mode changes;
- offline Wi-Fi/data toggles;
- Bluetooth permission adopted/dropped through the instrumentation shell identity;
- background restriction app-op and recovery;
- compact/standard/tablet smallest-width class, window-size, orientation, and
  attached-window basics;
- no-shrink and commercial production-dark static guardrails.

## Local image discovery (2026-08-10)

| API | locally installed image | exact `aosp` eligible | local result |
| --- | --- | --- | --- |
| 31 | none | no | `NOT_RUN` |
| 32 | none | no | `NOT_RUN` |
| 33 | none | no | `NOT_RUN` |
| 34 | `google_apis/arm64-v8a` | no | `NOT_RUN` |
| 35 | `default/arm64-v8a`, revision 2 | yes | compact/standard/tablet `PASS` |
| 36 | `google_apis_playstore/arm64-v8a` | no | `NOT_RUN` |

Missing exact images remain `NOT_RUN`; CI may download the exact AOSP image.
The runner invokes only a named Gradle-managed virtual device; connected devices
are never selected.

## Local representative receipt

- `m10CompactApi35OssDebugAndroidTest`: `PASS`, 5 tests, 0 failures, 0 skipped,
  29.251 seconds test time; strengthened rerun completed in 1m45s.
- `m10StandardApi35OssDebugAndroidTest`: `PASS`, 5 tests, 0 failures, 0 skipped,
  20.784 seconds test time; strengthened rerun completed in 52s.
- `m10TabletApi35OssDebugAndroidTest`: `PASS`, 5 tests, 0 failures, 0 skipped,
  23.997 seconds test time; strengthened rerun completed in 54s.
- Results: `app/build/outputs/androidTest-results/managedDevice/debug/flavors/oss/`.
- Standard/tablet image receipts bind `system-images/android-35/default/arm64-v8a/package.xml`
  at SHA-256 `e83eba51031b390db39d32956fb4331301fe77b3e57d77517c541524429079f6`.
- AndroidTest compilation: `PASS`.
- All 18 generated `OssDebugAndroidTest` tasks: discovered.
- `testOssReleaseUnitTest`: `PASS`.
- commercial build boundaries: `PASS`.
- commercial production-dark static proof: 32 tests and 13 receipt checks `PASS`.
- release no-shrink invariant and shell lint: `PASS`.
- API 31-34 and 36 exact AOSP profiles: `NOT_RUN` locally because exact images
  were unavailable.
- True OS process-kill/relaunch and old-APK-to-new-APK installation remain
  `NOT_RUN`; activity recreation, persisted install marker, and the real legacy
  target migration function are autonomous proxies, not those stronger proofs.
