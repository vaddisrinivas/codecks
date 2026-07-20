package io.codex.s23deck.core.trackpad

import androidx.compose.ui.geometry.Offset
import io.codex.s23deck.HidCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackpadGestureEngineTest {
    private val engine = TrackpadGestureEngine()

    @Test
    fun coreMotionModes_matchTrackpadBehavior() {
        assertEquals(TrackpadMotionMode.Pointer, engine.motionFor(1))
        assertEquals(TrackpadMotionMode.Scroll, engine.motionFor(2))
        assertEquals(TrackpadMotionMode.ReservedGesture, engine.motionFor(3))
        assertEquals(TrackpadMotionMode.ReservedGesture, engine.motionFor(4))
        assertEquals(TrackpadMotionMode.ReservedGesture, engine.motionFor(5))
    }

    @Test
    fun threeFourFiveFingerGestures_emitMacCommands() {
        assertCommand(HidCommand.SpaceRight, engine.gestureFor(3, Offset(48f, 0f), false))
        assertCommand(HidCommand.SpaceLeft, engine.gestureFor(3, Offset(-48f, 0f), false))
        assertCommand(HidCommand.AppExpose, engine.gestureFor(3, Offset(0f, 48f), false))
        assertCommand(HidCommand.MissionControl, engine.gestureFor(3, Offset(0f, -48f), false))
        assertCommand(HidCommand.Launchpad, engine.gestureFor(5, Offset.Zero, false))
        assertCommand(HidCommand.AppSwitcher, engine.gestureFor(5, Offset(-48f, 0f), false))
        assertCommand(HidCommand.AppSwitcher, engine.gestureFor(5, Offset(48f, 0f), false))
        assertCommand(HidCommand.MissionControl, engine.gestureFor(5, Offset(0f, -48f), false))
        assertCommand(HidCommand.ShowDesktop, engine.gestureFor(5, Offset(0f, 48f), false))
        assertCommand(HidCommand.SpaceLeft, engine.gestureFor(4, Offset(-48f, 0f), false))
    }

    @Test
    fun twoFingerHorizontalSwipe_emitsBrowserNavigationCommands() {
        assertCommand(HidCommand.BrowserBack, engine.gestureFor(2, Offset(-68f, 12f), false))
        assertCommand(HidCommand.BrowserForward, engine.gestureFor(2, Offset(70f, -10f), false))
    }

    @Test
    fun twoFingerVerticalSwipe_remainsScrollUntilGestureEnd() {
        assertEquals(TrackpadGestureEvent.None, engine.gestureFor(2, Offset(16f, 82f), false))
        assertEquals(TrackpadGestureEvent.RightClick, engine.gestureFor(2, Offset(4f, 4f), false))
    }

    @Test
    fun twoFingerHorizontalCandidate_reservesScrollEarly() {
        assertEquals(true, engine.shouldReserveTwoFingerBrowserSwipe(Offset(-24f, 4f)))
        assertEquals(false, engine.shouldReserveTwoFingerBrowserSwipe(Offset(10f, 36f)))
    }

    @Test
    fun diagonalSwipesUseDominantAxis() {
        assertCommand(HidCommand.MissionControl, engine.gestureFor(3, Offset(42f, -80f), false))
        assertCommand(HidCommand.AppExpose, engine.gestureFor(4, Offset(-42f, 80f), false))
        assertCommand(HidCommand.SpaceRight, engine.gestureFor(3, Offset(80f, -42f), false))
    }

    @Test
    fun dragLock_suppressesTapClick() {
        assertEquals(TrackpadGestureEvent.LeftClick, engine.gestureFor(1, Offset.Zero, false))
        assertEquals(TrackpadGestureEvent.RightClick, engine.gestureFor(2, Offset.Zero, false))
        assertEquals(TrackpadGestureEvent.None, engine.gestureFor(1, Offset.Zero, true))
    }

    @Test
    fun adaptiveTapThreshold_canTightenAccidentalClickGuard() {
        assertEquals(TrackpadGestureEvent.LeftClick, engine.gestureFor(1, Offset(8f, 0f), false))
        assertEquals(
            TrackpadGestureEvent.None,
            engine.gestureFor(
                maxPointers = 1,
                totalPan = Offset(8f, 0f),
                dragLockEnabled = false,
                tapMovementThresholdPx = 6f,
            ),
        )
    }

    @Test
    fun gestureDecisionSummariesAreContentFree() {
        val decision = engine.classifyGesture(
            maxPointers = 1,
            totalPan = Offset(2f, 1f),
            durationMillis = 130L,
            dragLockEnabled = false,
        )

        assertEquals(TrackpadGestureEvent.LeftClick, decision.event)
        assertEquals("pointers=1 movement=still duration=tap classified=left_click", decision.sample.diagnosticSummary())
    }

    private fun assertCommand(expected: HidCommand, actual: TrackpadGestureEvent) {
        assertEquals(TrackpadGestureEvent.Command(expected), actual)
    }
}
