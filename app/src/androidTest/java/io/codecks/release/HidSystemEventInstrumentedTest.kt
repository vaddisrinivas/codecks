package io.codecks.release

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.codecks.HidAppVisibility
import io.codecks.HidBluetoothPower
import io.codecks.HidDesiredConnectionState
import io.codecks.HidLifecycle
import io.codecks.HidState
import io.codecks.HidSystemEvent
import io.codecks.reduceHidSystemEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HidSystemEventInstrumentedTest {
    @Test
    fun managedDeviceLifecycleSequenceKeepsBackgroundConnectionAndRecoversBluetooth() {
        val connected = HidState(
            lifecycle = HidLifecycle.Connected,
            isReady = true,
            isConnected = true,
            desiredConnectionState = HidDesiredConnectionState.Connected,
            bluetoothPower = HidBluetoothPower.On,
        )
        val background = reduceHidSystemEvent(connected, HidSystemEvent.AppBackgrounded).state
        assertEquals(HidAppVisibility.Background, background.appVisibility)
        assertTrue(background.isConnected)

        val off = reduceHidSystemEvent(background, HidSystemEvent.BluetoothOff)
        assertTrue(off.cancelConnectionAttempt)
        assertEquals(HidLifecycle.Suspended, off.state.lifecycle)

        val on = reduceHidSystemEvent(off.state, HidSystemEvent.BluetoothOn)
        assertEquals(HidBluetoothPower.On, on.state.bluetoothPower)
        assertTrue(on.maintainConnection)
        assertFalse(on.state.isConnected)
    }
}
