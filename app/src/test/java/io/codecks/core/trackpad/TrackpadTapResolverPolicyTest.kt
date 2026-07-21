package io.codecks.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadTapResolverPolicyTest {
    @Test
    fun singleTapIsDelayedSoDoubleTapDoesNotEmitAnExtraClickFirst() {
        val mouseScreen = File("src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()

        assertTrue(mouseScreen.contains("SINGLE_TAP_DELAY_MS = 140L"))
        assertTrue(mouseScreen.contains("pendingSingleTapRunnable"))
        assertTrue(mouseScreen.contains("postDelayed(pendingSingleTapRunnable, SINGLE_TAP_DELAY_MS)"))
        assertTrue(mouseScreen.contains("removeCallbacks(pendingSingleTapRunnable)"))
        assertTrue(mouseScreen.contains("onLeftClick()"))
        assertTrue(mouseScreen.contains("if (controlsOpen) {"))
    }
}
