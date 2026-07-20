package io.codex.s23deck.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadTapResolverPolicyTest {
    @Test
    fun singleTapIsDelayedSoDoubleTapDoesNotEmitAnExtraClickFirst() {
        val mouseScreen = File("src/main/java/io/codex/s23deck/ui/mouse/MouseScreen.kt").readText()

        assertTrue(mouseScreen.contains("SINGLE_TAP_DELAY_MS = 140L"))
        assertTrue(mouseScreen.contains("pendingSingleTapRunnable"))
        assertTrue(mouseScreen.contains("postDelayed(pendingSingleTapRunnable, SINGLE_TAP_DELAY_MS)"))
        assertTrue(mouseScreen.contains("removeCallbacks(pendingSingleTapRunnable)"))
        assertTrue(mouseScreen.contains("onLeftClick()"))
        assertTrue(mouseScreen.contains("if (controlsOpen) {"))
    }
}
