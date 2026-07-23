# Codecks v0.1.19 release notes

Date: July 23, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.19

## Summary

Codecks v0.1.19 fixes the release-only Mac SSH failure still visible in v0.1.18.

## Changes since v0.1.18

- Fixed `java.lang.IllegalAccessException: com.jcraft.jsch.DHEC256 is not accessible` in signed/minified release builds.
- Kept JSch package structure and members intact so package-private SSH algorithm classes remain callable during real key exchange.
- Preserved the v0.1.18 narrowed scope: this release is only for Mac SSH action reliability.

## Verification

- `python3 tools/secret_surface_check.py`
- `./gradlew :app:check`
- `./gradlew :app:assembleRelease` with local non-production signing
- Decompiled local minified release APK and confirmed JSch package names remain under `com.jcraft.jsch`.
- Installed signed release APK on phone in-place with app data preserved.
- Ran logcat smoke check after opening the app.

## Assets

- `codecks-v0.1.19.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for release verification.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.18...v0.1.19
