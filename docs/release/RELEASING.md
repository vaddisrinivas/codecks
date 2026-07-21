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

Repository Actions secrets hold the base64-encoded keystore, alias, and passwords. Pushing a signed version tag runs `.github/workflows/release.yml`, rebuilds from the public commit, verifies quality gates, and publishes exactly one signed APK plus `SHA256SUMS.txt`.

- `codecks-<tag>.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

Do not attach debug, preview, incubator, or alternate-app APKs to public releases.

```bash
git tag -s v0.1.8 -m "Codecks v0.1.8"
git push origin v0.1.8
```

If signed Git tags are unavailable, use an annotated tag and rely on the signed APK plus published checksum. Never reuse a version name/code for different binaries.

## Key custody

Keep at least two encrypted offline backups of the release keystore. Loss of the key prevents trusted updates to existing installations. Rotation or compromise requires an incident note and a new application identity unless a managed store supports key reset.
