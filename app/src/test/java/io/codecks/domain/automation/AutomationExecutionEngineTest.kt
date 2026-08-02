package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.CommandOrigin
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationExecutionEngineTest {
    @Test
    fun failureRunsDeclaredCleanup() = runBlocking {
        val calls = mutableListOf<String>()
        val runner = recordingRunner(calls) { spec ->
            if (spec.id == "step") ActionResultStatus.Failed else ActionResultStatus.Succeeded
        }
        val result = AutomationExecutionEngine(runner).run(recipe(), allowDangerous = false)

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
            AutomationExecutionEngine(runner).run(recipe(), allowDangerous = false)
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
        ).run(recipe(), allowDangerous = false)

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
}
