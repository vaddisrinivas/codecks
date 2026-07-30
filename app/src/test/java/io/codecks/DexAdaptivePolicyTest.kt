package io.codecks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun mainActivityPreservesStateThroughConfigurationChanges() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val match = Regex("android:configChanges=\"([^\"]+)\"").find(manifest)
        assertNotNull(match)
        val values = match!!.groupValues[1].split("|").map { it.trim() }.toSet()
        assertTrue(values.contains("orientation"))
        assertTrue(values.contains("screenSize"))
        assertTrue(values.contains("smallestScreenSize"))
        assertTrue(values.contains("screenLayout"))

        val mainSource = File("src/main/java/io/codecks/MainActivity.kt").readText()
        assertTrue(mainSource.contains("restoredTopRouteName"))
        assertTrue(mainSource.contains("navRouteFromStateKey"))
        assertTrue(mainSource.contains("routeStateKey(currentRoute)"))
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

    @Test
    fun secondaryDisplayShellUsesDedicatedRailThreshold() {
        val shell = File("src/main/java/io/codecks/ui/app/CodecksAppShell.kt").readText()
        assertTrue(shell.contains("SECONDARY_NAV_RAIL_WIDTH = 840.dp"))
        assertTrue(shell.contains("val useRail = maxWidth >= SECONDARY_NAV_RAIL_WIDTH"))
    }
}
