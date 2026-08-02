package io.codecks.data.update

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdateManifestPolicyTest {
    @Test
    fun appHasNoInstallerPermissionAndUpdatePackageHasNoBackgroundEntryPoint() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val updateSources = File("src/main/java/io/codecks/data/update")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertFalse(manifest.contains("REQUEST_INSTALL_PACKAGES"))
        assertFalse(updateSources.contains("WorkManager"))
        assertFalse(updateSources.contains("AlarmManager"))
        assertFalse(updateSources.contains("Service"))
        assertFalse(updateSources.contains(".apk", ignoreCase = true))
    }
}
