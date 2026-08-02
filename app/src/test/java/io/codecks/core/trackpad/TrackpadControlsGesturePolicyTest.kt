package io.codecks.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadControlsGesturePolicyTest {
    @Test
    fun fiveFingerHoldOpensHiddenControlsWithoutChangingFourFingerDeckGesture() {
        val source = File("src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()

        assertTrue(source.contains("4 -> \"hold:Deck\""))
        assertTrue(source.contains("5 -> \"hold:Controls\""))
        assertTrue(source.contains("onOpenControlsGesture()"))
        assertTrue(source.contains("controlsOpen = true"))
    }
}
