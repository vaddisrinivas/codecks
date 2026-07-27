package io.codecks.core.reactive

import io.codecks.HidCommand
import io.codecks.HidRepository
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.commandRevision
import io.codecks.core.actions.dangerousConfirmationRevision
import io.codecks.data.ActionRepository
import io.codecks.domain.CommandReview
import io.codecks.domain.DeckAction
import io.codecks.domain.ExecutionAuthorization
import io.codecks.domain.ActionKind
import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.InMemoryReactiveReceiptStore
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveActionExecutor
import io.codecks.domain.reactive.ReactiveActionReceipt
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveAuthorization
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveExecutionOutcome
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.SharedHidCommand
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.newReactiveReceiptId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultReactiveActionExecutor @Inject constructor(
    private val actionRepository: ActionRepository,
    private val actionRunner: ActionRunner,
    private val hidRepository: HidRepository,
    private val receiptStore: InMemoryReactiveReceiptStore = InMemoryReactiveReceiptStore(),
    private val deviceRepository: DeviceRepository? = null,
) : ReactiveActionExecutor {

    override suspend fun execute(
        control: ReactiveControl,
        authorization: ReactiveAuthorization,
        nowMillis: Long,
        currentState: MacStateSnapshot?,
    ): ReactiveExecutionOutcome {
        if (control.expiresAtMillis < nowMillis) {
            return ReactiveExecutionOutcome(ReactiveActionResult.Expired)
        }
        currentState?.let { state ->
            if (control.stateRevision != state.snapshotRevision) {
                return ReactiveExecutionOutcome(ReactiveActionResult.Failed("stale_state_revision", retryable = false))
            }
            val available = state.capabilities
                .filter { it.availability == io.codecks.domain.reactive.CapabilityAvailability.Available }
                .map { it.capability }
                .toSet()
            if (!control.requiredCapabilities.all(available::contains)) {
                return ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("capability_unavailable"))
            }
        }
        return when (val action = control.action) {
            is ReactiveAction.ExistingCatalog -> executeCatalog(control, authorization, nowMillis, action.actionId, currentState)
            is ReactiveAction.Hid -> executeHid(control, nowMillis, action.command)
            is ReactiveAction.Composite -> executeComposite(control, authorization, nowMillis, action.actions, currentState)
            is ReactiveAction.Helper -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("helper_not_implemented"))
            is ReactiveAction.BundledSshFallback -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("bundled_ssh_fallback_not_implemented"))
            is ReactiveAction.ChangeMode -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("mode_change_requires_viewmodel"))
        }
    }

    private suspend fun executeCatalog(
        control: ReactiveControl,
        authorization: ReactiveAuthorization,
        nowMillis: Long,
        actionId: String,
        currentState: MacStateSnapshot?,
    ): ReactiveExecutionOutcome {
        val action = actionRepository.allActions().firstOrNull { it.id == actionId }
            ?: return ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("catalog_action_missing"))

        val resolvedActionRevision = action.reactiveActionRevision()
        if (control.actionRevision != resolvedActionRevision) {
            return ReactiveExecutionOutcome(ReactiveActionResult.Failed("stale_action_revision", retryable = false))
        }
        if (currentState != null && !action.targetSelector.matches(currentState.macId.value, deviceRepository)) {
            return ReactiveExecutionOutcome(ReactiveActionResult.Failed("target_changed", retryable = false))
        }

        if (action.dangerous && authorization.confirmedActionRevision != resolvedActionRevision) {
            return ReactiveExecutionOutcome(
                ReactiveActionResult.RequiresConfirmation(
                    actionRevision = resolvedActionRevision,
                    title = action.confirmationTitle ?: "Confirm ${action.label}",
                    body = action.confirmationBody ?: (action.riskReason ?: "This action needs explicit confirmation."),
                ),
            )
        }

        val effectiveAction = action.withReviewedRevisionIfAuthorized(
            resolvedActionRevision,
            authorization.reviewedActionRevision,
        )
        val needsReview = effectiveAction.kind == ActionKind.Ssh &&
            effectiveAction.commandOrigin != io.codecks.domain.CommandOrigin.Bundled &&
            effectiveAction.commandRevision() != null &&
            effectiveAction.commandReview.reviewedRevision != effectiveAction.commandRevision()
        if (needsReview) {
            return ReactiveExecutionOutcome(
                ReactiveActionResult.RequiresReview(
                    actionRevision = resolvedActionRevision,
                    reason = effectiveAction.riskReason ?: "Review this command before running",
                ),
            )
        }

        val result = actionRunner.run(
            spec = ActionSpec.DeckActionSpec(effectiveAction),
            authorization = ExecutionAuthorization(
                dangerousRevisionConfirmed = if (authorization.confirmedActionRevision == resolvedActionRevision) {
                    ActionSpec.DeckActionSpec(effectiveAction).dangerousConfirmationRevision()
                } else {
                    null
                },
            ),
        )

        return when (result.status) {
            io.codecks.core.actions.ActionResultStatus.Succeeded -> {
                val receipt = ReactiveActionReceipt(
                    id = newReactiveReceiptId(),
                    controlId = control.id,
                    actionRevision = resolvedActionRevision,
                    completedAtMillis = result.timestampMillis.takeIf { it > 0 } ?: nowMillis,
                    result = ReactiveActionResult.Succeeded("catalog_action_succeeded"),
                    undo = null,
                    expiresAtMillis = null,
                    metadata = mapOf(
                        "actionId" to action.id,
                        "title" to action.label,
                        "message" to result.message,
                    ),
                )
                receiptStore.record(receipt)
                ReactiveExecutionOutcome(result = receipt.result, receipt = receipt)
            }
            io.codecks.core.actions.ActionResultStatus.RequiresConfirmation -> ReactiveExecutionOutcome(
                ReactiveActionResult.RequiresConfirmation(
                    actionRevision = resolvedActionRevision,
                    title = action.confirmationTitle ?: "Confirm ${action.label}",
                    body = action.confirmationBody ?: (action.riskReason ?: "This action needs explicit confirmation."),
                ),
            )
            io.codecks.core.actions.ActionResultStatus.RequiresReview -> ReactiveExecutionOutcome(
                ReactiveActionResult.RequiresReview(
                    actionRevision = resolvedActionRevision,
                    reason = result.message,
                ),
            )
            io.codecks.core.actions.ActionResultStatus.Failed -> ReactiveExecutionOutcome(
                ReactiveActionResult.Failed(
                    errorCode = "catalog_action_failed",
                    retryable = true,
                ),
            )
        }
    }

    private fun executeHid(
        control: ReactiveControl,
        nowMillis: Long,
        command: SharedHidCommand,
    ): ReactiveExecutionOutcome {
        val hidState = hidRepository.state.value
        if (!hidState.isConnected) {
            return ReactiveExecutionOutcome(
                ReactiveActionResult.Failed(
                    errorCode = "hid_not_connected",
                    retryable = true,
                ),
            )
        }
        return runCatching {
            hidRepository.send(command.toHidCommand())
            val receipt = ReactiveActionReceipt(
                id = newReactiveReceiptId(),
                controlId = control.id,
                actionRevision = control.actionRevision,
                completedAtMillis = nowMillis,
                result = ReactiveActionResult.Succeeded("hid_command_sent"),
                undo = null,
                expiresAtMillis = null,
                metadata = mapOf(
                    "actionId" to (control.action as? ReactiveAction.Hid)?.command?.name.orEmpty(),
                    "hidCommand" to command.name,
                ),
            )
            receiptStore.record(receipt)
            ReactiveExecutionOutcome(result = receipt.result, receipt = receipt)
        }.getOrElse {
            ReactiveExecutionOutcome(
                ReactiveActionResult.Failed(
                    errorCode = "hid_command_failed",
                    retryable = true,
                ),
            )
        }
    }

    private suspend fun executeComposite(
        control: ReactiveControl,
        authorization: ReactiveAuthorization,
        nowMillis: Long,
        actions: List<ReactiveAction>,
        currentState: MacStateSnapshot?,
    ): ReactiveExecutionOutcome {
        var lastOutcome: ReactiveExecutionOutcome = ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("composite_empty"))
        for (action in actions) {
            val stepControl = control.copy(action = action)
            lastOutcome = execute(stepControl, authorization, nowMillis, currentState)
            if (lastOutcome.result !is ReactiveActionResult.Succeeded) {
                return lastOutcome
            }
        }
        return lastOutcome
    }
}

private suspend fun TargetSelector.matches(currentMacId: String, deviceRepository: DeviceRepository?): Boolean = when (this) {
    TargetSelector.CurrentDevice -> true
    TargetSelector.AskAtRunTime -> true
    TargetSelector.AllCompatibleDevices -> true
    is TargetSelector.SpecificDevice -> deviceId.value == currentMacId
    is TargetSelector.DeviceGroup -> deviceRepository?.groups()
        ?.firstOrNull { it.id == groupId }
        ?.memberIds
        ?.any { it.value == currentMacId }
        ?: false
}

private fun DeckAction.withReviewedRevisionIfAuthorized(
    resolvedActionRevision: ActionRevision,
    reviewedActionRevision: ActionRevision?,
): DeckAction {
    if (reviewedActionRevision != resolvedActionRevision) return this
    val actualRevision = commandRevision() ?: return this
    return copy(
        commandReview = CommandReview(
            reviewedRevision = actualRevision,
            checkedRevision = commandReview.checkedRevision,
        ),
    )
}

private fun SharedHidCommand.toHidCommand(): HidCommand = when (this) {
    SharedHidCommand.BrowserBack -> HidCommand.BrowserBack
    SharedHidCommand.BrowserForward -> HidCommand.BrowserForward
    SharedHidCommand.Reload -> HidCommand.Reload
    SharedHidCommand.NewTab -> HidCommand.NewDocument
    SharedHidCommand.Enter -> HidCommand.Enter
    SharedHidCommand.CommandEnter -> HidCommand.CommandEnter
    SharedHidCommand.MediaPlayPause -> HidCommand.MediaPlayPause
}
