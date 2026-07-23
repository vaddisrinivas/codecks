# Codecks v0.1.18 release notes

Date: July 23, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.18

## Summary

Codecks v0.1.18 fixes the remaining release-only JSch/R8 Mac action failure found after v0.1.17.

## Fix

- Kept JSch top-level key-exchange classes such as `com.jcraft.jsch.DHEC256` in release builds.
- Kept Android-relevant JSch signature, keypair, userauth, compression, and JCE implementation classes.
- Still avoids keeping optional desktop integrations that reference Windows, Kerberos, JNA, or BouncyCastle classes not shipped in the Android app.

## Verification

- `python3 tools/secret_surface_check.py`
- `./gradlew :app:check`
- `./gradlew :app:assembleRelease` with local non-production signing properties
- Decompiled local minified release APK and confirmed `com.jcraft.jsch.DHEC256`, `com.jcraft.jsch.DHGEX256`, and `com.jcraft.jsch.jce.Random` are present.

## Release assets

- `codecks-v0.1.18.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.17...v0.1.18
