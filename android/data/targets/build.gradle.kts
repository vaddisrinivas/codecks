plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.codecks.data.targets"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(project(":domain:targets"))
    implementation(project(":transport:ssh"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
