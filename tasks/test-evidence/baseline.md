# Baseline Evidence

Base SHA: `4da58b279c50fec182bf52f0a73861d9b3bc22fd`

## Passed

- `./gradlew :shared:jvmTest`
- `./gradlew --no-daemon :app:lintDebug`
- `./gradlew --no-daemon :app:assembleDebug`

## Blocked

- `./gradlew :shared:jvmTest :app:testReleaseUnitTest`
- Reason: release signing config is absent in this environment: `releaseStoreFile`, `releaseKeyAlias`, `releaseStorePassword`, `releaseKeyPassword`.

## Notes

- A combined debug lint/assemble run crashed the Gradle daemon with JVM internal error `assembler_aarch64.hpp:264` while lint was running.
- Rerunning lint in a single-use daemon passed.

