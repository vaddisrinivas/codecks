package io.codecks.ui.mouse.lockscreen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockscreenReactiveExclusionPolicyTest {
    @Test
    fun lockscreenActivityDoesNotConstructReactiveTrackpadUi() {
        val activitySource = File("src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadActivity.kt").readText()
        val viewModelSource = File("src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadViewModel.kt").readText()

        assertFalse(activitySource.contains("ReactiveTrackpadViewModel"))
        assertFalse(activitySource.contains("ReactiveTrackpadCard"))
        assertFalse(viewModelSource.contains("ReactiveTrackpadViewModel"))
        assertFalse(viewModelSource.contains("ReactiveTrackpadCard"))
        assertFalse(viewModelSource.contains("ReactiveActionExecutor"))
    }

    @Test
    fun restrictedLockscreenCopyKeepsGuardedSurfacesOutOfScope() {
        val screenSource = File("src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadScreen.kt").readText()

        assertTrue(screenSource.contains("keyboard, deck, settings, and SSH"))
        assertFalse(screenSource.contains("Clipboard"))
        assertFalse(screenSource.contains("Reactive Trackpad"))
    }
}
