package io.codex.s23deck.core.trackpad

import androidx.compose.ui.geometry.Offset
import io.codex.s23deck.HidCommand
import kotlin.math.abs

class TrackpadGestureEngine {
    fun motionFor(pointerCount: Int): TrackpadMotionMode = when (pointerCount) {
        1 -> TrackpadMotionMode.Pointer
        2 -> TrackpadMotionMode.Scroll
        else -> TrackpadMotionMode.ReservedGesture
    }

    fun gestureFor(
        maxPointers: Int,
        totalPan: Offset,
        dragLockEnabled: Boolean,
    ): TrackpadGestureEvent {
        val pan = totalPan.dominantPan()
        return when {
            maxPointers >= 5 && totalPan.getDistanceSquared() < 400f -> TrackpadGestureEvent.Command(HidCommand.Launchpad)
            maxPointers >= 5 && pan == DominantPan.Left -> TrackpadGestureEvent.Command(HidCommand.AppSwitcher)
            maxPointers >= 5 && pan == DominantPan.Right -> TrackpadGestureEvent.Command(HidCommand.AppSwitcher)
            maxPointers >= 5 && pan == DominantPan.Up -> TrackpadGestureEvent.Command(HidCommand.MissionControl)
            maxPointers >= 5 && pan == DominantPan.Down -> TrackpadGestureEvent.Command(HidCommand.ShowDesktop)
            maxPointers >= 4 && pan == DominantPan.Right -> TrackpadGestureEvent.Command(HidCommand.SpaceRight)
            maxPointers >= 4 && pan == DominantPan.Left -> TrackpadGestureEvent.Command(HidCommand.SpaceLeft)
            maxPointers >= 4 && pan == DominantPan.Down -> TrackpadGestureEvent.Command(HidCommand.AppExpose)
            maxPointers >= 4 && pan == DominantPan.Up -> TrackpadGestureEvent.Command(HidCommand.MissionControl)
            maxPointers == 3 && pan == DominantPan.Right -> TrackpadGestureEvent.Command(HidCommand.SpaceRight)
            maxPointers == 3 && pan == DominantPan.Left -> TrackpadGestureEvent.Command(HidCommand.SpaceLeft)
            maxPointers == 3 && pan == DominantPan.Down -> TrackpadGestureEvent.Command(HidCommand.AppExpose)
            maxPointers == 3 && pan == DominantPan.Up -> TrackpadGestureEvent.Command(HidCommand.MissionControl)
            maxPointers == 3 && totalPan.getDistanceSquared() < 256f -> TrackpadGestureEvent.Command(HidCommand.ShowDesktop)
            maxPointers > 2 && totalPan.getDistanceSquared() < 196f -> TrackpadGestureEvent.None
            maxPointers == 2 && totalPan.getDistanceSquared() < 196f -> TrackpadGestureEvent.RightClick
            totalPan.getDistanceSquared() < 100f && !dragLockEnabled -> TrackpadGestureEvent.LeftClick
            else -> TrackpadGestureEvent.None
        }
    }
}

sealed interface TrackpadGestureEvent {
    data object None : TrackpadGestureEvent
    data object LeftClick : TrackpadGestureEvent
    data object RightClick : TrackpadGestureEvent
    data class Command(val command: HidCommand) : TrackpadGestureEvent
}

enum class TrackpadMotionMode {
    Pointer,
    Scroll,
    ReservedGesture,
}

private enum class DominantPan {
    Left,
    Right,
    Up,
    Down,
}

private fun Offset.dominantPan(threshold: Float = 24f): DominantPan? {
    val absX = abs(x)
    val absY = abs(y)
    return when {
        absX < threshold && absY < threshold -> null
        absX >= absY && x > 0f -> DominantPan.Right
        absX >= absY -> DominantPan.Left
        y > 0f -> DominantPan.Down
        else -> DominantPan.Up
    }
}
