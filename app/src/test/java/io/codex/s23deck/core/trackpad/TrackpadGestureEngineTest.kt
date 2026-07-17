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

    private fun assertCommand(expected: HidCommand, actual: TrackpadGestureEvent) {
        assertEquals(TrackpadGestureEvent.Command(expected), actual)
    }
}
