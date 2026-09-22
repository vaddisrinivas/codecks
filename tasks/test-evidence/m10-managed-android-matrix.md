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

## Local image discovery (2026-08-17)

| API | locally installed image | exact `aosp` eligible | local result |
| --- | --- | --- | --- |
| 31 | `default/arm64-v8a` | yes | compact/standard/tablet `PASS` |
| 32 | `default/arm64-v8a` | yes | compact/standard/tablet `PASS` |
| 33 | `default/arm64-v8a` | yes | compact/standard/tablet `PASS` |
| 34 | `default/arm64-v8a` | yes | compact/standard/tablet `PASS` |
| 35 | `default/arm64-v8a`, revision 2 | yes | compact/standard/tablet `PASS` |
| 36 | `default/arm64-v8a` | yes | compact/standard/tablet `PASS` |

All exact AOSP images were locally installed. The runner invokes only a named
Gradle-managed virtual device; connected devices are never selected.

## Local current-source receipt

- All 18 API/shape profiles: `PASS`, exactly 5 named tests each, 90 total,
  0 failures, 0 errors, 0 skipped.
- Results: `app/build/outputs/androidTest-results/managedDevice/debug/flavors/oss/`.
- Target APK SHA-256: `47f71bd89bdf48a0e82d157a20b0765c52f4caf14c6d761c1f508c97ce27f0a4`.
- Test APK SHA-256: `3434ea36e63e09a773761b30425418f9d0f5e814c909cbc6c2becf876567fdc7`.
- Every JSON receipt binds exact source files, target/test APKs, five testcase
  identities, managed device name, device-info/proto/XML outputs, and exact
  `system-images/android-API/default/arm64-v8a/package.xml` digest.
- AndroidTest compilation: `PASS`.
- All 18 generated `OssDebugAndroidTest` tasks: discovered.
- `testOssReleaseUnitTest`: `PASS`.
- commercial build boundaries: `PASS`.
- commercial production-dark static proof: 32 tests and 13 receipt checks `PASS`.
- release no-shrink invariant and shell lint: `PASS`.
- True OS process-kill/relaunch and old-APK-to-new-APK installation remain
  `NOT_RUN`; activity recreation, persisted install marker, and the real legacy
  target migration function are autonomous proxies, not those stronger proofs.
