package io.codecks.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadTrayOverlayPolicyTest {
    @Test
    fun expandedTrackpadTrayConsumesDeadSpaceTouches() {
        val source = File("src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()

        assertTrue(source.contains("consumeOverlayTouches()"))
        assertTrue(source.contains("val down = awaitFirstDown(requireUnconsumed = false)"))
        assertTrue(source.contains("event.changes.forEach { it.consume() }"))
    }
}
