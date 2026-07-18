plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val optionalContextSurfacesEnabled = providers.gradleProperty("optionalContextSurfacesEnabled").orElse("false")
val quickSettingsTileEnabled = providers.gradleProperty("quickSettingsTileEnabled").orElse("false")
val liteLlmBaseUrl = providers.gradleProperty("liteLlmBaseUrl").orElse("")
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

val validateReleaseConfig by tasks.registering {
    group = "verification"
    description = "Fails release builds when required production configuration is missing."
    doLast {
        val missing = buildList {
            if (releaseStoreFile.get().isBlank()) add("releaseStoreFile")
            if (releaseKeyAlias.get().isBlank()) add("releaseKeyAlias")
            if (releaseStorePassword.get().isBlank()) add("releaseStorePassword")
            if (releaseKeyPassword.get().isBlank()) add("releaseKeyPassword")
        }
        check(missing.isEmpty()) {
            "Release config incomplete: ${missing.joinToString()}"
        }
    }
}

val validateArchitectureBoundaries by tasks.registering {
    group = "verification"
    description = "Blocks new Android/data/ui imports from pure domain and core logic packages."
    doLast {
        val sourceRoot = file("src/main/java/io/codex/s23deck")
        val guardedRoots = listOf("domain", "core/actions", "core/trackpad")
        val allowedBaseline = setOf(
            "core/actions/DefaultActionRunner.kt",
            "core/trackpad/TrackpadGestureEngine.kt",
            "core/trackpad/TrackpadSettingsRepository.kt",
            "domain/ai/StructuredDraftParser.kt",
            "domain/commerce/CommerceContracts.kt",
        )
        val forbidden = Regex("""^import (android\.|androidx\.|io\.codex\.s23deck\.data\.|io\.codex\.s23deck\.ui\.).*""")
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
        val featureDefaults = file("src/main/java/io/codex/s23deck/domain/commerce/FeatureFlagDefaults.kt")
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
        val problems = buildList {
            if ("usesCleartextTraffic=\"true\"" in mainManifest || "android:usesCleartextTraffic=\"true\"" in mainManifest) {
                add("Main manifest must not allow cleartext traffic")
            }
            if ("debugfiles" in mainManifest || "debug_file_paths" in mainManifest || "GestureTestActivity" in mainManifest) {
                add("Debug FileProvider/test activity must stay out of src/main manifest")
            }
            if ("debugfiles" !in debugManifest) {
                add("Debug bundle FileProvider should remain scoped to src/debug manifest")
            }
            if ("android:resizeableActivity=\"true\"" !in mainManifest) {
                add("MainActivity must stay resizeable for Samsung DeX/freeform")
            }
            if ("android:enabled=\"\${optionalContextSurfacesEnabled}\"" !in mainManifest) {
                add("Context/widget manifest components must be explicitly gated for local-only v1")
            }
            val optionalContextGateCount = Regex("""android:enabled="\$\{optionalContextSurfacesEnabled\}"""")
                .findAll(mainManifest)
                .count()
            if (optionalContextGateCount < 3) {
                add("Widget provider, widget launch activity, and notification listener must all use optionalContextSurfacesEnabled")
            }
            listOf(
                ".DeckWidgetProvider",
                ".WidgetLaunchActivity",
                ".data.notifications.DeckBridgeNotificationListenerService",
            ).filterNot { it in mainManifest }.forEach { component ->
                add("Release ledger expects optional component missing from manifest check: $component")
            }
            if ("val optionalContextSurfacesEnabled = providers.gradleProperty(\"optionalContextSurfacesEnabled\").orElse(\"false\")" !in buildScript) {
                add("Optional context/widget surfaces must default disabled")
            }
            if ("android:enabled=\"\${quickSettingsTileEnabled}\"" !in mainManifest) {
                add("Quick Settings tile must be explicitly gated for local-only v1")
            }
            if ("val quickSettingsTileEnabled = providers.gradleProperty(\"quickSettingsTileEnabled\").orElse(\"false\")" !in buildScript) {
                add("Quick Settings tile default must remain disabled")
            }
            listOf(
                "FeatureFlag.ContextDeck to false",
                "FeatureFlag.Widget to false",
                "FeatureFlag.Activity to false",
                "FeatureFlag.Devices to false",
                "FeatureFlag.Premium to false",
                "FeatureFlag.Paywall to false",
                "FeatureFlag.Advanced to false",
                "FeatureFlag.Appearance to false",
                "FeatureFlag.Labs to false",
            ).filterNot { it in featureDefaults }.forEach { expectedDefault ->
                add("Local-only feature default must remain release-off: $expectedDefault")
            }
            if ("applicationIdSuffix = \".debug\"" !in buildScript || "versionNameSuffix = \"-debug\"" !in buildScript) {
                add("Debug build must keep distinct app id and version suffix")
            }
            if ("isMinifyEnabled = true" !in buildScript || "isShrinkResources = true" !in buildScript) {
                add("Release build must keep minify and resource shrinking enabled")
            }
            if (privacyLedger.isBlank()) {
                add("Permission/privacy ledger is missing at docs/security/PERMISSION_PRIVACY_LEDGER.md")
            }
            if (releaseLedger.isBlank()) {
                add("Codecks release ledger is missing at docs/release/CODECKS_RELEASE_LEDGER.md")
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
    namespace = "io.codex.s23deck"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.codecks"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["optionalContextSurfacesEnabled"] = optionalContextSurfacesEnabled.get()
        manifestPlaceholders["quickSettingsTileEnabled"] = quickSettingsTileEnabled.get()
        buildConfigField("Boolean", "LOCAL_ONLY_V1", "true")
        buildConfigField("String", "LITELLM_BASE_URL", "\"${liteLlmBaseUrl.get()}\"")
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile.get().isNotBlank()) {
                storeFile = file(releaseStoreFile.get())
            }
            releaseKeyAlias.get().takeIf { it.isNotBlank() }?.let { keyAlias = it }
            releaseStorePassword.get().takeIf { it.isNotBlank() }?.let { storePassword = it }
            releaseKeyPassword.get().takeIf { it.isNotBlank() }?.let { keyPassword = it }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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

}

afterEvaluate {
    tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "lintRelease", "preReleaseBuild") }
        .configureEach {
            dependsOn(validateReleaseConfig)
            dependsOn(validateReleaseSurface)
        }
    tasks.matching { it.name in setOf("check", "lintDebug", "testDebugUnitTest") }
        .configureEach {
            dependsOn(validateArchitectureBoundaries)
            dependsOn(validateReleaseSurface)
        }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")
    implementation("br.com.devsrsouza.compose.icons:font-awesome:1.1.1")
    implementation("br.com.devsrsouza.compose.icons:tabler-icons:1.1.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
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
