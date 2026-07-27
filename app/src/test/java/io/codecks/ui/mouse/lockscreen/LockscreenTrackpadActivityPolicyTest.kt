package io.codecks.ui.mouse.lockscreen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LockscreenTrackpadActivityPolicyTest {
    @Test
    fun activityShowsOverLockscreenWithoutAutoWakingAndReleasesButtonsOnStop() {
        val source = File("src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadActivity.kt").readText()

        assertTrue(source.contains("setShowWhenLocked(true)"))
        assertTrue(source.contains("setTurnScreenOn(false)"))
        assertTrue(source.contains("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)"))
        assertTrue(source.contains("viewModel.releaseButtons()"))
    }

    @Test
    fun fullTrackpadOnlyLaunchesThroughExplicitUnlockPath() {
        val source = File("src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadActivity.kt").readText()

        assertTrue(source.contains("LockscreenTrackpadEvent.RequestUnlock -> dismissKeyguardForFullTrackpad()"))
        assertTrue(source.contains("keyguardManager.requestDismissKeyguard("))
        assertTrue(source.contains("TrackpadEntryActivity.fullTrackpadIntent"))
    }
}
