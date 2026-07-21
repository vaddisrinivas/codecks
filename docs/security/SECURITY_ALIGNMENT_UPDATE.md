# Best Practices and Security Alignment Update

## Android component and intent hardening

- **Improvement description:** Kept only the launcher as an always-enabled exported activity, removed the unused external text-share entry, verified app-private internal destination authorization, retained immutable `PendingIntent` flags, and kept optional system components disabled by default.
- **Priority:** High
- **Impact:** Reduces unauthorized navigation and component-hijacking surface while preserving Android AppWidget, Quick Settings, and notification-listener contracts when explicitly enabled.

Files modified:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/codecks/MainActivity.kt`
- `app/src/main/java/io/codecks/IntentDestinationPolicy.kt`
- `app/src/main/java/io/codecks/DeckWidgetProvider.kt`
- `app/src/main/java/io/codecks/DeckTileService.kt`

```diff
 <application
     android:allowBackup="false"
+    android:fullBackupContent="false"
+    android:usesCleartextTraffic="false">

-<intent-filter>
-    <action android:name="android.intent.action.SEND" />
-    <category android:name="android.intent.category.DEFAULT" />
-    <data android:mimeType="text/plain" />
-</intent-filter>
```

Verification:

1. `./gradlew :app:testDebugUnitTest :app:lintDebug`
2. Inspect merged release manifest and confirm optional components default disabled.
3. Confirm every `PendingIntent` includes `FLAG_IMMUTABLE` and an explicit component.

## Private-data and release-signing isolation

- **Improvement description:** Added repository-wide private identity/path checks, moved release signing to environment variables and GitHub Actions secrets, separated debug package identity, and excluded private evidence/build artifacts.
- **Priority:** High
- **Impact:** Prevents credentials, signing keys, workstation identity, and private QA evidence from entering public Git history.

Files modified:

- `.gitignore`
- `.github/workflows/quality.yml`
- `.github/workflows/release.yml`
- `app/build.gradle.kts`
- `tools/secret_surface_check.py`

```diff
-val releaseStoreFile = providers.gradleProperty("releaseStoreFile").orElse("")
+val releaseStoreFile = providers.environmentVariable("CODECKS_RELEASE_STORE_FILE")
+    .orElse(providers.gradleProperty("releaseStoreFile"))
+    .orElse("")

-applicationId = "io.codecks"
+applicationId = "app.codecks"

-applicationIdSuffix = ".next"
+applicationIdSuffix = ".debug"
```

Verification:

1. `python3 tools/secret_surface_check.py`
2. `git ls-files` contains no keystore, `.env`, `local.properties`, APK, AAB, device screenshot dump, or private path.
3. `apksigner verify --verbose --print-certs` passes for the release asset.

## Dormant cloud-commerce removal

- **Improvement description:** Removed Google sign-in, Play Billing, hosted entitlement, and backend HTTP implementations and dependencies from the public local-only build. Kept only local repositories and moved Android feature-flag persistence out of the domain layer.
- **Priority:** High
- **Impact:** Removes unused billing permission, Google exported services, account metadata, and data-transport components from the release manifest and reduces APK size.

Files modified:

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/codecks/MainActivity.kt`
- `app/src/main/java/io/codecks/domain/features/FeatureFlagDefaults.kt`
- `app/src/main/java/io/codecks/data/features/LocalFeatureFlagRepository.kt`

```diff
-implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
-implementation("com.android.billingclient:billing-ktx:9.1.0")
-implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

-val commerce = if (localOnlyV1) localRepositories() else productionRepositories()
+val commerce = localRepositories()
```

Verification:

1. APK permissions contain no `com.android.vending.BILLING`.
2. Merged release manifest contains no Google sign-in, Play Billing, Credential Play Services, or Google data-transport component.
3. Local account, billing, and entitlement repositories remain covered by unit tests.
