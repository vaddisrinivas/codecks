package io.codex.s23deck.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadLockTaskPolicyTest {
    @Test
    fun trackpadOffersExplicitUserInitiatedScreenPinning() {
        val mainActivity = File("src/main/java/io/codex/s23deck/MainActivity.kt")

        assertTrue("MainActivity.kt must be readable for screen-pinning regression guard", mainActivity.exists())
        val source = mainActivity.readText()
        assertTrue(source.contains("onToggleSessionPin ="))
        assertTrue(source.contains("host.startLockTask()"))
        assertTrue(source.contains("host.stopLockTask()"))
    }

    @Test
    fun trackpadChromeExplainsBackExitAndHidesEmptyDynamicTray() {
        val mouseScreen = File("src/main/java/io/codex/s23deck/ui/mouse/MouseScreen.kt").readText()

        assertTrue(mouseScreen.contains("Tap Lock to protect against Home gestures"))
        assertTrue(mouseScreen.contains("Pin app"))
        assertTrue(mouseScreen.contains("Quiet while using Trackpad"))
        assertTrue(mouseScreen.contains("Screen blanks after idle"))
        assertTrue(mouseScreen.contains("background(Color.Black.copy(alpha = 0.96f))"))
        assertTrue(mouseScreen.contains("onActivity = ::recordTrackpadActivity"))
        assertTrue(mouseScreen.contains("phoneNotificationLaneEnabled && !quietModeEnabled"))
        assertTrue(mouseScreen.contains("dynamicEnabled = dynamicActions.isNotEmpty()"))
        assertTrue(mouseScreen.contains("if (dynamicEnabled) add(TrackpadQuickTray.Dynamic to Icons.Outlined.AutoAwesome)"))
        assertTrue(mouseScreen.contains("if (quickTray == TrackpadQuickTray.Dynamic && dynamicActions.isEmpty())"))
    }
}
