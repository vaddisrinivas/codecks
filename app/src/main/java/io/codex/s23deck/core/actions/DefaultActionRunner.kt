package io.codex.s23deck.core.actions

import io.codex.s23deck.data.ActionRepository
import io.codex.s23deck.data.ConnectionRepository
import io.codex.s23deck.data.LocalActionException
import io.codex.s23deck.domain.ActionIcon
import io.codex.s23deck.domain.ActionKind
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.domain.device.Capability
import io.codex.s23deck.domain.device.DeviceRepository
import io.codex.s23deck.domain.device.TransportRegistry
import io.codex.s23deck.domain.execution.ActionExecutor
import io.codex.s23deck.domain.execution.BoundedParallelExecutor
import io.codex.s23deck.domain.execution.ExecutionPlan
import io.codex.s23deck.domain.execution.ExecutionPlanner
import io.codex.s23deck.domain.execution.ExecutionResult
import io.codex.s23deck.domain.execution.ExecutionStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultActionRunner @Inject constructor(
    private val actionRepository: ActionRepository,
    private val connectionRepository: ConnectionRepository,
    private val deviceRepository: DeviceRepository,
    private val transportRegistry: TransportRegistry,
) : ActionRunner {
    override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult {
        if (spec.dangerous && !allowDangerous) {
            return ActionResult(
                actionId = spec.id,
                title = spec.title,
                status = ActionResultStatus.RequiresConfirmation,
                message = "Confirmation required",
            )
        }
        return when (spec) {
            is ActionSpec.DeckActionSpec -> runDeckAction(spec)
            is ActionSpec.CatalogAction -> {
                val action = actionRepository.allActions().firstOrNull { it.id == spec.id }
                    ?: return spec.failure("Action not found")
                runDeckAction(ActionSpec.DeckActionSpec(action.copy(dangerous = action.dangerous || spec.dangerous)))
            }
            is ActionSpec.ShellCommand -> runCommandSpec(spec, allowDangerous)
            is ActionSpec.LocalRoute -> spec.failure(LocalActionException(spec.route).message ?: "Open ${spec.route}")
        }
    }

    private suspend fun runDeckAction(spec: ActionSpec.DeckActionSpec): ActionResult {
        val action = spec.action
        if (action.kind == ActionKind.Local) {
            return action.failure(LocalActionException(action.route.orEmpty()).message ?: "Open ${action.route.orEmpty()}")
        }
        return executeSsh(
            spec = spec,
            command = action.command,
            catalogActionId = action.id.takeIf { action.command == null },
        )
    }

    private suspend fun runCommandSpec(spec: ActionSpec.ShellCommand, allowDangerous: Boolean): ActionResult {
        RawCommandPolicy.firstViolation(spec.command)?.let { reason ->
            return spec.failure("Command blocked: $reason")
        }
        if (spec.trustLevel == ShellTrustLevel.Generated) {
            RawCommandPolicy.firstAllowlistViolation(spec.command)?.let { reason ->
                return spec.failure("Generated command is outside Codecks safe templates: $reason")
            }
            if (!allowDangerous) {
                return ActionResult(
                    actionId = spec.id,
                    title = spec.title,
                    status = ActionResultStatus.RequiresConfirmation,
                    message = "Review generated command before running",
                )
            }
        }
        return executeSsh(spec = spec, command = spec.command, catalogActionId = null)
    }

    private suspend fun executeSsh(
        spec: ActionSpec,
        command: String?,
        catalogActionId: String?,
    ): ActionResult {
        val plan = ExecutionPlanner(deviceRepository, transportRegistry).plan(
            actionId = spec.id,
            selector = spec.targetSelector,
            requiredCapabilities = setOf(Capability("ssh")),
            steps = listOfNotNull(catalogActionId ?: command),
        )
        if (plan.targets.isEmpty()) {
            return spec.failure("No compatible Mac target is ready")
        }
        val executor = BoundedParallelExecutor(
            ActionExecutor { target, executionPlan ->
                val targetId = target.device.id.value
                if (catalogActionId != null) {
                    connectionRepository.runActionOnTarget(targetId, catalogActionId, spec.dangerous).getOrThrow()
                } else {
                    val shellCommand = executionPlan.steps.firstOrNull().orEmpty()
                    connectionRepository.runCommandOnTarget(targetId, shellCommand).getOrThrow()
                }
            },
        )
        val result = executor.execute(plan)
        return spec.toActionResult(result)
    }
}

private fun DeckAction.success(message: String): ActionResult =
    ActionResult(id, label, ActionResultStatus.Succeeded, message.ifBlank { "$label completed" })

private fun DeckAction.failure(message: String): ActionResult =
    ActionResult(id, label, ActionResultStatus.Failed, message)

private fun ActionSpec.success(message: String): ActionResult =
    ActionResult(id, title, ActionResultStatus.Succeeded, message.ifBlank { "$title completed" })

private fun ActionSpec.failure(message: String): ActionResult =
    ActionResult(id, title, ActionResultStatus.Failed, message)

private fun ActionSpec.toActionResult(result: ExecutionResult): ActionResult {
    val status = if (result.isSuccess) {
        ActionResultStatus.Succeeded
    } else {
        ActionResultStatus.Failed
    }
    val targetCount = result.results.size
    val message = when {
        result.isSuccess && targetCount == 1 -> result.results.first().output.orEmpty().ifBlank { "$title completed" }
        result.isSuccess -> "$title completed on $targetCount targets"
        result.isPartialSuccess -> "$title completed on ${result.succeeded.size}/${result.results.size} targets"
        else -> result.failed.firstOrNull()?.status?.failureMessage() ?: "$title failed"
    }
    val logs = result.results.joinToString(separator = "\n") { perDevice ->
        val target = perDevice.deviceId.value
        val statusText = when (val statusValue = perDevice.status) {
            ExecutionStatus.Succeeded -> "ok"
            ExecutionStatus.Canceled -> "canceled"
            is ExecutionStatus.Failed -> "failed: ${statusValue.message}"
            is ExecutionStatus.TimedOut -> "timed out after ${statusValue.timeoutMillis}ms"
            is ExecutionStatus.Incompatible -> "missing ${statusValue.missingCapabilities.joinToString { it.value }}"
        }
        val output = perDevice.output?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        "$target: $statusText$output"
    }
    return ActionResult(
        actionId = id,
        title = title,
        status = status,
        message = message,
        logs = logs,
        target = if (targetCount == 1) result.results.first().deviceId.value else "$targetCount targets",
    )
}

private fun ExecutionStatus.failureMessage(): String? = when (this) {
    ExecutionStatus.Succeeded -> null
    ExecutionStatus.Canceled -> "Action canceled"
    is ExecutionStatus.Failed -> message
    is ExecutionStatus.TimedOut -> "Timed out after ${timeoutMillis}ms"
    is ExecutionStatus.Incompatible -> "Target missing ${missingCapabilities.joinToString { it.value }}"
}

fun ActionSpec.toDeckAction(): DeckAction = when (this) {
    is ActionSpec.DeckActionSpec -> action
    is ActionSpec.CatalogAction -> DeckAction(
        id,
        title,
        ActionKind.Ssh,
        ActionIcon.Apps,
        dangerous = dangerous,
        targetSelector = targetSelector,
    )
    is ActionSpec.ShellCommand -> DeckAction(
        id,
        title,
        ActionKind.Ssh,
        ActionIcon.Apps,
        command = command,
        dangerous = dangerous,
        targetSelector = targetSelector,
    )
    is ActionSpec.LocalRoute -> DeckAction(
        id,
        title,
        ActionKind.Local,
        ActionIcon.Apps,
        route = route,
        targetSelector = targetSelector,
    )
}
