package io.codecks.core.trackpad

fun trackpadPointerGain(
    speed: Float,
    dragging: Boolean,
    stylus: Boolean,
    acceleration: Float,
): Float {
    val baseGain = when {
        stylus && dragging -> 0.62f
        stylus && speed < 1.4f -> 0.54f
        stylus && speed < 4.0f -> 0.74f
        stylus -> 0.92f
        dragging -> 0.82f
        speed < 0.9f -> 0.62f
        speed < 3.0f -> 0.86f
        speed < 7.0f -> 1.08f
        else -> 1.26f
    }
    val normalizedAcceleration = acceleration.coerceIn(0.5f, 1.75f)
    val accelerationGain = when {
        dragging -> 0.92f + (normalizedAcceleration * 0.08f)
        stylus -> 0.85f + (normalizedAcceleration * 0.15f)
        speed < 0.9f -> 0.85f + (normalizedAcceleration * 0.15f)
        else -> 0.65f + (normalizedAcceleration * 0.35f)
    }
    return baseGain * accelerationGain
}

fun isTrackpadScrollZone(
    x: Float,
    width: Int,
    railSide: TrackpadRailSide,
    widthFraction: Float = 0.12f,
    minWidthPx: Float = 72f,
): Boolean {
    if (width <= 0) return false
    val zoneWidth = maxOf(width * widthFraction, minWidthPx).coerceAtMost(width.toFloat())
    return when (railSide) {
        TrackpadRailSide.Left -> x <= zoneWidth
        TrackpadRailSide.Right -> x >= width - zoneWidth
    }
}
