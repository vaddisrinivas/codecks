package io.codecks

import io.codecks.domain.connection.ConnectionIssueCode
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReconnectPolicyTest {
    @Test
    fun transientBackoffUsesInjectedClockAndStaysBounded() {
        val clock = FakeHidClock(10_000L)
        val policy = HidReconnectPolicy(clock)
        val expectedDelays = listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L, 30_000L)

        expectedDelays.forEachIndexed { index, delay ->
            val retry = policy.scheduledRetry(attempt = index + 1)
            assertEquals(HidRetryDisposition.Scheduled, retry.disposition)
            assertEquals(10_000L + delay, retry.nextAttemptAtMillis)
        }

        clock.now = 50_000L
        assertEquals(50_000L, policy.nowMillis())
    }

    @Test
    fun permanentFailureNeverSchedulesRetry() {
        val retry = HidReconnectPolicy(FakeHidClock(10_000L)).scheduledRetry(
            attempt = 4,
            failureClass = HidFailureClass.RepairRequired,
        )

        assertEquals(HidRetryDisposition.BlockedUntilRepair, retry.disposition)
        assertEquals(0L, retry.nextAttemptAtMillis)
    }

    @Test
    fun manualRetryOnlyClearsTransientBackoff() {
        val transient = HidState(
            desiredConnectionState = HidDesiredConnectionState.Connected,
            bluetoothPower = HidBluetoothPower.On,
            failureClass = HidFailureClass.Transient,
            issueCode = ConnectionIssueCode.CONNECT_BACKOFF,
            retry = HidRetryMetadata(HidRetryDisposition.Scheduled, attempt = 3, nextAttemptAtMillis = 40_000L),
        )
        val retried = reduceHidSystemEvent(transient, HidSystemEvent.ManualRetry)
        assertTrue(retried.maintainConnection)
        assertEquals(HidRetryDisposition.Attempting, retried.state.retry.disposition)
        assertEquals(0L, retried.state.retry.nextAttemptAtMillis)

        permanentIssues.forEach { issue ->
            val blocked = transient.copy(
                failureClass = HidFailureClass.RepairRequired,
                issueCode = issue,
                retry = HidRetryMetadata(HidRetryDisposition.BlockedUntilRepair),
            )
            val decision = reduceHidSystemEvent(blocked, HidSystemEvent.ManualRetry)
            assertEquals(blocked, decision.state)
            assertFalse(decision.maintainConnection)
        }
    }

    @Test
    fun writesReconnectPolicyEvidence() {
        val policy = HidReconnectPolicy(FakeHidClock(100_000L))
        val cases = JSONArray()
        (1..6).forEach { attempt ->
            val retry = policy.scheduledRetry(attempt)
            cases.put(
                JSONObject()
                    .put("case", "transient_attempt_$attempt")
                    .put("disposition", retry.disposition.name)
                    .put("attempt", retry.attempt)
                    .put("nextAttemptAtMillis", retry.nextAttemptAtMillis),
            )
        }
        permanentIssues.forEach { issue ->
            val retry = policy.scheduledRetry(1, failureClass = HidFailureClass.RepairRequired)
            cases.put(
                JSONObject()
                    .put("case", issue.persistedCode)
                    .put("disposition", retry.disposition.name)
                    .put("nextAttemptAtMillis", retry.nextAttemptAtMillis),
            )
        }
        val output = evidenceDirectory().resolve("reconnect_policy_cases.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(cases.toString(2))

        assertTrue(output.isFile)
        assertEquals(11, cases.length())
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/HID-04")
    }

    private class FakeHidClock(var now: Long) : HidClock {
        override fun nowMillis(): Long = now
    }

    private companion object {
        val permanentIssues = listOf(
            ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
            ConnectionIssueCode.HOST_UNPAIRED,
            ConnectionIssueCode.SSH_AUTH_FAILED,
            ConnectionIssueCode.SSH_HOST_KEY_MISMATCH,
            ConnectionIssueCode.MAC_TOOL_MISSING,
        )
    }
}
