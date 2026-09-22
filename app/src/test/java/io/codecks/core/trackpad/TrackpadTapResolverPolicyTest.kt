package io.codecks.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadTapResolverPolicyTest {
    @Test
    fun singleTapIsDelayedSoDoubleTapDoesNotEmitAnExtraClickFirst() {
        val adapter = File("src/main/java/io/codecks/ui/mouse/RawTrackpadAdapter.kt").readText()
        val screen = File("src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()

        assertTrue(adapter.contains("SINGLE_TAP_DELAY_MS = 140L"))
        assertTrue(adapter.contains("pendingSingleTapRunnable"))
        assertTrue(adapter.contains("postDelayed(pendingSingleTapRunnable, SINGLE_TAP_DELAY_MS)"))
        assertTrue(adapter.contains("removeCallbacks(pendingSingleTapRunnable)"))
        assertTrue(adapter.contains("onLeftClick()"))
        assertTrue(screen.contains("if (controlsOpen) {"))
    }
}
