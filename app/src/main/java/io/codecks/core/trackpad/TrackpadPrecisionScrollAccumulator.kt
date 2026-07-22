package io.codecks.core.trackpad

import kotlin.math.abs

class TrackpadPrecisionScrollAccumulator {
    private var remainder = 0f

    fun add(delta: Float, speed: Float, acceleration: Float, divisor: Float = 5.5f): Float {
        val velocityGain = 1f +
            (abs(delta) / 24f).coerceIn(0f, 1f) * acceleration.coerceIn(0f, 1f) * 1.5f
        remainder += (delta / divisor) * speed.coerceIn(0.05f, 2f) * velocityGain
        if (abs(remainder) < 1f) return 0f
        val whole = remainder.toInt()
        remainder -= whole
        return whole.toFloat()
    }

    fun reset() {
        remainder = 0f
    }
}
