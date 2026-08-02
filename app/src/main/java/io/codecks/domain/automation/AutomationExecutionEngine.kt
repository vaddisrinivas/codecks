package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.domain.device.DeviceId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Single execution path for user-initiated and background automation runs.
 *
 * Cleanup is part of the recipe contract, not a UI concern. It therefore runs in a
 * non-cancellable context after every declared terminal state.
 */
class AutomationExecutionEngine(
    private val actionRunner: ActionRunner,
) {
    internal suspend fun run(
        executable: ExecutableAutomation,
        allowDangerous: Boolean,
        validatePinned: suspend () -> Boolean,
    ): ActionResult {
        val recipe = executable.recipe
        if (recipe.steps.isEmpty()) return recipe.result(
            status = ActionResultStatus.Failed,
            message = "Recipe has no actions",
        )
        if (!allowDangerous && (recipe.safety.requiresConfirmation || recipe.steps.any { it.dangerous })) {
            return recipe.result(
                status = ActionResultStatus.RequiresConfirmation,
                message = "Trigger matched, but ${recipe.title} needs manual confirmation",
            )
        }

        return try {
            var last = recipe.result(ActionResultStatus.Succeeded, "Automation completed")
            for (step in recipe.steps) {
                if (!validatePinned()) return recipe.result(
                    ActionResultStatus.RequiresReview,
                    "Automation or Mac target changed before dispatch",
                )
                last = actionRunner.run(step, allowDangerous = allowDangerous)
                if (!last.succeeded) {
                    return finish(recipe, last, AutomationCleanupTrigger.FAILURE, validatePinned)
                }
            }
            finish(
                recipe,
                last.copy(actionId = recipe.id, title = recipe.title),
                AutomationCleanupTrigger.SUCCESS,
                validatePinned,
            )
        } catch (cancelled: CancellationException) {
            runCleanup(recipe, AutomationCleanupTrigger.CANCEL, validatePinned)
            throw cancelled
        } catch (error: Exception) {
            val primary = recipe.result(
                status = ActionResultStatus.Failed,
                message = "Automation execution failed",
                logs = error::class.simpleName.orEmpty(),
            )
            finish(recipe, primary, AutomationCleanupTrigger.FAILURE, validatePinned)
        }
    }

    private suspend fun finish(
        recipe: AutomationRecipe,
        primary: ActionResult,
        trigger: AutomationCleanupTrigger,
        validatePinned: suspend () -> Boolean,
    ): ActionResult {
        val cleanup = runCleanup(recipe, trigger, validatePinned) ?: return primary.copy(
            actionId = recipe.id,
            title = recipe.title,
        )
        return if (cleanup.succeeded) {
            primary.copy(actionId = recipe.id, title = recipe.title)
        } else {
            recipe.result(
                status = ActionResultStatus.Failed,
                message = "Cleanup failed after ${trigger.persistedCode}",
                logs = "cleanup_failed",
            )
        }
    }

    private suspend fun runCleanup(
        recipe: AutomationRecipe,
        trigger: AutomationCleanupTrigger,
        validatePinned: suspend () -> Boolean,
    ): ActionResult? {
        val definition = recipe.cleanupDefinition
        val action = definition.action ?: return null
        if (trigger !in definition.runAfter) return null
        return withContext(NonCancellable) {
            if (!validatePinned()) {
                return@withContext recipe.result(
                    ActionResultStatus.RequiresReview,
                    "Automation or Mac target changed before cleanup",
                )
            }
            runCatching { actionRunner.run(action, allowDangerous = false) }
                .getOrElse {
                    ActionResult(
                        actionId = action.id,
                        title = action.title,
                        status = ActionResultStatus.Failed,
                        message = "Cleanup execution failed",
                        logs = it::class.simpleName.orEmpty(),
                    )
                }
        }
    }
}

@ConsistentCopyVisibility
data class ExecutableAutomation internal constructor(
    val recipe: AutomationRecipe,
    internal val sourceRecipeId: String,
    internal val revision: String,
    internal val macIdentity: String,
    internal val targetId: DeviceId,
)

private fun AutomationRecipe.result(
    status: ActionResultStatus,
    message: String,
    logs: String = message,
): ActionResult = ActionResult(
    actionId = id,
    title = title,
    status = status,
    message = message,
    logs = logs,
)
