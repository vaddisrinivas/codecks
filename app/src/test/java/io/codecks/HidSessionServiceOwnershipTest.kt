package io.codecks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidSessionServiceOwnershipTest {
    @Test
    fun rehydrationCodecFailsClosedAndRoundTripsExplicitIntent() {
        assertEquals(
            HidDesiredConnectionState.Disconnected,
            HidRehydrationCodec.decode("corrupt").desiredConnectionState,
        )
        HidDesiredConnectionState.entries.forEach { desired ->
            val record = HidRehydrationRecord(desiredConnectionState = desired)
            assertEquals(record, HidRehydrationCodec.decode(HidRehydrationCodec.encode(record)))
        }
    }

    @Test
    fun criticalSystemEventsCarryLosslessControlDecisions() {
        val connected = HidState(
            lifecycle = HidLifecycle.Connected,
            isConnected = true,
            isReady = true,
            desiredConnectionState = HidDesiredConnectionState.Connected,
        )
        val locked = reduceHidSystemEvent(connected, HidSystemEvent.UserLocked)
        assertTrue(locked.invalidatePendingInputs)
        assertEquals(HidInputAccess.PointerOnly, locked.state.inputAccess)

        val bluetoothOff = reduceHidSystemEvent(connected, HidSystemEvent.BluetoothOff)
        assertTrue(bluetoothOff.cancelConnectionAttempt)
        assertEquals(HidLifecycle.Suspended, bluetoothOff.state.lifecycle)
    }
}
