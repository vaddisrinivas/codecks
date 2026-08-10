package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.CommandOrigin
import io.codecks.domain.device.DeviceId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.codecks.domain.assurance.ActionAssuranceReceipt
import io.codecks.domain.assurance.AssuranceComponentReceipt
import io.codecks.domain.assurance.AssuranceComponentStatus
import io.codecks.domain.assurance.AssuranceReceiptStatus
import io.codecks.domain.assurance.AssuranceSource
import io.codecks.domain.assurance.AssuranceTransport

class AutomationExecutionEngineTest {
    @Test
    fun ruleDispatchRetainsCommonReceiptAndRestampsSource() = runBlocking {
        val runner = object : ActionRunner {
            override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult =
                result(spec, ActionResultStatus.Succeeded).copy(
                    assuranceReceipt = assuranceReceipt(spec.id),
                )
        }
        val result = AutomationExecutionEngine(runner).run(
            recipe().copy(cleanupDefinition = AutomationCleanupDefinition()).executable(),
            allowDangerous = false,
        ) { true }

        assertEquals(AssuranceSource.Rule, result.assuranceReceipt?.source)
        assertEquals(AssuranceReceiptStatus.Succeeded, result.assuranceReceipt?.status)
    }
    @Test
    fun failureRunsDeclaredCleanup() = runBlocking {
        val calls = mutableListOf<String>()
        val runner = recordingRunner(calls) { spec ->
            if (spec.id == "step") ActionResultStatus.Failed else ActionResultStatus.Succeeded
        }
        val result = AutomationExecutionEngine(runner).run(recipe().executable(), allowDangerous = false) { true }

        assertEquals(listOf("step", "cleanup"), calls)
        assertEquals(ActionResultStatus.Failed, result.status)
    }

    @Test
    fun cancellationStillRunsCleanupAndPropagatesCancellation() = runBlocking {
        val calls = mutableListOf<String>()
        val runner = object : ActionRunner {
            override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult {
                calls += spec.id
                if (spec.id == "step") throw CancellationException("stop")
                return result(spec, ActionResultStatus.Succeeded)
            }
        }

        val failure = runCatching {
            AutomationExecutionEngine(runner).run(recipe().executable(), allowDangerous = false) { true }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(listOf("step", "cleanup"), calls)
    }

    @Test
    fun cleanupFailureFailsOtherwiseSuccessfulRun() = runBlocking {
        val result = AutomationExecutionEngine(
            recordingRunner(mutableListOf()) { spec ->
                if (spec.id == "cleanup") ActionResultStatus.Failed else ActionResultStatus.Succeeded
            },
        ).run(recipe().executable(), allowDangerous = false) { true }

        assertEquals(ActionResultStatus.Failed, result.status)
        assertEquals("cleanup_failed", result.logs)
    }

    private fun recipe(): AutomationRecipe = AutomationRecipe(
        id = "recipe",
        title = "Recipe",
        description = "",
        enabled = false,
        steps = listOf(shell("step", "open -a Notes")),
        cleanupDefinition = AutomationCleanupDefinition(
            action = shell("cleanup", "open -a Finder"),
            runAfter = AutomationCleanupTrigger.entries.toSet(),
            undoGuarantee = AutomationUndoGuarantee.GUARANTEED,
        ),
    )

    private fun AutomationRecipe.executable(): ExecutableAutomation = ExecutableAutomation(
        recipe = this,
        sourceRecipeId = id,
        revision = revisionFingerprint(),
        macIdentity = "automation-identity-v1:" + "a".repeat(64),
        targetId = DeviceId("mac"),
    )

    private fun shell(id: String, command: String): ActionSpec.ShellCommand =
        ActionSpec.ShellCommand(
            id = id,
            title = id,
            command = command,
            commandOrigin = CommandOrigin.Bundled,
        )

    private fun recordingRunner(
        calls: MutableList<String>,
        status: (ActionSpec) -> ActionResultStatus,
    ): ActionRunner = object : ActionRunner {
        override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult {
            calls += spec.id
            return result(spec, status(spec))
        }
    }

    private fun result(spec: ActionSpec, status: ActionResultStatus): ActionResult =
        ActionResult(
            actionId = spec.id,
            title = spec.title,
            status = status,
            message = status.name,
        )

    private fun assuranceReceipt(subject: String) = ActionAssuranceReceipt(
        receiptId = "receipt",
        operationId = "operation",
        idempotencyKey = "idempotency",
        subjectId = subject,
        revision = "revision",
        source = AssuranceSource.Deck,
        transport = AssuranceTransport.Ssh,
        status = AssuranceReceiptStatus.Succeeded,
        preflight = emptyList(),
        components = listOf(
            AssuranceComponentReceipt(
                "ssh",
                AssuranceComponentStatus.Succeeded,
                "completed",
                retryable = false,
                undoAvailable = false,
            ),
        ),
        retryToken = null,
        undoToken = null,
        completedAtMillis = 1L,
    )
}
