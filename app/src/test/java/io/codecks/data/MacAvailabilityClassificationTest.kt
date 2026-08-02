package io.codecks.data

import io.codecks.domain.connection.ConnectionIssueCode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacAvailabilityClassificationTest {
    @Test
    fun temporaryTransportHostAndSleepFailuresAreTransient() {
        val expected = mapOf(
            MacAvailabilitySignal.BluetoothTransportLost to ConnectionIssueCode.BLUETOOTH_DISABLED,
            MacAvailabilitySignal.HostTemporarilyUnavailable to ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP,
            MacAvailabilitySignal.MacSleepingOrOffline to ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP,
        )

        expected.forEach { (signal, issue) ->
            val decision = classifyMacAvailability(signal)
            assertEquals(ConnectionRetryClass.Transient, decision.retryClass)
            assertEquals(issue, decision.issueCode)
            assertTrue(decision.automaticRetryAllowed)
            assertFalse(decision.scheduleHealthCheck)
        }
    }

    @Test
    fun permissionPairingIdentityAuthAndToolsRequireRepair() {
        val expected = mapOf(
            MacAvailabilitySignal.PermissionDenied to ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
            MacAvailabilitySignal.HostUnpaired to ConnectionIssueCode.HOST_UNPAIRED,
            MacAvailabilitySignal.AuthenticationFailed to ConnectionIssueCode.SSH_AUTH_FAILED,
            MacAvailabilitySignal.HostKeyMismatch to ConnectionIssueCode.SSH_HOST_KEY_MISMATCH,
            MacAvailabilitySignal.RequiredToolMissing to ConnectionIssueCode.MAC_TOOL_MISSING,
        )

        expected.forEach { (signal, issue) ->
            val decision = classifyMacAvailability(signal)
            assertEquals(ConnectionRetryClass.RepairRequired, decision.retryClass)
            assertEquals(issue, decision.issueCode)
            assertFalse(decision.automaticRetryAllowed)
            assertFalse(decision.scheduleHealthCheck)
        }
    }

    @Test
    fun wakeAndReavailabilityOnlyScheduleControlPathHealthChecks() {
        listOf(
            MacAvailabilitySignal.BluetoothReavailable,
            MacAvailabilitySignal.HostReavailable,
            MacAvailabilitySignal.MacAwake,
        ).forEach { signal ->
            val decision = classifyMacAvailability(signal)
            assertEquals(ConnectionRetryClass.Reavailable, decision.retryClass)
            assertTrue(decision.scheduleHealthCheck)
            assertFalse(decision.automaticRetryAllowed)
        }

        val hidSource = File("src/main/java/io/codecks/HidRepository.kt").readText()
        val inputMethods = hidSource.substring(
            hidSource.indexOf("override fun move"),
            hidSource.indexOf("private fun key"),
        )
        assertFalse(inputMethods.contains("reduceHidSystemEvent"))
        assertFalse(inputMethods.contains("classifyMacAvailability"))
        assertFalse(inputMethods.contains("scheduleHealthCheck"))
    }
}
