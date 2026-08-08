import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val optionalContextSurfacesEnabled = providers.gradleProperty("optionalContextSurfacesEnabled").orElse("false")
val liteLlmBaseUrl = providers.gradleProperty("liteLlmBaseUrl").orElse("")
val instrumentedTestBuildType = providers.gradleProperty("codecksInstrumentedTestBuildType").orElse("release")
val releaseStoreFile = providers.environmentVariable("CODECKS_RELEASE_STORE_FILE")
    .orElse(providers.gradleProperty("releaseStoreFile"))
    .orElse("")
val releaseKeyAlias = providers.environmentVariable("CODECKS_RELEASE_KEY_ALIAS")
    .orElse(providers.gradleProperty("releaseKeyAlias"))
    .orElse("")
val releaseStorePassword = providers.environmentVariable("CODECKS_RELEASE_STORE_PASSWORD")
    .orElse(providers.gradleProperty("releaseStorePassword"))
    .orElse("")
val releaseKeyPassword = providers.environmentVariable("CODECKS_RELEASE_KEY_PASSWORD")
    .orElse(providers.gradleProperty("releaseKeyPassword"))
    .orElse("")
val playUploadStoreFile = providers.environmentVariable("CODECKS_PLAY_UPLOAD_STORE_FILE")
    .orElse(providers.gradleProperty("playUploadStoreFile"))
    .orElse("")
val playUploadKeyAlias = providers.environmentVariable("CODECKS_PLAY_UPLOAD_KEY_ALIAS")
    .orElse(providers.gradleProperty("playUploadKeyAlias"))
    .orElse("")
val playUploadStorePassword = providers.environmentVariable("CODECKS_PLAY_UPLOAD_STORE_PASSWORD")
    .orElse(providers.gradleProperty("playUploadStorePassword"))
    .orElse("")
val playUploadKeyPassword = providers.environmentVariable("CODECKS_PLAY_UPLOAD_KEY_PASSWORD")
    .orElse(providers.gradleProperty("playUploadKeyPassword"))
    .orElse("")
val playInternalStoreFile = providers.environmentVariable("CODECKS_PLAY_INTERNAL_STORE_FILE")
    .orElse(providers.gradleProperty("playInternalStoreFile"))
    .orElse("")
val playInternalKeyAlias = providers.environmentVariable("CODECKS_PLAY_INTERNAL_KEY_ALIAS")
    .orElse(providers.gradleProperty("playInternalKeyAlias"))
    .orElse("")
val playInternalStorePassword = providers.environmentVariable("CODECKS_PLAY_INTERNAL_STORE_PASSWORD")
    .orElse(providers.gradleProperty("playInternalStorePassword"))
    .orElse("")
val playInternalKeyPassword = providers.environmentVariable("CODECKS_PLAY_INTERNAL_KEY_PASSWORD")
    .orElse(providers.gradleProperty("playInternalKeyPassword"))
    .orElse("")
val commercialTestBackendUrl = providers.gradleProperty("commercialTestBackendUrl")
    .orElse("https://codecks.invalid")

val validateOssReleaseSigning by tasks.registering {
    group = "verification"
    description = "Requires the protected GitHub/OSS release signer configuration."
    doLast {
        val missing = buildList {
            if (releaseStoreFile.get().isBlank()) add("releaseStoreFile")
            if (releaseKeyAlias.get().isBlank()) add("releaseKeyAlias")
            if (releaseStorePassword.get().isBlank()) add("releaseStorePassword")
            if (releaseKeyPassword.get().isBlank()) add("releaseKeyPassword")
        }
        check(missing.isEmpty()) {
            "OSS release signing config incomplete: ${missing.joinToString()}"
        }
    }
}

val validatePlayReleaseSigning by tasks.registering {
    group = "verification"
    description = "Requires the distinct Play upload signer without inspecting app-signing certificates."
    doLast {
        val missing = buildList {
            if (playUploadStoreFile.get().isBlank()) add("playUploadStoreFile")
            if (playUploadKeyAlias.get().isBlank()) add("playUploadKeyAlias")
            if (playUploadStorePassword.get().isBlank()) add("playUploadStorePassword")
            if (playUploadKeyPassword.get().isBlank()) add("playUploadKeyPassword")
        }
        check(missing.isEmpty()) {
            "Play upload signing config incomplete: ${missing.joinToString()}"
        }
        check(
            releaseStoreFile.get() != playUploadStoreFile.get() ||
                releaseKeyAlias.get() != playUploadKeyAlias.get(),
        ) {
            "OSS release signer and Play upload signer must use distinct configured roles"
        }
    }
}

val validatePlayInternalSigning by tasks.registering {
    group = "verification"
    description = "Allows the debug signer or a complete isolated playInternal signer configuration."
    doLast {
        val values = listOf(
            playInternalStoreFile.get(),
            playInternalKeyAlias.get(),
            playInternalStorePassword.get(),
            playInternalKeyPassword.get(),
        )
        check(values.all(String::isBlank) || values.none(String::isBlank)) {
            "playInternal signing must be entirely absent or completely configured"
        }
        check(commercialTestBackendUrl.get() == "https://codecks.invalid" || commercialTestBackendUrl.get().startsWith("https://")) {
            "playInternal backend placeholder must use HTTPS"
        }
    }
}

val validateArchitectureBoundaries by tasks.registering {
    group = "verification"
    description = "Blocks new Android/data/ui imports from pure domain and core logic packages."
    doLast {
        val sourceRoot = file("src/main/java/io/codecks")
        val guardedRoots = listOf("domain", "core/actions", "core/trackpad")
        val allowedBaseline = setOf(
            "core/actions/DefaultActionRunner.kt",
            "core/trackpad/TrackpadGestureEngine.kt",
            "core/trackpad/TrackpadSettingsRepository.kt",
            "domain/ai/StructuredDraftParser.kt",
            "domain/features/FeatureContracts.kt",
        )
        val forbidden = Regex("""^import (android\.|androidx\.|io\.codecks\.data\.|io\.codecks\.ui\.).*""")
        val violations = guardedRoots
            .flatMap { guardedRoot ->
                file("$sourceRoot/$guardedRoot")
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .flatMap { source ->
                        val relative = source.relativeTo(sourceRoot).invariantSeparatorsPath
                        source.readLines()
                            .filter { forbidden.matches(it) }
                            .map { relative to it }
                    }
            }
            .filter { (relative, _) -> relative !in allowedBaseline }
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "Architecture boundary violations:\n",
                separator = "\n",
            ) { (relative, importLine) -> "$relative: $importLine" }
        }
    }
}

val validateReleaseSurface by tasks.registering {
    group = "verification"
    description = "Fails when debug-only surfaces or unsafe network flags leak into release sources."
    doLast {
        val mainManifest = file("src/main/AndroidManifest.xml").readText()
        val debugManifest = file("src/debug/AndroidManifest.xml").takeIf { it.exists() }?.readText().orEmpty()
        val buildScript = file("build.gradle.kts").readText()
        val featureDefaults = file("src/main/java/io/codecks/domain/features/FeatureFlagDefaults.kt")
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
        val privacyLedgerFile = rootProject.file("docs/security/PERMISSION_PRIVACY_LEDGER.md")
        val privacyLedger = privacyLedgerFile.takeIf { it.exists() }?.readText().orEmpty()
        val releaseLedgerFile = rootProject.file("docs/release/CODECKS_RELEASE_LEDGER.md")
        val releaseLedger = releaseLedgerFile.takeIf { it.exists() }?.readText().orEmpty()
        val manifestPermissions = Regex("""<uses-permission\s+android:name="([^"]+)"""")
            .findAll(mainManifest)
            .map { it.groupValues[1] }
            .toList()
        val forbiddenMinifySetting = "isMinifyEnabled = " + "true"
        val forbiddenResourceShrinkSetting = "isShrinkResources = " + "true"
        val problems = buildList {
            if ("usesCleartextTraffic=\"true\"" in mainManifest || "android:usesCleartextTraffic=\"true\"" in mainManifest) {
                add("Main manifest must not allow cleartext traffic")
            }
            if ("debugfiles" in mainManifest || "debug_file_paths" in mainManifest || "GestureTestActivity" in mainManifest) {
                add("Debug FileProvider/test activity must stay out of src/main manifest")
            }
            if ("supportfiles" !in mainManifest || "support_file_paths" !in mainManifest) {
                add("Release support bundle FileProvider must be declared in src/main manifest")
            }
            if ("androidx.core.content.FileProvider" !in mainManifest ||
                "android:exported=\"false\"" !in mainManifest ||
                "android:grantUriPermissions=\"true\"" !in mainManifest
            ) {
                add("Release support bundle FileProvider must be non-exported with URI grants")
            }
            if ("GestureTestActivity" !in debugManifest) {
                add("Debug gesture test activity must remain scoped to src/debug manifest")
            }
            if ("android:resizeableActivity=\"true\"" !in mainManifest) {
                add("MainActivity must stay resizeable for Samsung DeX/freeform")
            }
            if ("android:enabled=\"\${optionalContextSurfacesEnabled}\"" !in mainManifest) {
                add("Optional notification listener must be explicitly gated for local-only v1")
            }
            val optionalContextGateCount = Regex("""android:enabled="\$\{optionalContextSurfacesEnabled\}"""")
                .findAll(mainManifest)
                .count()
            if (optionalContextGateCount < 1) {
                add("Notification listener must use optionalContextSurfacesEnabled")
            }
            listOf(
                ".data.context.CodecksNotificationListenerService",
                ".ui.mouse.lockscreen.TrackpadEntryActivity",
                ".widget.TrackpadWidgetProvider",
            ).filterNot { it in mainManifest }.forEach { component ->
                add("Expected release component missing from manifest: $component")
            }
            if ("val optionalContextSurfacesEnabled = providers.gradleProperty(\"optionalContextSurfacesEnabled\").orElse(\"false\")" !in buildScript) {
                add("Optional context surfaces must default disabled")
            }
            listOf(
                "FeatureFlag.SmartSuggestions to false",
                "FeatureFlag.SmartDeck to false",
                "FeatureFlag.SmartKeyboard to false",
                "FeatureFlag.SmartClipboard to false",
                "FeatureFlag.SmartRules to false",
                "FeatureFlag.SmartSettings to false",
                "FeatureFlag.SmartTrackpadSuggest to false",
                "FeatureFlag.SmartTrackpadSnap to false",
                "FeatureFlag.SmartOcr to false",
                "FeatureFlag.Labs to false",
            ).filterNot { it in featureDefaults }.forEach { expectedDefault ->
                add("Local-only feature default must remain release-off: $expectedDefault")
            }
            if ("applicationIdSuffix = \".debug\"" !in buildScript || "versionNameSuffix = \"-debug\"" !in buildScript) {
                add("Debug build must keep distinct app id and version suffix")
            }
            if ("isMinifyEnabled = false" !in buildScript || "isShrinkResources = false" !in buildScript) {
                add("Release build must keep minification and resource shrinking disabled")
            }
            if (forbiddenMinifySetting in buildScript || forbiddenResourceShrinkSetting in buildScript) {
                add("Release shrinking is forbidden because it repeatedly broke JSch SSH at runtime")
            }
            if (privacyLedger.isBlank()) {
                add("Permission/privacy ledger is missing at docs/security/PERMISSION_PRIVACY_LEDGER.md")
            }
            if (releaseLedger.isBlank()) {
                add("Codecks release ledger is missing at docs/release/CODECKS_RELEASE_LEDGER.md")
            }
            listOf(
                "TrackpadEntryActivity",
                "TrackpadWidgetProvider",
            ).filterNot { it in releaseLedger }.forEach { component ->
                add("Public release component missing from release ledger: $component")
            }
            manifestPermissions.filterNot { it in privacyLedger }.forEach { permission ->
                add("Manifest permission missing from privacy ledger: $permission")
            }
            if ("NotificationListenerService" in mainManifest && "Notification listener special access" !in privacyLedger) {
                add("Notification listener special access must be documented in privacy ledger")
            }
        }
        check(problems.isEmpty()) {
            problems.joinToString(prefix = "Release surface violations:\n", separator = "\n")
        }
    }
}

android {
    testBuildType = instrumentedTestBuildType.get()

    namespace = "io.codecks"
    compileSdk = 37
    flavorDimensions += "distribution"

    defaultConfig {
        applicationId = "app.codecks"
        minSdk = 28
        targetSdk = 37
        versionCode = 37
        versionName = "0.1.37"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["optionalContextSurfacesEnabled"] = optionalContextSurfacesEnabled.get()
        buildConfigField("Boolean", "LOCAL_ONLY_V1", "true")
        buildConfigField("Boolean", "OPTIONAL_CONTEXT_SURFACES_ENABLED", optionalContextSurfacesEnabled.get())
        buildConfigField("String", "LITELLM_BASE_URL", "\"${liteLlmBaseUrl.get()}\"")
        buildConfigField(
            "String",
            "GITHUB_RELEASES_API_URL",
            "\"https://api.github.com/repos/vaddisrinivas/codecks/releases\"",
        )
        buildConfigField("String", "GITHUB_API_HOST", "\"api.github.com\"")
        buildConfigField("String", "GITHUB_RELEASE_HOST", "\"github.com\"")
    }

    signingConfigs {
        create("ossRelease") {
            if (releaseStoreFile.get().isNotBlank()) {
                storeFile = file(releaseStoreFile.get())
            }
            releaseKeyAlias.get().takeIf { it.isNotBlank() }?.let { keyAlias = it }
            releaseStorePassword.get().takeIf { it.isNotBlank() }?.let { storePassword = it }
            releaseKeyPassword.get().takeIf { it.isNotBlank() }?.let { keyPassword = it }
        }
        create("playUpload") {
            if (playUploadStoreFile.get().isNotBlank()) {
                storeFile = file(playUploadStoreFile.get())
            }
            playUploadKeyAlias.get().takeIf { it.isNotBlank() }?.let { keyAlias = it }
            playUploadStorePassword.get().takeIf { it.isNotBlank() }?.let { storePassword = it }
            playUploadKeyPassword.get().takeIf { it.isNotBlank() }?.let { keyPassword = it }
        }
        create("playInternal") {
            if (playInternalStoreFile.get().isNotBlank()) {
                storeFile = file(playInternalStoreFile.get())
            }
            playInternalKeyAlias.get().takeIf { it.isNotBlank() }?.let { keyAlias = it }
            playInternalStorePassword.get().takeIf { it.isNotBlank() }?.let { storePassword = it }
            playInternalKeyPassword.get().takeIf { it.isNotBlank() }?.let { keyPassword = it }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Do not re-enable either shrinker. JSch discovers SSH algorithms
            // dynamically, and three prior releases broke Mac SSH after R8
            // removed or rewrote classes that static analysis could not trace.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    productFlavors {
        create("oss") {
            dimension = "distribution"
            signingConfig = signingConfigs.getByName("ossRelease")
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"oss\"")
            buildConfigField("String", "COMMERCIAL_POLICY_MODE", "\"ABSENT\"")
            buildConfigField("Boolean", "COMMERCIAL_TEST_OVERRIDES_ALLOWED", "false")
            buildConfigField("String", "COMMERCIAL_TEST_BACKEND_URL", "\"\"")
        }
        create("play") {
            dimension = "distribution"
            signingConfig = signingConfigs.getByName("playUpload")
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"play\"")
            buildConfigField("String", "COMMERCIAL_POLICY_MODE", "\"PRODUCTION_DARK\"")
            buildConfigField("Boolean", "COMMERCIAL_TEST_OVERRIDES_ALLOWED", "false")
            buildConfigField("String", "COMMERCIAL_TEST_BACKEND_URL", "\"\"")
        }
        create("playInternal") {
            dimension = "distribution"
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-play-internal"
            manifestPlaceholders["commercialTestBackendUrl"] = commercialTestBackendUrl.get()
            signingConfig = if (
                listOf(
                    playInternalStoreFile.get(),
                    playInternalKeyAlias.get(),
                    playInternalStorePassword.get(),
                    playInternalKeyPassword.get(),
                ).none(String::isBlank)
            ) {
                signingConfigs.getByName("playInternal")
            } else {
                signingConfigs.getByName("debug")
            }
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"play_internal\"")
            buildConfigField("String", "COMMERCIAL_POLICY_MODE", "\"INTERNAL_TEST_CAPABLE\"")
            buildConfigField("Boolean", "COMMERCIAL_TEST_OVERRIDES_ALLOWED", "true")
            buildConfigField("String", "COMMERCIAL_TEST_BACKEND_URL", "\"${commercialTestBackendUrl.get()}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api35") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

val validateCommercialDependencyBoundaries by tasks.registering {
    group = "verification"
    description = "Rejects commercial SDK dependencies before those integrations are explicitly admitted."
    doLast {
        val forbiddenCoordinates = setOf(
            "com.android.billingclient:billing",
            "com.android.billingclient:billing-ktx",
            "com.google.android.gms:play-services-ads",
            "com.google.android.gms:play-services-ads-lite",
            "com.google.android.ump:user-messaging-platform",
            "com.google.android.play:integrity",
            "com.google.firebase:firebase-auth",
            "com.google.firebase:firebase-config",
            "com.google.firebase:firebase-firestore",
            "com.google.firebase:firebase-functions",
            "com.google.firebase:firebase-analytics",
            "com.google.firebase:firebase-appcheck-playintegrity",
        )
        // Only public artifacts are permanently commercial-SDK-free. The
        // isolated playInternal source set is the deliberate future test seam.
        val publicConfigurationsToCheck = listOf(
            "ossDebugRuntimeClasspath",
            "ossReleaseRuntimeClasspath",
            "playReleaseRuntimeClasspath",
        )
        val resolvedCoordinates = publicConfigurationsToCheck.associateWith { configurationName ->
            configurations.getByName(configurationName)
                .incoming
                .resolutionResult
                .allComponents
                .mapNotNull { component ->
                    val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                    "${id.group}:${id.module}"
                }
                .toSet()
        }
        val violations = resolvedCoordinates.flatMap { (configurationName, coordinates) ->
            coordinates.filter { it in forbiddenCoordinates }.map { configurationName to it }
        }
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "Commercial dependencies leaked into a public distribution: ",
            ) { (configurationName, coordinate) -> "$configurationName=$coordinate" }
        }
        val requiredLocalDependencies = setOf(
            "androidx.credentials:credentials",
            "androidx.work:work-runtime-ktx",
        )
        val missingRequired = resolvedCoordinates.flatMap { (configurationName, coordinates) ->
            requiredLocalDependencies.filterNot(coordinates::contains).map { configurationName to it }
        }
        check(missingRequired.isEmpty()) {
            missingRequired.joinToString(
                prefix = "Required local infrastructure was removed: ",
            ) { (configurationName, coordinate) -> "$configurationName=$coordinate" }
        }
    }
}

val validateOssReleaseArtifact by tasks.registering(Exec::class) {
    group = "verification"
    description = "Builds and scans the GitHub OSS release APK."
    dependsOn("assembleOssRelease")
    commandLine(
        "python3",
        rootProject.file("tools/validate_commercial_artifact.py"),
        "--variant",
        "ossRelease",
        "--artifact",
        layout.buildDirectory.file("outputs/apk/oss/release/app-oss-release.apk").get().asFile,
    )
}

val validatePlayReleaseArtifact by tasks.registering(Exec::class) {
    group = "verification"
    description = "Builds and scans the production-dark Play AAB. It does not upload."
    dependsOn("bundlePlayRelease")
    commandLine(
        "python3",
        rootProject.file("tools/validate_commercial_artifact.py"),
        "--variant",
        "playRelease",
        "--artifact",
        layout.buildDirectory.file("outputs/bundle/playRelease/app-play-release.aab").get().asFile,
    )
}

val validatePlayInternalReleaseArtifact by tasks.registering(Exec::class) {
    group = "verification"
    description = "Builds and scans the isolated internal-test APK."
    dependsOn("assemblePlayInternalRelease")
    commandLine(
        "python3",
        rootProject.file("tools/validate_commercial_artifact.py"),
        "--variant",
        "playInternalRelease",
        "--artifact",
        layout.buildDirectory.file("outputs/apk/playInternal/release/app-playInternal-release.apk").get().asFile,
        "--internal-backend-url",
        commercialTestBackendUrl.get(),
    )
}

val validateCommercialArtifacts by tasks.registering {
    group = "verification"
    description = "Runs all distribution artifact isolation scans."
    dependsOn(
        validateOssReleaseArtifact,
        validatePlayReleaseArtifact,
        validatePlayInternalReleaseArtifact,
    )
}

val enabledDistributionVariants = mutableSetOf<String>()

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        val distribution = variantBuilder.productFlavors
            .firstOrNull { it.first == "distribution" }
            ?.second
        val supported = when (distribution) {
            "oss" -> variantBuilder.buildType in setOf("debug", "release")
            "play", "playInternal" -> variantBuilder.buildType == "release"
            else -> false
        }
        variantBuilder.enable = supported
    }

    onVariants(selector().all()) { variant ->
        val variantName = variant.name
        enabledDistributionVariants += variantName
        val expectedDistribution = when (variantName) {
            "ossDebug", "ossRelease" -> "oss"
            "playRelease" -> "play"
            "playInternalRelease" -> "play_internal"
            else -> error("Unexpected enabled Android variant: $variantName")
        }
        val expectedPolicy = when (variantName) {
            "ossDebug", "ossRelease" -> "absent"
            "playRelease" -> "production_dark"
            "playInternalRelease" -> "internal_test_capable"
            else -> error("Unexpected enabled Android variant: $variantName")
        }
        val expectedApplicationId = when (variantName) {
            "ossDebug" -> "app.codecks.debug"
            "ossRelease", "playRelease" -> "app.codecks"
            "playInternalRelease" -> "app.codecks.internal"
            else -> error("Unexpected enabled Android variant: $variantName")
        }
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val applicationId = variant.applicationId
        val taskName = "validate${variantName.replaceFirstChar(Char::uppercase)}CommercialManifest"
        tasks.register(taskName) {
            group = "verification"
            description = "Rejects commercial initializer leakage in the $variantName merged manifest."
            inputs.file(mergedManifest)
            inputs.property("expectedApplicationId", expectedApplicationId)
            doLast {
                check(applicationId.get() == expectedApplicationId) {
                    "$variantName application ID was ${applicationId.get()}, expected $expectedApplicationId"
                }
                val manifest = mergedManifest.get().asFile.readText()
                val banned = listOf(
                    "com.google.firebase.provider.FirebaseInitProvider",
                    "com.google.android.gms.ads.MobileAdsInitProvider",
                    "com.google.android.gms.ads.AdActivity",
                    "com.google.android.ump",
                    "com.android.billingclient",
                    "com.google.android.play.core.integrity",
                    "com.google.android.play.integrity",
                )
                val violations = banned.filter(manifest::contains)
                check(violations.isEmpty()) {
                    "$variantName merged manifest contains forbidden commercial components: ${violations.joinToString()}"
                }
                check("android:name=\"app.codecks.distribution\"" in manifest) {
                    "$variantName merged manifest is missing the distribution marker"
                }
                check("android:value=\"$expectedDistribution\"" in manifest) {
                    "$variantName merged manifest has the wrong distribution marker"
                }
                check("android:name=\"app.codecks.commercial_policy\"" in manifest) {
                    "$variantName merged manifest is missing the commercial policy marker"
                }
                check("android:value=\"$expectedPolicy\"" in manifest) {
                    "$variantName merged manifest has the wrong commercial policy marker"
                }
                if (variantName == "playInternalRelease") {
                    check("android:name=\"app.codecks.test_backend\"" in manifest) {
                        "playInternalRelease must carry an explicit test-backend marker"
                    }
                } else {
                    check("app.codecks.test_backend" !in manifest) {
                        "$variantName must not carry a test-backend marker"
                    }
                }
            }
        }
    }
}

val validateDistributionMatrix by tasks.registering {
    group = "verification"
    description = "Rejects accidental extra or missing Android distribution variants."
    doLast {
        val expected = setOf("ossDebug", "ossRelease", "playRelease", "playInternalRelease")
        check(enabledDistributionVariants == expected) {
            "Enabled variants were ${enabledDistributionVariants.sorted()}, expected ${expected.sorted()}"
        }
    }
}

val validateCommercialManifests by tasks.registering {
    group = "verification"
    description = "Validates every enabled distribution merged manifest."
    dependsOn(
        "validateOssDebugCommercialManifest",
        "validateOssReleaseCommercialManifest",
        "validatePlayReleaseCommercialManifest",
        "validatePlayInternalReleaseCommercialManifest",
    )
}

val validateCommercialBuildBoundaries by tasks.registering {
    group = "verification"
    description = "Runs dependency and merged-manifest commercial isolation gates."
    dependsOn(validateDistributionMatrix, validateCommercialDependencyBoundaries, validateCommercialManifests)
}

afterEvaluate {
    tasks.matching { it.name in setOf("assembleOssRelease", "packageOssRelease", "bundleOssRelease") }.configureEach {
        dependsOn(validateOssReleaseSigning)
    }
    tasks.matching { it.name in setOf("assemblePlayRelease", "packagePlayRelease", "bundlePlayRelease") }.configureEach {
        dependsOn(validatePlayReleaseSigning)
    }
    tasks.matching { it.name in setOf("assemblePlayInternalRelease", "packagePlayInternalRelease", "bundlePlayInternalRelease") }.configureEach {
        dependsOn(validatePlayInternalSigning)
    }
    tasks.matching {
        it.name in setOf("preOssReleaseBuild", "prePlayReleaseBuild", "prePlayInternalReleaseBuild")
    }.configureEach {
        dependsOn(validateReleaseSurface)
    }
    tasks.matching {
        it.name in setOf("check", "lintOssDebug", "testOssReleaseUnitTest")
    }
        .configureEach {
            dependsOn(validateArchitectureBoundaries)
            dependsOn(validateReleaseSurface)
            dependsOn(validateCommercialBuildBoundaries)
        }
}

dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")
    implementation("br.com.devsrsouza.compose.icons:tabler-icons:1.1.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    implementation("androidx.navigation3:navigation3-ui:1.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.github.mwiede:jsch:2.28.4")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
