package io.codecks.ui.connection

import io.codecks.domain.connection.MacCapabilityCheckReceipt
import io.codecks.domain.connection.MacCapabilityCheckResult
import io.codecks.domain.connection.MacSetupCapability
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCompletionTest {
    private val now = System.currentTimeMillis()

    @Test
    fun completeRequiresMatchingCurrentSshCapabilitiesAndUserConfirmedHid() {
        val proof = completeProof()

        assertEquals(SetupCompletionEvaluation.COMPLETE, evaluateSetupCompletion(proof))

        val staleSsh = proof.copy(
            sshReceipt = requireNotNull(proof.sshReceipt).copy(setupRevision = "0".repeat(64)),
        )
        assertFalse(evaluateSetupCompletion(staleSsh) is SetupCompletionEvaluation.COMPLETE)

        val missingCapability = proof.copy(
            currentMacCapabilityReceipts = proof.currentMacCapabilityReceipts.dropLast(1),
        )
        assertFalse(evaluateSetupCompletion(missingCapability) is SetupCompletionEvaluation.COMPLETE)

        val unconfirmedHid = proof.copy(
            hidReceipt = requireNotNull(proof.hidReceipt).copy(result = HidTerminalResult.FAILED),
        )
        assertFalse(evaluateSetupCompletion(unconfirmedHid) is SetupCompletionEvaluation.COMPLETE)
    }

    @Test
    fun timeoutCancelAuthKeyAndToolCanNeverPass() {
        val blocked = listOf(
            SshTerminalResult.Timeout,
            SshTerminalResult.Cancelled,
            SshTerminalResult.AuthenticationFailed,
            SshTerminalResult.KeyMissing,
            SshTerminalResult.ToolMissing,
        )
        blocked.forEach { result ->
            val proof = completeProof().let { valid ->
                valid.copy(
                    sshReceipt = requireNotNull(valid.sshReceipt).copy(
                        result = result,
                        confirmedCapabilities = emptySet(),
                        capabilityCheckedAtEpochMs = emptyMap(),
                    ),
                )
            }
            assertTrue(evaluateSetupCompletion(proof) is SetupCompletionEvaluation.REPAIR_REQUIRED)
        }
    }

    @Test
    fun receiptsAreBoundedAndScopedRepairDoesNotMutateSetup() {
        val proof = completeProof()
        assertThrows(IllegalArgumentException::class.java) {
            requireNotNull(proof.sshReceipt).copy(durationMs = 30_001L)
        }

        val before = SetupSnapshotCodec.encode(proof.snapshot)
        val authFailure = proof.copy(
            sshReceipt = requireNotNull(proof.sshReceipt).copy(
                result = SshTerminalResult.AuthenticationFailed,
                confirmedCapabilities = emptySet(),
                capabilityCheckedAtEpochMs = emptyMap(),
            ),
        )
        val result = evaluateSetupCompletion(authFailure)
        assertEquals(
            SetupRepairTarget.Authorize,
            (result as SetupCompletionEvaluation.REPAIR_REQUIRED).target,
        )
        assertEquals(before, SetupSnapshotCodec.encode(proof.snapshot))

        val source = File("src/main/java/io/codecks/ui/connection/SetupCompletion.kt").readText()
        assertFalse(source.contains("setupSnapshotStore.clear"))
        assertFalse(source.contains("password"))
        assertFalse(source.contains("credential"))
    }

    @Test
    fun emptyStaleAndCrossTargetProofsFailClosed() {
        val valid = completeProof()
        val emptyRequired = valid.copy(requiredMacCapabilities = emptySet())
        val stale = valid.copy(
            evaluatedAtEpochMs = now + SETUP_TERMINAL_RECEIPT_MAX_AGE_MS + 1L,
        )
        val otherTarget = valid.copy(currentMacTargetId = "other-target")

        assertFalse(evaluateSetupCompletion(emptyRequired) is SetupCompletionEvaluation.COMPLETE)
        assertFalse(evaluateSetupCompletion(stale) is SetupCompletionEvaluation.COMPLETE)
        assertFalse(evaluateSetupCompletion(otherTarget) is SetupCompletionEvaluation.COMPLETE)
        assertFalse(
            evaluateSetupCompletion(valid.copy(hidReceipt = null)) is SetupCompletionEvaluation.COMPLETE,
        )

        val runtimeSource = File("src/main/java/io/codecks/ui/connection/SetupCompletion.kt").readText()
            .substringAfter("fun evaluateRuntimeSetupCompletion")
            .substringBefore("private fun Long.isFreshAt")
        assertFalse(runtimeSource.contains("HidTerminalReceipt("))
    }

    @Test
    fun readinessSurfacesScopedRepairTarget() {
        val readiness = codecksReadiness(
            connectionHealth = simpleConnectionHealth(connectionReady = true),
            hidHealth = HidHealth(HidHealthKind.Connected, "Connected", "Connected"),
            aiReady = false,
            setupCompletion = SetupCompletionEvaluation.REPAIR_REQUIRED(
                SetupRepairTarget.HidPairing,
                "hid_state_not_confirmed",
            ),
        )

        assertEquals(SetupRepairTarget.HidPairing, readiness.setupRepairTarget)
        assertTrue(readiness.title.contains("Pair a Mac"))
        assertFalse(readiness.coreReady)
    }

    @Test
    fun writesTerminalReceiptEvidence() {
        val rows = JSONArray()
        SshTerminalResult.entries.forEach { result ->
            val proof = completeProof().let { valid ->
                valid.copy(
                    sshReceipt = requireNotNull(valid.sshReceipt).copy(
                        result = result,
                        confirmedCapabilities = if (result == SshTerminalResult.Passed) {
                            valid.requiredMacCapabilities
                        } else {
                            emptySet()
                        },
                        capabilityCheckedAtEpochMs = if (result == SshTerminalResult.Passed) {
                            valid.requiredMacCapabilities.associateWith { now - 150L }
                        } else {
                            emptyMap()
                        },
                    ),
                )
            }
            val evaluation = evaluateSetupCompletion(proof)
            rows.put(
                JSONObject()
                    .put("terminalResult", result.name)
                    .put("complete", evaluation is SetupCompletionEvaluation.COMPLETE)
                    .put(
                        "repairTarget",
                        (evaluation as? SetupCompletionEvaluation.REPAIR_REQUIRED)?.target?.name
                            ?: JSONObject.NULL,
                    ),
            )
        }
        val output = evidenceDirectory().resolve("terminal_receipt_matrix.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(rows.toString(2))

        assertEquals(SshTerminalResult.entries.size, rows.length())
        assertTrue(output.isFile)
    }

    private fun completeProof(): SetupCompletionProof {
        val snapshot = SetupSnapshot(
            receipts = SetupStep.entries.associateWith { step ->
                SetupReceipt(
                    step = step,
                    result = SetupReceiptResult.Passed,
                    completedAtMillis = now - 10L + step.ordinal,
                )
            },
        )
        val required = setOf(
            MacSetupCapability.Reachability,
            MacSetupCapability.HostIdentity,
            MacSetupCapability.Authentication,
            MacSetupCapability.RemoteShell,
        )
        val revision = snapshot.revisionToken()
        val ssh = SshTerminalReceipt(
            setupRevision = revision,
            macTargetId = "mac-target",
            result = SshTerminalResult.Passed,
            attempt = 1,
            durationMs = 500L,
            completedAtEpochMs = now - 100L,
            confirmedCapabilities = required,
            capabilityCheckedAtEpochMs = required.associateWith { now - 150L },
        )
        return SetupCompletionProof(
            snapshot = snapshot,
            sshReceipt = ssh,
            hidReceipt = HidTerminalReceipt(
                setupRevision = revision,
                macTargetId = "mac-target",
                hidHostAddress = "AA:BB",
                result = HidTerminalResult.USER_CONFIRMED,
                completedAtEpochMs = now - 50L,
            ),
            requiredMacCapabilities = required,
            currentMacCapabilityReceipts = required.map { capability ->
                MacCapabilityCheckReceipt(
                    capability = capability,
                    result = MacCapabilityCheckResult.Passed,
                    failureCode = null,
                    checkedAtEpochMs = now - 150L,
                )
            },
            currentMacTargetId = "mac-target",
            currentHidStatus = evaluateBluetoothSetup(
                BluetoothSetupObservation(
                    permissionState = BluetoothPermissionState.Granted,
                    bluetoothEnabled = true,
                    hidRegistered = true,
                    profileRegistrationFailed = false,
                    bondedHostCount = 1,
                    selectedHostIsBonded = true,
                    connectedToSelectedHost = true,
                ),
            ),
            currentHidHostAddress = "AA:BB",
            evaluatedAtEpochMs = now,
        )
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/SET-04")
    }
}
