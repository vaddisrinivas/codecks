package io.codecks.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadLockTaskPolicyTest {
    @Test
    fun trackpadOffersExplicitUserInitiatedScreenPinning() {
        val mainActivity = File("src/main/java/io/codecks/AppDestinationSupport.kt")

        assertTrue("AppDestinationSupport.kt must be readable for screen-pinning regression guard", mainActivity.exists())
        val source = mainActivity.readText()
        assertTrue(source.contains("onToggleSessionPin ="))
        assertTrue(source.contains("host.startLockTask()"))
        assertTrue(source.contains("host.stopLockTask()"))
    }

    @Test
    fun trackpadChromeExplainsBackExitAndHidesEmptyDynamicTray() {
        val mouseScreen = File("src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()
        val surface = File("src/main/java/io/codecks/ui/mouse/TrackpadSurface.kt").readText()
        val controls = File("src/main/java/io/codecks/ui/mouse/MouseControls.kt").readText()

        assertTrue(surface.contains("Back returns to Deck"))
        assertTrue(controls.contains("Pin app"))
        assertTrue(controls.contains("Quiet while using Trackpad"))
        assertTrue(controls.contains("Screen blanks after idle"))
        assertTrue(surface.contains("background(Color.Black.copy(alpha = 0.96f))"))
        assertTrue(surface.contains("onActivity = ::recordTrackpadActivity"))
        assertTrue(mouseScreen.contains("phoneNotificationLaneEnabled && !quietModeEnabled"))
        assertTrue(mouseScreen.contains("dynamicEnabled = dynamicActions.isNotEmpty()"))
        assertTrue(controls.contains("if (dynamicEnabled) add(TrackpadQuickTray.Dynamic to Icons.Outlined.AutoAwesome)"))
        assertTrue(mouseScreen.contains("if (quickTray == TrackpadQuickTray.Dynamic && dynamicActions.isEmpty())"))
    }
}
