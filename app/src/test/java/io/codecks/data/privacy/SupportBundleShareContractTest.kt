package io.codecks.data.privacy

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportBundleShareContractTest {
    @Test
    fun releaseManifestOwnsNarrowNonExportedSupportProvider() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val paths = File("src/main/res/xml/support_file_paths.xml").readText()
        val debugManifest = File("src/debug/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("\${applicationId}.supportfiles"))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("@xml/support_file_paths"))
        assertTrue(paths.contains("<cache-path"))
        assertTrue(paths.contains("path=\"support-bundles/\""))
        assertFalse(paths.contains("path=\".\""))
        assertFalse(paths.contains("<root-path"))
        assertFalse(debugManifest.contains("androidx.core.content.FileProvider"))
    }
}
