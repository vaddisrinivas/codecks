# Releasing Codecks

Release signing material must never be committed. The build accepts these environment variables:

- `CODECKS_RELEASE_STORE_FILE`
- `CODECKS_RELEASE_KEY_ALIAS`
- `CODECKS_RELEASE_STORE_PASSWORD`
- `CODECKS_RELEASE_KEY_PASSWORD`

## Local verification

```bash
python3 tools/secret_surface_check.py
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

## GitHub release

Repository Actions secrets hold the base64-encoded keystore, alias, and passwords. Pushing a signed version tag runs `.github/workflows/release.yml`, rebuilds from the public commit, verifies quality gates, and publishes a signed APK plus `SHA256SUMS.txt`.

For `v0.1.5` and later, the same workflow also builds and uploads the nested Codecks v2 cockpit preview APK:

- `codecks-<tag>.apk`: signed root Codecks APK.
- `codecks-v2-cockpit-preview-<tag>.apk`: debug-signed Codecks v2 cockpit preview APK.
- `SHA256SUMS.txt`: checksums for both APKs.

```bash
git tag -s v0.1.5 -m "Codecks v0.1.5"
git push origin v0.1.5
```

If signed Git tags are unavailable, use an annotated tag and rely on the signed APK plus published checksum. Never reuse a version name/code for different binaries.

## Key custody

Keep at least two encrypted offline backups of the release keystore. Loss of the key prevents trusted updates to existing installations. Rotation or compromise requires an incident note and a new application identity unless a managed store supports key reset.
