package io.codecks.ui.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadSurfaceReachabilityPolicyTest {
    @Test
    fun deletedSurfaces_haveNoProductionOrManifestReachability() {
        val mainRoot = File("src/main")
        val productionText = mainRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .joinToString("\n") { it.readText() }
        val deletedSymbols = listOf(
            listOf("Connection", "Screen").joinToString(""),
            listOf("Devices", "Screen").joinToString(""),
            listOf("Devices", "ViewModel").joinToString(""),
            listOf("Devices", "UiState").joinToString(""),
        )

        deletedSymbols.forEach { symbol -> assertFalse(symbol, productionText.contains(symbol)) }
        listOf(
            "src/main/java/io/codecks/ui/connection/ConnectionScreen.kt",
            "src/main/java/io/codecks/ui/settings/DevicesScreen.kt",
            "src/main/java/io/codecks/ui/settings/DevicesViewModel.kt",
        ).forEach { path -> assertFalse(path, File(path).exists()) }
    }

    @Test
    fun settingsOwnsConnectionAndDeviceSetup_withoutNoOpCallbacks() {
        val settings = File("src/main/java/io/codecks/ui/settings/SettingsScreen.kt").readText()
        val connectionSections = File("src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt").readText()
        val compositionRoot = File("src/main/java/io/codecks/AppCompositionRoot.kt").readText()

        assertTrue(settings.contains("MacConnectionSettingsPanel("))
        assertTrue(connectionSections.contains("state.setupSnapshot"))
        assertTrue(connectionSections.contains("state.connectionDiagnostic()"))
        assertTrue(compositionRoot.contains("onOpenConnection = { navigate(SettingsRoute) }"))
        listOf("onDevices", "onAppearance", "onAdvanced").forEach { callback ->
            assertFalse(callback, settings.contains(callback))
            assertFalse(callback, compositionRoot.contains(callback))
        }
    }

    @Test
    fun adaptivePreviewsRemainIntentionalTooling() {
        val previews = File("src/main/java/io/codecks/ui/quality/AdaptiveSurfacePreviews.kt")

        assertTrue(previews.exists())
        assertTrue(previews.readText().contains("@Preview"))
    }
}
