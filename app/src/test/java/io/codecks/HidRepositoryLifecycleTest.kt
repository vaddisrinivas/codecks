package io.codecks

import io.codecks.domain.connection.ConnectionIssueCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HidRepositoryLifecycleTest {
    @Test
    fun controllerStatusesMapToTypedSignals() {
        val cases = mapOf(
            "Bluetooth idle" to HidLifecycleSignal.Idle,
            "Opening HID profile" to HidLifecycleSignal.Opening,
            "Registering HID app" to HidLifecycleSignal.Opening,
            "HID registered" to HidLifecycleSignal.Ready,
            "Connecting Test Mac" to HidLifecycleSignal.Connecting,
            "Connected Test Mac" to HidLifecycleSignal.Connected,
            "Disconnected" to HidLifecycleSignal.Disconnected,
            "Bluetooth permission missing" to HidLifecycleSignal.PermissionMissing,
            "Bluetooth unavailable" to HidLifecycleSignal.BluetoothUnavailable,
            "HID profile open failed" to HidLifecycleSignal.ProfileUnregistered,
            "HID registration failed" to HidLifecycleSignal.ProfileRegistrationFailed,
            "Connect request failed" to HidLifecycleSignal.ConnectionRequestFailed,
        )

        cases.forEach { (status, expected) ->
            assertEquals(status, expected, classifyHidLifecycleSignal(status, false, false))
        }
    }

    @Test
    fun permissionPairingAndRegistrationFailuresRequireRepairWithoutRetry() {
        val repairSignals = listOf(
            HidLifecycleSignal.PermissionMissing to ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
            HidLifecycleSignal.ProfileUnregistered to ConnectionIssueCode.HID_PROFILE_UNREGISTERED,
            HidLifecycleSignal.ProfileRegistrationFailed to ConnectionIssueCode.HID_PROFILE_REGISTRATION_FAILED,
        )

        repairSignals.forEach { (signal, expectedIssue) ->
            val decision = resolveHidLifecycleTransition(
                current = HidLifecycle.Opening,
                signal = signal,
                desired = HidDesiredConnectionState.Connected,
            )
            assertEquals(HidFailureClass.RepairRequired, decision.failureClass)
            assertEquals(expectedIssue, decision.issueCode)
            assertEquals(HidRetryDisposition.BlockedUntilRepair, decision.retryDisposition)
        }

        val pairing = HidState(
            failureClass = HidFailureClass.RepairRequired,
            issueCode = ConnectionIssueCode.HOST_UNPAIRED,
            retry = HidRetryMetadata(HidRetryDisposition.BlockedUntilRepair),
        )
        assertFalse(pairing.autoReconnectEnabled)
    }

    @Test
    fun bluetoothTransportUnavailableIsTransientAndSuspended() {
        val decision = resolveHidLifecycleTransition(
            current = HidLifecycle.Connected,
            signal = HidLifecycleSignal.BluetoothUnavailable,
            desired = HidDesiredConnectionState.Connected,
        )

        assertEquals(HidFailureClass.Transient, decision.failureClass)
        assertEquals(ConnectionIssueCode.BLUETOOTH_DISABLED, decision.issueCode)
        assertEquals(HidRetryDisposition.Suspended, decision.retryDisposition)
    }

    @Test
    fun transientDisconnectRetriesOnlyWhenConnectionIsStillDesired() {
        val reconnect = resolveHidLifecycleTransition(
            current = HidLifecycle.Connected,
            signal = HidLifecycleSignal.Disconnected,
            desired = HidDesiredConnectionState.Connected,
        )
        val manualDisconnect = resolveHidLifecycleTransition(
            current = HidLifecycle.Connected,
            signal = HidLifecycleSignal.Disconnected,
            desired = HidDesiredConnectionState.Disconnected,
        )

        assertEquals(HidFailureClass.Transient, reconnect.failureClass)
        assertEquals(ConnectionIssueCode.CONNECT_BACKOFF, reconnect.issueCode)
        assertEquals(HidRetryDisposition.Scheduled, reconnect.retryDisposition)
        assertEquals(HidFailureClass.None, manualDisconnect.failureClass)
        assertEquals(null, manualDisconnect.issueCode)
        assertEquals(HidRetryDisposition.Idle, manualDisconnect.retryDisposition)
    }

    @Test
    fun retryMetadataRejectsImpossibleValues() {
        assertThrows(IllegalArgumentException::class.java) {
            HidRetryMetadata(attempt = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HidRetryMetadata(nextAttemptAtMillis = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HidRetryMetadata(
                disposition = HidRetryDisposition.BlockedUntilRepair,
                nextAttemptAtMillis = 1L,
            )
        }
        assertTrue(HidRetryMetadata(HidRetryDisposition.Scheduled, 1, 2_000L).attempt == 1)
    }
}
