# WT03 Android Helper Evidence

Status: focused green

Branch: `codex/reactive-android-helper`

Scope changed:

- `app/src/main/java/io/codecks/platform/helper/ReactiveHelperClient.kt`
- `app/src/main/java/io/codecks/platform/helper/ReactiveHelperContracts.kt`
- `app/src/test/java/io/codecks/platform/helper/ReactiveHelperClientTest.kt`
- `app/src/test/java/io/codecks/platform/helper/ReactiveHelperContractsTest.kt`

Implemented:

- Android helper credentials now carry the pinned helper identity fingerprint.
- Helper open verifies Mac ID, helper ID, helper fingerprint, challenge proof, and optional server-returned pin.
- Proof request includes an HMAC pin acknowledgement over the challenge identity.
- Stored helper identity boundary persists helper ID and public-key fingerprint, not raw secrets.
- Client tests cover success, Mac ID mismatch, helper fingerprint mismatch, bad server proof, and replayed response rejection.

Validation:

- `ANDROID_HOME=<android-sdk> ./gradlew :app:testReleaseUnitTest --tests 'io.codecks.platform.helper.*' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit` -> `BUILD SUCCESSFUL`
- `git diff --check` -> clean

Notes:

- Dummy release signing properties were used only to satisfy the repo's unit-test Gradle gate. No APK was signed, installed, or released.
- No physical phone, ADB, uninstall, clear, or package replacement was used.
