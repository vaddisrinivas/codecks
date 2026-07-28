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
import io.codecks.domain.reactive.ReactiveActionInvocation
import io.codecks.domain.reactive.ReactiveActionReceipt
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveAuthorization
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveExecutionOutcome
import io.codecks.domain.reactive.SharedHidCommand
import io.codecks.domain.reactive.ReactiveUndoOutcome
import io.codecks.domain.reactive.ReceiptId
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
        invocation: ReactiveActionInvocation,
    ): ReactiveExecutionOutcome {
        val signature = control.idempotencySignature()
        receiptStore.findByIdempotencyKey(invocation.idempotencyKey)?.let { replay ->
            return if (replay.signature == signature) {
                ReactiveExecutionOutcome(result = replay.receipt.result, receipt = replay.receipt)
            } else {
                ReactiveExecutionOutcome(ReactiveActionResult.Failed("idempotency_key_reused", retryable = false))
            }
        }
        if (invocation.requestedAtMillis + invocation.timeoutMillis < nowMillis) {
            return ReactiveExecutionOutcome(ReactiveActionResult.Expired)
        }
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
            is ReactiveAction.ExistingCatalog -> executeCatalog(control, authorization, nowMillis, invocation, signature, action.actionId, currentState)
            is ReactiveAction.Hid -> executeHid(control, nowMillis, invocation, signature, action.command)
            is ReactiveAction.Composite -> executeComposite(control, authorization, nowMillis, invocation, signature, action.actions, currentState)
            is ReactiveAction.Helper -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("helper_not_implemented"))
            is ReactiveAction.BundledSshFallback -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("bundled_ssh_fallback_not_implemented"))
            is ReactiveAction.ChangeMode -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("mode_change_requires_viewmodel"))
        }
    }

    override suspend fun undo(
        receiptId: ReceiptId,
        nowMillis: Long,
        invocation: ReactiveActionInvocation?,
    ): ReactiveUndoOutcome {
        val receipt = receiptStore.findByReceiptId(receiptId)
            ?: return ReactiveUndoOutcome.Unsupported("receipt_missing")
        val undo = receipt.undo ?: return ReactiveUndoOutcome.Unsupported("undo_not_supported")
        if (undo.expiresAtMillis < nowMillis) return ReactiveUndoOutcome.Expired
        val undoControl = ReactiveControl(
            id = io.codecks.domain.reactive.ControlId("${receipt.controlId.value}_undo"),
            title = undo.label,
            subtitle = null,
            icon = io.codecks.domain.reactive.ReactiveIcon.Generic,
            action = undo.action,
            source = io.codecks.domain.reactive.ReactiveControlSource.UndoReceipt,
            basePriority = 0,
            reason = "undo:${receipt.id.value}",
            requiredCapabilities = emptySet(),
            risk = io.codecks.domain.reactive.ReactiveRisk.Safe,
            reversible = false,
            stateRevision = receipt.metadata["stateRevision"]?.toLongOrNull() ?: 0L,
            actionRevision = receipt.actionRevision,
            expiresAtMillis = undo.expiresAtMillis,
        )
        val outcome = execute(
            control = undoControl,
            authorization = ReactiveAuthorization(),
            nowMillis = nowMillis,
            currentState = null,
            invocation = invocation ?: ReactiveActionInvocation.create(undoControl, nowMillis),
        )
        return when (val result = outcome.result) {
            is ReactiveActionResult.Succeeded -> outcome.receipt
                ?.let(ReactiveUndoOutcome::Succeeded)
                ?: ReactiveUndoOutcome.Failed("undo_receipt_missing", retryable = true)
            is ReactiveActionResult.Failed -> ReactiveUndoOutcome.Failed(result.errorCode, result.retryable)
            is ReactiveActionResult.Unsupported -> ReactiveUndoOutcome.Unsupported(result.reasonCode)
            ReactiveActionResult.Expired -> ReactiveUndoOutcome.Expired
            is ReactiveActionResult.RequiresConfirmation -> ReactiveUndoOutcome.Unsupported("undo_requires_confirmation")
            is ReactiveActionResult.RequiresReview -> ReactiveUndoOutcome.Unsupported("undo_requires_review")
        }
    }

    private suspend fun executeCatalog(
        control: ReactiveControl,
        authorization: ReactiveAuthorization,
        nowMillis: Long,
        invocation: ReactiveActionInvocation,
        signature: String,
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
                    operationId = invocation.operationId,
                    idempotencyKey = invocation.idempotencyKey,
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
                        "stateRevision" to control.stateRevision.toString(),
                    ),
                )
                receiptStore.recordIdempotent(signature, receipt)
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
        invocation: ReactiveActionInvocation,
        signature: String,
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
                operationId = invocation.operationId,
                idempotencyKey = invocation.idempotencyKey,
                controlId = control.id,
                actionRevision = control.actionRevision,
                completedAtMillis = nowMillis,
                result = ReactiveActionResult.Succeeded("hid_command_sent"),
                undo = command.undoAction(nowMillis),
                expiresAtMillis = null,
                metadata = mapOf(
                    "actionId" to (control.action as? ReactiveAction.Hid)?.command?.name.orEmpty(),
                    "hidCommand" to command.name,
                    "stateRevision" to control.stateRevision.toString(),
                ),
            )
            receiptStore.recordIdempotent(signature, receipt)
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
        invocation: ReactiveActionInvocation,
        signature: String,
        actions: List<ReactiveAction>,
        currentState: MacStateSnapshot?,
    ): ReactiveExecutionOutcome {
        var lastOutcome: ReactiveExecutionOutcome = ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("composite_empty"))
        for (action in actions) {
            val stepControl = control.copy(action = action)
            lastOutcome = when (action) {
                is ReactiveAction.ExistingCatalog -> executeCatalog(
                    stepControl,
                    authorization,
                    nowMillis,
                    invocation,
                    signature,
                    action.actionId,
                    currentState,
                )
                is ReactiveAction.Hid -> executeHid(stepControl, nowMillis, invocation, signature, action.command)
                is ReactiveAction.Helper -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("helper_not_implemented"))
                is ReactiveAction.BundledSshFallback -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("bundled_ssh_fallback_not_implemented"))
                is ReactiveAction.ChangeMode -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("mode_change_requires_viewmodel"))
                is ReactiveAction.Composite -> ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("nested_composite_not_supported"))
            }
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

private fun SharedHidCommand.undoAction(nowMillis: Long): io.codecks.domain.reactive.ReactiveUndoAction? {
    val inverse = when (this) {
        SharedHidCommand.BrowserBack -> SharedHidCommand.BrowserForward
        SharedHidCommand.BrowserForward -> SharedHidCommand.BrowserBack
        else -> return null
    }
    return io.codecks.domain.reactive.ReactiveUndoAction(
        label = "Undo $name",
        action = ReactiveAction.Hid(inverse),
        expiresAtMillis = nowMillis + 30_000L,
    )
}

private fun ReactiveControl.idempotencySignature(): String = listOf(
    id.value,
    actionRevision.value,
    stateRevision.toString(),
    action.signaturePart(),
).joinToString("|")

private fun ReactiveAction.signaturePart(): String = when (this) {
    is ReactiveAction.ExistingCatalog -> "catalog:$actionId"
    is ReactiveAction.Hid -> "hid:${command.name}"
    is ReactiveAction.Helper -> "helper:$actionId:${arguments.toSortedString()}"
    is ReactiveAction.BundledSshFallback -> "ssh:$bundleId:${arguments.toSortedString()}"
    is ReactiveAction.Composite -> actions.joinToString(prefix = "composite:", separator = ",") { it.signaturePart() }
    is ReactiveAction.ChangeMode -> "mode:${mode.name}"
}

private fun Map<String, String>.toSortedString(): String =
    entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
