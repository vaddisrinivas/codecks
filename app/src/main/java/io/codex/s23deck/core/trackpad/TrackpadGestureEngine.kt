package io.codex.s23deck.core.trackpad

import androidx.compose.ui.geometry.Offset
import io.codex.s23deck.HidCommand
import kotlin.math.abs
import kotlin.math.max

class TrackpadGestureEngine {
    fun motionFor(pointerCount: Int): TrackpadMotionMode = when (pointerCount) {
        1 -> TrackpadMotionMode.Pointer
        2 -> TrackpadMotionMode.Scroll
        else -> TrackpadMotionMode.ReservedGesture
    }

    fun classifyGesture(
        maxPointers: Int,
        totalPan: Offset,
        durationMillis: Long,
        dragLockEnabled: Boolean,
        tapMovementThresholdPx: Float = DEFAULT_TAP_MOVEMENT_THRESHOLD_PX,
    ): TrackpadGestureDecision {
        val event = gestureFor(
            maxPointers = maxPointers,
            totalPan = totalPan,
            dragLockEnabled = dragLockEnabled,
            tapMovementThresholdPx = tapMovementThresholdPx,
        )
        return TrackpadGestureDecision(
            event = event,
            sample = TrackpadGestureSample(
                maxPointers = maxPointers,
                panDistancePx = totalPan.getDistance(),
                durationMillis = durationMillis.coerceAtLeast(0L),
                classification = event.safeLabel(),
            ),
        )
    }

    fun gestureFor(
        maxPointers: Int,
        totalPan: Offset,
        dragLockEnabled: Boolean,
        tapMovementThresholdPx: Float = DEFAULT_TAP_MOVEMENT_THRESHOLD_PX,
    ): TrackpadGestureEvent {
        val pan = totalPan.dominantPan()
        val tapThreshold = tapMovementThresholdPx.coerceIn(
            MIN_TAP_MOVEMENT_THRESHOLD_PX,
            MAX_TAP_MOVEMENT_THRESHOLD_PX,
        )
        val tapDistanceSquared = tapThreshold * tapThreshold
        return when {
            maxPointers == 2 && totalPan.isBrowserSwipe() && totalPan.x < 0f -> TrackpadGestureEvent.Command(HidCommand.BrowserBack)
            maxPointers == 2 && totalPan.isBrowserSwipe() -> TrackpadGestureEvent.Command(HidCommand.BrowserForward)
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
            totalPan.getDistanceSquared() < tapDistanceSquared && !dragLockEnabled -> TrackpadGestureEvent.LeftClick
            else -> TrackpadGestureEvent.None
        }
    }

    fun shouldReserveTwoFingerBrowserSwipe(totalPan: Offset): Boolean =
        totalPan.isBrowserSwipe(candidateDistancePx = 18f)

    companion object {
        const val DEFAULT_TAP_MOVEMENT_THRESHOLD_PX = 10f
        const val MIN_TAP_MOVEMENT_THRESHOLD_PX = 6f
        const val MAX_TAP_MOVEMENT_THRESHOLD_PX = 18f
    }
}

data class TrackpadGestureDecision(
    val event: TrackpadGestureEvent,
    val sample: TrackpadGestureSample,
)

data class TrackpadGestureSample(
    val maxPointers: Int,
    val panDistancePx: Float,
    val durationMillis: Long,
    val classification: String,
) {
    fun diagnosticSummary(): String =
        "pointers=$maxPointers movement=${panDistancePx.movementBucket()} duration=${durationMillis.durationBucket()} classified=$classification"
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

private fun TrackpadGestureEvent.safeLabel(): String = when (this) {
    TrackpadGestureEvent.None -> "none"
    TrackpadGestureEvent.LeftClick -> "left_click"
    TrackpadGestureEvent.RightClick -> "right_click"
    is TrackpadGestureEvent.Command -> "command:${command.name}"
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

private fun Offset.isBrowserSwipe(candidateDistancePx: Float = 56f): Boolean {
    val absX = abs(x)
    val absY = abs(y)
    if (absX < candidateDistancePx) return false
    return absX >= max(candidateDistancePx, absY * 1.75f)
}

private fun Float.movementBucket(): String = when {
    this < 4f -> "still"
    this < 10f -> "small"
    this < 24f -> "medium"
    this < 56f -> "large"
    else -> "swipe"
}

private fun Long.durationBucket(): String = when {
    this < 90L -> "tap-fast"
    this < 220L -> "tap"
    this < 520L -> "press"
    else -> "hold"
}
