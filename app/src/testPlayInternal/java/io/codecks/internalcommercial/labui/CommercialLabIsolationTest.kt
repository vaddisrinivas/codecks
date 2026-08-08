package io.codecks.internalcommercial.labui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialLabIsolationTest {
    private val activityName = "io.codecks.internalcommercial.labui.CommercialLabActivity"
    private val launcherName = "io.codecks.internalcommercial.labui.CommercialLabLauncher"

    @Test
    fun launchPolicyRejectsEveryPublicPackage() {
        assertTrue(CommercialLabLaunchPolicy.allows("app.codecks.internal"))
        assertFalse(CommercialLabLaunchPolicy.allows("app.codecks.internal.debug"))
        assertFalse(CommercialLabLaunchPolicy.allows("app.codecks"))
        assertFalse(CommercialLabLaunchPolicy.allows("app.codecks.debug"))
        assertFalse(CommercialLabLaunchPolicy.allows("app.codecks.internalattacker"))
    }

    @Test
    fun sourceManifestsKeepLabOutOfPublicVariants() {
        listOf("src/main/AndroidManifest.xml", "src/oss/AndroidManifest.xml", "src/play/AndroidManifest.xml")
            .map(::File)
            .filter(File::exists)
            .forEach { manifest ->
                val source = manifest.readText()
                assertFalse(manifest.path, source.contains(activityName))
                assertFalse(manifest.path, source.contains(launcherName))
                assertFalse(manifest.path, source.contains("Commercial Lab"))
            }

        val internal = File("src/playInternal/AndroidManifest.xml").readText()
        assertTrue(internal.contains(activityName))
        assertTrue(internal.contains("android:exported=\"false\""))
        assertTrue(internal.contains(launcherName))
    }

    @Test
    fun generatedPublicManifestsContainNoLabComponentWhenAvailable() {
        listOf("ossRelease", "playRelease").forEach { variant ->
            val root = File("build/intermediates/merged_manifests/$variant")
            if (!root.exists()) return@forEach
            root.walkTopDown().filter { it.isFile && it.name == "AndroidManifest.xml" }.forEach { manifest ->
                val source = manifest.readText()
                assertFalse(manifest.path, source.contains(activityName))
                assertFalse(manifest.path, source.contains(launcherName))
                assertFalse(manifest.path, source.contains("Commercial Lab"))
            }
        }
    }
}
