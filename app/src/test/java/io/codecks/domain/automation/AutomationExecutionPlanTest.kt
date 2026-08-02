package io.codecks.domain.automation

import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.ShellTrustLevel
import io.codecks.core.actions.commandRevision
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.device.TargetSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationExecutionPlanTest {
    @Test
    fun planIsNormalizedDeterministicAndHasOneAssertionPerAction() {
        val first = recipe(
            commands = listOf(
                "  open 'https://example.com'\r\n",
                "caffeinate -u -t 30",
            ),
        )
        val second = recipe(
            commands = listOf(
                "open 'https://example.com'",
                "caffeinate -u -t 30",
            ),
        )

        val firstPlan = AutomationExecutionPlanCompiler.compile(first).getOrThrow()
        val secondPlan = AutomationExecutionPlanCompiler.compile(second).getOrThrow()

        assertEquals(secondPlan.planHash, firstPlan.planHash)
        assertEquals(firstPlan.actions.size, firstPlan.assertions.size)
        assertTrue(firstPlan.assertions.all { it.expectedExitCode == 0 })
        assertEquals("open 'https://example.com'", firstPlan.actions.first().command)
    }

    @Test
    fun planSnapshotCannotBeMutatedThroughCallerOrReturnedList() {
        val mutableSteps = mutableListOf(reviewedGeneratedStep("one", "open -a Notes"))
        val plan = AutomationExecutionPlanCompiler.compile(recipeFromSteps(mutableSteps)).getOrThrow()
        mutableSteps += reviewedGeneratedStep("two", "open -a Calendar")

        assertEquals(1, plan.actions.size)
        val mutation = runCatching {
            @Suppress("UNCHECKED_CAST")
            (plan.actions as MutableList<NormalizedAutomationAction>).clear()
        }
        assertTrue(mutation.isFailure)
        assertEquals(1, plan.actions.size)
    }

    @Test
    fun commandOrReviewChangeChangesHashAndStaleReviewFailsClosed() {
        val original = recipe(listOf("open -a Notes"))
        val changed = recipe(listOf("open -a Calendar"))
        val originalPlan = AutomationExecutionPlanCompiler.compile(original).getOrThrow()
        val changedPlan = AutomationExecutionPlanCompiler.compile(changed).getOrThrow()
        val stale = changed.copy(
            steps = listOf(
                (changed.steps.single() as ActionSpec.ShellCommand).copy(
                    review = (original.steps.single() as ActionSpec.ShellCommand).review,
                ),
            ),
        )

        assertNotEquals(originalPlan.planHash, changedPlan.planHash)
        assertTrue(AutomationExecutionPlanCompiler.compile(stale).isFailure)
    }

    @Test
    fun unsupportedExecutableShapeIsBlockedInsteadOfLosingAssertion() {
        val recipe = AutomationRecipe(
            id = "unsupported",
            title = "Unsupported",
            description = "",
            enabled = false,
            steps = listOf(ActionSpec.LocalRoute("route", "Route", "settings")),
        )

        val result = AutomationExecutionPlanCompiler.compile(recipe)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Unsupported"))
    }

    @Test
    fun toolRequirementsCoverEveryPipelineCommandAndCleanup() {
        val cleanup = ActionSpec.ShellCommand(
            id = "cleanup",
            title = "Cleanup",
            command = "osascript -e 'return 1'",
            commandOrigin = CommandOrigin.Bundled,
        )
        val candidate = AutomationRecipe(
            id = "tools",
            title = "Tools",
            description = "",
            enabled = false,
            steps = listOf(
                ActionSpec.ShellCommand(
                    id = "pipeline",
                    title = "Pipeline",
                    command = "printf x | pbcopy && open -a Notes",
                    commandOrigin = CommandOrigin.Bundled,
                ),
            ),
            cleanupDefinition = AutomationCleanupDefinition(
                action = cleanup,
                runAfter = setOf(AutomationCleanupTrigger.FAILURE),
            ),
        )

        assertEquals(setOf("pbcopy", "open", "osascript"), candidate.requiredCommandTools())
        assertEquals(
            setOf("permission.accessibility"),
            candidate.requiredPermissions(),
        )
    }

    private fun recipe(commands: List<String>): AutomationRecipe =
        recipeFromSteps(commands.mapIndexed { index, command ->
            reviewedGeneratedStep("step-$index", command)
        })

    private fun recipeFromSteps(steps: List<ActionSpec>): AutomationRecipe =
        AutomationRecipe(
            id = "normalized",
            title = "Normalized",
            description = "",
            enabled = false,
            trigger = AutomationTrigger.Manual,
            steps = steps,
            safety = AutomationSafety(false),
        )

    private fun reviewedGeneratedStep(id: String, command: String): ActionSpec.ShellCommand {
        val normalized = command.replace("\r\n", "\n").replace('\r', '\n').trim()
        val revision = commandRevision(
            command = normalized,
            targetSelector = TargetSelector.CurrentDevice,
            origin = CommandOrigin.AiGenerated,
            dangerous = false,
        )
        return ActionSpec.ShellCommand(
            id = id,
            title = id,
            command = command,
            trustLevel = ShellTrustLevel.Generated,
            dangerous = false,
            targetSelector = TargetSelector.CurrentDevice,
            commandOrigin = CommandOrigin.AiGenerated,
            review = CommandReview(reviewedRevision = revision),
        )
    }
}
