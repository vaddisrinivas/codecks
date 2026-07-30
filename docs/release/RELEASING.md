# Releasing Codecks

Release signing material must never be committed. The build accepts these environment variables:

- `CODECKS_RELEASE_STORE_FILE`
- `CODECKS_RELEASE_KEY_ALIAS`
- `CODECKS_RELEASE_STORE_PASSWORD`
- `CODECKS_RELEASE_KEY_PASSWORD`

## Local verification

```bash
python3 tools/secret_surface_check.py
./scripts/verify_release_no_shrink.sh
./gradlew :shared:jvmTest
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
./scripts/verify_release_no_shrink.sh app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

Production code minification and resource shrinking stay disabled because prior R8 releases repeatedly broke JSch SSH at runtime.

## GitHub release

Repository Actions secrets hold the base64-encoded keystore, alias, and passwords. Pushing a signed version tag runs `.github/workflows/release.yml`, rebuilds from the public commit, verifies quality gates, and publishes exactly one signed APK plus `SHA256SUMS.txt`.

Full source quality runs once on the pull request. It does not rerun after the
merge or during release. The release workflow accepts only a tag whose commit
is contained in `main`, restores the production signing key, enforces the
no-shrink invariant, builds the signed APK, verifies its checksum, and runs the
exact signed artifact on the managed emulator before publication.

- `codecks-release.apk`: signed Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

Do not attach debug, preview, incubator, or alternate-app APKs to public releases.

```bash
VERSION=vX.Y.Z
git tag -s "$VERSION" -m "Codecks $VERSION"
git push origin "$VERSION"
```

If signed Git tags are unavailable, use an annotated tag and rely on the signed APK plus published checksum. Never reuse a version name/code for different binaries.

## Key custody

Keep at least two encrypted offline backups of the release keystore. Loss of the key prevents trusted updates to existing installations. Rotation or compromise requires an incident note and a new application identity unless a managed store supports key reset.
