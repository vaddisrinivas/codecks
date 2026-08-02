package io.codecks

import java.io.File
import io.codecks.ui.app.ShellNavigationMode
import io.codecks.ui.app.shellAccessibilityLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static manifest and large-window policy coverage only.
 * This is not physical Samsung DeX or secondary-display execution evidence.
 */
class DexAdaptivePolicyTest {
    @Test
    fun manifestAllowsResizableAndFreeformWindows() {
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
    fun largeWindowShellUsesDedicatedRailThreshold() {
        val shell = File("src/main/java/io/codecks/ui/app/CodecksAppShell.kt").readText()
        assertEquals(
            ShellNavigationMode.BottomBar,
            shellAccessibilityLayout(839, 720, 1f, fullscreen = false).navigationMode,
        )
        assertEquals(
            ShellNavigationMode.Rail,
            shellAccessibilityLayout(840, 720, 1f, fullscreen = false).navigationMode,
        )
        assertEquals(
            ShellNavigationMode.BottomBar,
            shellAccessibilityLayout(1280, 720, 2f, fullscreen = false).navigationMode,
        )
        assertTrue(shell.contains("val accessibilityLayout = shellAccessibilityLayout("))
        assertTrue(shell.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(shell.contains("ModalBottomSheet(onDismissRequest"))
        assertTrue(shell.contains(""") "AI Builder" else currentRoute.title()"""))
        assertTrue(shell.contains("AiBuilderRoute -> 0"))
    }
}
