package io.codecks.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadControlsGesturePolicyTest {
    @Test
    fun fiveFingerHoldOpensHiddenControlsWithoutChangingFourFingerDeckGesture() {
        val adapter = File("src/main/java/io/codecks/ui/mouse/RawTrackpadAdapter.kt").readText()
        val screen = File("src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()

        assertTrue(adapter.contains("4 -> \"hold:Deck\""))
        assertTrue(adapter.contains("5 -> \"hold:Controls\""))
        assertTrue(adapter.contains("onOpenControlsGesture()"))
        assertTrue(screen.contains("controlsOpen = true"))
    }
}
