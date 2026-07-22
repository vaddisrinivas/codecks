package io.codecks.core.trackpad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadMultiTapDetectorTest {
    @Test
    fun secondTapRequiresSameFingerCountInsideTimeout() {
        val detector = TrackpadMultiTapDetector()

        assertFalse(detector.register(pointerCount = 2, timestampMillis = 1_000, timeoutMillis = 500))
        assertFalse(detector.register(pointerCount = 3, timestampMillis = 1_200, timeoutMillis = 500))
        assertTrue(detector.register(pointerCount = 3, timestampMillis = 1_500, timeoutMillis = 500))
        assertFalse(detector.register(pointerCount = 3, timestampMillis = 2_100, timeoutMillis = 500))
    }

    @Test
    fun twoFingerDoubleTapRecognizesRealisticDownUpGap() {
        val detector = TrackpadMultiTapDetector()

        assertFalse(detector.register(pointerCount = 2, timestampMillis = 1_140, timeoutMillis = 620))
        assertTrue(detector.register(pointerCount = 2, timestampMillis = 1_510, timeoutMillis = 620))
    }

    @Test
    fun fourFingerDoubleTapIsSupported() {
        val detector = TrackpadMultiTapDetector()

        assertFalse(detector.register(pointerCount = 4, timestampMillis = 2_000, timeoutMillis = 620))
        assertTrue(detector.register(pointerCount = 4, timestampMillis = 2_420, timeoutMillis = 620))
    }
}
