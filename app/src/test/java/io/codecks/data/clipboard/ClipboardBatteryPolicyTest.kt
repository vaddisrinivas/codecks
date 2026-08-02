package io.codecks.data.clipboard

import io.codecks.domain.clipboard.ClipboardBatteryPolicy
import io.codecks.domain.clipboard.ClipboardSessionPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardBatteryPolicyTest {
    @Test
    fun batterySaverPausesAutomaticPollingButDoesNotModelManualActions() {
        assertFalse(
            ClipboardBatteryPolicy.automaticPollingAllowed(
                ClipboardSessionPhase.ActiveVisible,
                batterySaverActive = true,
            ),
        )
        assertTrue(
            ClipboardBatteryPolicy.automaticPollingAllowed(
                ClipboardSessionPhase.ActiveVisible,
                batterySaverActive = false,
            ),
        )
    }

    @Test
    fun hiddenLockedExpiredAndInactiveSessionsCannotPoll() {
        ClipboardSessionPhase.entries
            .filterNot { it == ClipboardSessionPhase.ActiveVisible }
            .forEach { phase ->
                assertFalse(ClipboardBatteryPolicy.automaticPollingAllowed(phase, batterySaverActive = false))
            }
    }
}
