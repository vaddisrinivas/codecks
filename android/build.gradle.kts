plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("checkReleaseBoundary") {
    group = "verification"
    description = "Fails if local release picks up forbidden cloud/commercial dependencies."

    doLast {
        val forbiddenPatterns = listOf(
            Regex("firebase", RegexOption.IGNORE_CASE),
            Regex("billing", RegexOption.IGNORE_CASE),
            Regex("remote[ _-]?config", RegexOption.IGNORE_CASE),
        )
        val text = rootProject.fileTree(".") {
            include("**/build.gradle.kts", "**/libs.versions.toml", "**/AndroidManifest.xml")
            exclude("build.gradle.kts")
            exclude("**/build/**")
        }.joinToString("\n") { it.readText() }

        forbiddenPatterns.forEach { pattern ->
            check(!pattern.containsMatchIn(text)) {
                "Forbidden localRelease dependency/config token found: ${pattern.pattern}"
            }
        }
    }
}
