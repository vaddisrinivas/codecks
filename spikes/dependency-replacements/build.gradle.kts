plugins {
    id("com.android.library") version "9.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.9"
    id("androidx.room") version "2.8.4"
}

android {
    namespace = "io.codecks.spikes"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("io.github.nsk90:kstatemachine:0.38.1")
    implementation("io.github.nsk90:kstatemachine-coroutines:0.38.1")
    implementation("io.github.nsk90:kstatemachine-serialization:0.38.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.github.alorma.compose-settings:ui-tiles:3.1.0")
    implementation("com.github.alorma.compose-settings:ui-tiles-extended:3.1.0")
    implementation("com.github.skydoves:colorpicker-compose:1.2.0")
    implementation("br.com.devsrsouza.compose.icons:font-awesome:1.1.1")
    implementation("br.com.devsrsouza.compose.icons:simple-icons:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
