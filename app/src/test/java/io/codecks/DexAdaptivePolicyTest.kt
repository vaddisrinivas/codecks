package io.codecks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DexAdaptivePolicyTest {
    @Test
    fun mainActivityIsResizableForDexAndFreeform() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:name=".MainActivity""""))
        assertTrue(manifest.contains("""android:resizeableActivity="true""""))
        assertFalse(manifest.contains("android:screenOrientation"))
    }

    @Test
    fun trackpadOnlyUsesImmersiveSystemBarsAfterExplicitToggle() {
        val source = File("src/main/java/io/codecks/MainActivity.kt").readText()

        assertTrue(source.contains("val fullscreen = fullscreenOverride == true"))
        assertFalse(source.contains("currentRoute == MouseRoute && !desktopSurface"))
    }

    @Test
    fun coreControlScreensKeepAdaptiveLargeWindowBranches() {
        val home = File("src/main/java/io/codecks/ui/home/HomeScreen.kt").readText()
        val automations = File("src/main/java/io/codecks/ui/automations/AutomationsScreen.kt").readText()
        val editor = File("src/main/java/io/codecks/ui/editor/DeckEditorScreen.kt").readText()

        assertTrue(home.contains("maxWidth >= 900.dp"))
        assertTrue(automations.contains("val wide = maxWidth >= 840.dp"))
        assertTrue(editor.contains("maxWidth >= 840.dp"))
    }
}
