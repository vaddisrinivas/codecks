package io.codecks.core.trackpad

class TrackpadMultiTapDetector {
    private var lastPointerCount = 0
    private var lastTapMillis = 0L

    fun register(pointerCount: Int, timestampMillis: Long, timeoutMillis: Int): Boolean {
        val timeout = timeoutMillis.coerceIn(250, 900).toLong()
        val secondTap = pointerCount == lastPointerCount &&
            lastTapMillis > 0L &&
            timestampMillis - lastTapMillis in 1..timeout
        if (secondTap) {
            reset()
        } else {
            lastPointerCount = pointerCount
            lastTapMillis = timestampMillis
        }
        return secondTap
    }

    fun reset() {
        lastPointerCount = 0
        lastTapMillis = 0L
    }
}
