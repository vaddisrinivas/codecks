package io.codecks.domain.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClipboardProcessRecreationTest {
    @Test
    fun newProcessDoesNotRestorePriorTimedSession() {
        val priorProcess = ClipboardSessionState(
            appForeground = true,
            surfaceVisible = true,
            deviceUnlocked = true,
        ).start(nowMillis = 1_000, durationMillis = 60_000)
        val recreatedProcess = ClipboardSessionState().withEnvironment(
            appForeground = false,
            surfaceVisible = false,
            deviceUnlocked = true,
            nowMillis = 2_000,
        )

        assertFalse(priorProcess.requestedUntilMillis == null)
        assertEquals(ClipboardSessionPhase.Inactive, recreatedProcess.phase)
        assertFalse(recreatedProcess.canReadPhoneClipboard)
    }
}
