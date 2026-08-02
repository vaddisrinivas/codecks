package io.codecks.domain.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSessionLifecycleTest {
    @Test
    fun readRequiresExplicitSessionForegroundSurfaceAndUnlock() {
        val initial = ClipboardSessionState()
        val visible = initial.withEnvironment(
            appForeground = true,
            surfaceVisible = true,
            deviceUnlocked = true,
            nowMillis = 1_000,
        )

        assertFalse(visible.canReadPhoneClipboard)

        val active = visible.start(nowMillis = 1_000, durationMillis = 5_000)
        assertEquals(ClipboardSessionPhase.ActiveVisible, active.phase)
        assertTrue(active.canReadPhoneClipboard)

        assertEquals(
            ClipboardSessionPhase.Hidden,
            active.withEnvironment(appForeground = false, nowMillis = 1_100).phase,
        )
        assertEquals(
            ClipboardSessionPhase.Hidden,
            active.withEnvironment(surfaceVisible = false, nowMillis = 1_100).phase,
        )
        assertEquals(
            ClipboardSessionPhase.Locked,
            active.withEnvironment(deviceUnlocked = false, nowMillis = 1_100).phase,
        )
    }

    @Test
    fun expiryIsEvaluatedBeforeReadCanResume() {
        val active = ClipboardSessionState(
            appForeground = true,
            surfaceVisible = true,
            deviceUnlocked = true,
        ).start(nowMillis = 1_000, durationMillis = 500)

        val expired = active.withEnvironment(
            appForeground = true,
            surfaceVisible = true,
            deviceUnlocked = true,
            nowMillis = 1_500,
        )

        assertEquals(ClipboardSessionPhase.Expired, expired.phase)
        assertFalse(expired.canReadPhoneClipboard)
    }

    @Test
    fun stopRevokesReadAuthorityImmediately() {
        val active = ClipboardSessionState(
            appForeground = true,
            surfaceVisible = true,
            deviceUnlocked = true,
        ).start(nowMillis = 1_000)

        assertEquals(ClipboardSessionPhase.Inactive, active.stop().phase)
        assertFalse(active.stop().canReadPhoneClipboard)
    }

    @Test
    fun wallClockRollbackCannotExtendSessionAuthority() {
        val active = ClipboardSessionState(
            appForeground = true,
            surfaceVisible = true,
            deviceUnlocked = true,
        ).start(
            nowMillis = 100_000,
            durationMillis = 5_000,
            elapsedRealtimeMillis = 10_000,
        )

        val expired = active.withEnvironment(
            nowMillis = 1_000,
            elapsedRealtimeMillis = 15_000,
        )

        assertEquals(ClipboardSessionPhase.Expired, expired.phase)
        assertFalse(expired.canReadPhoneClipboard)
    }
}
