package io.codecks

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HidLifecycleTransitionTest {
    @Test
    fun everyStateSignalAndDesiredStateHasOneDeterministicTransition() {
        HidLifecycle.entries.forEach { current ->
            HidLifecycleSignal.entries.forEach { signal ->
                HidDesiredConnectionState.entries.forEach { desired ->
                    val first = resolveHidLifecycleTransition(current, signal, desired)
                    val second = resolveHidLifecycleTransition(current, signal, desired)

                    assertEquals(first, second)
                    assertNotNull(first.lifecycle)
                    if (first.failureClass == HidFailureClass.RepairRequired) {
                        assertNotNull(first.issueCode)
                        assertEquals(HidRetryDisposition.BlockedUntilRepair, first.retryDisposition)
                    }
                    if (first.lifecycle == HidLifecycle.Connected) {
                        assertEquals(HidFailureClass.None, first.failureClass)
                        assertEquals(null, first.issueCode)
                        assertEquals(HidRetryDisposition.Idle, first.retryDisposition)
                    }
                }
            }
        }
    }

    @Test
    fun illegalUnknownSignalCannotDegradeConnectedState() {
        val decision = resolveHidLifecycleTransition(
            current = HidLifecycle.Connected,
            signal = HidLifecycleSignal.Unknown,
            desired = HidDesiredConnectionState.Connected,
        )

        assertEquals(
            HidTransitionDecision(
                lifecycle = HidLifecycle.Connected,
                failureClass = HidFailureClass.None,
                issueCode = null,
                retryDisposition = HidRetryDisposition.Idle,
            ),
            decision,
        )
    }

    @Test
    fun writesCompleteTransitionMatrixEvidence() {
        val matrix = JSONArray()
        HidLifecycle.entries.forEach { current ->
            HidLifecycleSignal.entries.forEach { signal ->
                HidDesiredConnectionState.entries.forEach { desired ->
                    val next = resolveHidLifecycleTransition(current, signal, desired)
                    matrix.put(
                        JSONObject()
                            .put("state", current.name)
                            .put("trigger", signal.name)
                            .put("desired", desired.name)
                            .put("nextState", next.lifecycle.name)
                            .put("failureClass", next.failureClass.name)
                            .put("issueCode", next.issueCode?.persistedCode ?: JSONObject.NULL)
                            .put("retry", next.retryDisposition.name),
                    )
                }
            }
        }

        val output = evidenceDirectory().resolve("hid_transition_matrix.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(matrix.toString(2))

        val expectedRows = HidLifecycle.entries.size *
            HidLifecycleSignal.entries.size *
            HidDesiredConnectionState.entries.size
        assertEquals(expectedRows, matrix.length())
        assertTrue(output.isFile)
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/HID-01")
    }
}
