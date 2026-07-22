package io.codecks.core.trackpad

fun shouldTriggerTrackpadHold(
    pointerCount: Int,
    activePointerCount: Int,
    panDistanceSquared: Float,
    movementThresholdPx: Float,
    hasCommand: Boolean,
): Boolean =
    hasCommand &&
        pointerCount in 3..4 &&
        activePointerCount == pointerCount &&
        panDistanceSquared <= movementThresholdPx * movementThresholdPx
