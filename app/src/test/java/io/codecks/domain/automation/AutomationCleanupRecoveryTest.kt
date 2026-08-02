package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.CommandOrigin
import io.codecks.domain.smart.SmartCapability
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationCleanupRecoveryTest {
    @Test
    fun successfulPlanRunsDeclaredCleanupBeforePassing() = runBlocking {
        val recipe = recipe()
        val executed = mutableListOf<String>()
        val receipt = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { action ->
                executed += action.actionId
                AutomationActionProbeResult(0)
            },
        ).run(recipe, preflight(recipe))

        assertEquals(listOf("first", "dependent", "cleanup"), executed)
        assertEquals(AutomationLiveTestTerminalStatus.PASSED, receipt.terminalStatus)
        assertEquals(AutomationLiveTestCleanupCode.SUCCEEDED, receipt.cleanup.outcomeCode)
        assertTrue(receipt.assertions.all {
            it.terminalStatus == AutomationStepTerminalStatus.SUCCEEDED
        })
    }

    @Test
    fun failedDependencyStopsLaterStepsThenRunsDeclaredCleanup() = runBlocking {
        val recipe = recipe()
        val executed = mutableListOf<String>()
        val receipt = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { action ->
                executed += action.actionId
                AutomationActionProbeResult(
                    exitCode = if (action.actionId == "first") 9 else 0,
                )
            },
        ).run(recipe, preflight(recipe))

        assertEquals(listOf("first", "cleanup"), executed)
        assertEquals(
            listOf(
                AutomationStepTerminalStatus.FAILED,
                AutomationStepTerminalStatus.SKIPPED_DEPENDENCY,
            ),
            receipt.assertions.map(AutomationLiveTestAssertion::terminalStatus),
        )
        assertEquals(AutomationLiveTestCleanupCode.SUCCEEDED, receipt.cleanup.outcomeCode)
        assertEquals(AutomationUndoGuarantee.GUARANTEED, receipt.cleanup.undoGuarantee)
        assertEquals(AutomationLiveTestTerminalStatus.ASSERTION_FAILED, receipt.terminalStatus)
        assertFalse(receipt.recoveryRequired)
    }

    @Test
    fun cleanupRunsAfterTimeoutAndCancellation() = runBlocking {
        val recipe = recipe()
        val timeoutExecutions = mutableListOf<String>()
        val timeout = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { action ->
                timeoutExecutions += action.actionId
                AutomationActionProbeResult(0)
            },
            timeoutRunner = AutomationLiveTestTimeoutRunner { _, _ ->
                throw AutomationLiveTestTimedOutException()
            },
        ).run(recipe, preflight(recipe))
        val cancelExecutions = mutableListOf<String>()
        val cancelled = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { action ->
                cancelExecutions += action.actionId
                AutomationActionProbeResult(0)
            },
            timeoutRunner = AutomationLiveTestTimeoutRunner { _, _ ->
                throw CancellationException()
            },
        ).run(recipe, preflight(recipe))

        assertEquals(listOf("cleanup"), timeoutExecutions)
        assertEquals(listOf("cleanup"), cancelExecutions)
        assertEquals(AutomationLiveTestTerminalStatus.TIMED_OUT, timeout.terminalStatus)
        assertEquals(AutomationStepTerminalStatus.TIMED_OUT, timeout.assertions.first().terminalStatus)
        assertEquals(AutomationLiveTestTerminalStatus.CANCELLED, cancelled.terminalStatus)
        assertEquals(AutomationStepTerminalStatus.CANCELLED, cancelled.assertions.first().terminalStatus)
        assertTrue(timeout.cleanup.passed)
        assertTrue(cancelled.cleanup.passed)
    }

    @Test
    fun failedGuaranteedCleanupSetsPersistentRecoveryBlock() = runBlocking {
        val validated = validatedRecipe()
        val preflight = preflight(validated)
        val ready = validated.withPreflightReceipt(preflight)
        val receipt = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { action ->
                AutomationActionProbeResult(exitCode = if (action.actionId == "cleanup") 2 else 0)
            },
        ).run(ready, preflight)

        val recorded = ready.withLiveTestReceipt(receipt)

        assertEquals(AutomationLiveTestTerminalStatus.RECOVERY_REQUIRED, receipt.terminalStatus)
        assertTrue(receipt.recoveryRequired)
        assertTrue(recorded.recoveryRequired)
        assertEquals(AutomationStage.PREFLIGHT_PASSED, recorded.stage)
        assertNull(recorded.gateStamp?.liveTestReceiptId)
        assertFalse(recorded.hasCurrentRevisionGateForExecution())
        val forgedClear = recorded.copy(recoveryRequired = false, enabled = true)
            .enforceRevisionGate(previous = recorded)
        assertTrue(forgedClear.recoveryRequired)
        assertFalse(forgedClear.enabled)
    }

    @Test
    fun cleanupSchemaRequiresActionTriggersAndExplicitUndoGuarantee() {
        assertTrue(
            runCatching {
                AutomationCleanupDefinition(
                    action = cleanupAction(),
                    runAfter = emptySet(),
                    undoGuarantee = AutomationUndoGuarantee.GUARANTEED,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                AutomationCleanupDefinition(
                    action = null,
                    runAfter = setOf(AutomationCleanupTrigger.FAILURE),
                    undoGuarantee = AutomationUndoGuarantee.NONE,
                )
            }.isFailure,
        )
        assertEquals(AutomationUndoGuarantee.NONE, AutomationCleanupDefinition().undoGuarantee)
    }

    private fun validatedRecipe(): AutomationRecipe {
        val draft = recipe().enforceRevisionGate(previous = null)
        return draft.withValidationResult(
            ActionResult(
                actionId = draft.id,
                title = draft.title,
                status = ActionResultStatus.Succeeded,
                message = "validated",
                logs = "validated",
                timestampMillis = 100L,
            ),
            draft.revisionFingerprint(),
        )
    }

    private fun recipe(): AutomationRecipe =
        AutomationRecipe(
            id = "cleanup-recipe",
            title = "Cleanup recipe",
            description = "",
            enabled = false,
            steps = listOf(
                bundledAction("first", "open -a Notes"),
                bundledAction("dependent", "open -a Calendar"),
            ),
            cleanupDefinition = AutomationCleanupDefinition(
                action = cleanupAction(),
                runAfter = AutomationCleanupTrigger.entries.toSet(),
                undoGuarantee = AutomationUndoGuarantee.GUARANTEED,
            ),
        )

    private fun cleanupAction(): ActionSpec.ShellCommand =
        bundledAction("cleanup", "open -a Finder")

    private fun bundledAction(id: String, command: String): ActionSpec.ShellCommand =
        ActionSpec.ShellCommand(
            id = id,
            title = id,
            command = command,
            commandOrigin = CommandOrigin.Bundled,
        )

    private fun preflight(recipe: AutomationRecipe): AutomationPreflightReceipt =
        AutomationPreflightReceipt(
            recipeRevision = recipe.revisionFingerprint(),
            checkedAtMillis = 200L,
            macIdentity = "opaque-host-version",
            targetId = "current",
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            checks = listOf(automationConnectionPreflightCheck(null, 200L)),
            commandTools = setOf("open"),
            commandPaths = emptySet(),
            permissionSnapshot = emptySet(),
            requiredCheckCodes = setOf(AutomationCapabilityCodes.Connection),
        )
}
