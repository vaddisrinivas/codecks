package io.codecks.core.reactive

import io.codecks.HidCommand
import io.codecks.HidRepository
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.commandRevision
import io.codecks.core.actions.dangerousConfirmationRevision
import io.codecks.data.ActionRepository
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandReview
import io.codecks.domain.DeckAction
import io.codecks.domain.ExecutionAuthorization
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.ControlId
import io.codecks.domain.reactive.InMemoryReactiveReceiptStore
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveActionExecutor
import io.codecks.domain.reactive.ReactiveActionInvocation
import io.codecks.domain.reactive.ReactiveActionReceipt
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveAuthorization
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveExecutionOutcome
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveIdempotencyKey
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveUndoAction
import io.codecks.domain.reactive.ReactiveUndoOutcome
import io.codecks.domain.reactive.ReceiptId
import io.codecks.domain.reactive.SharedHidCommand
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
        receiptStore.findByIdempotencyKey(invocation.idempotencyKey)?.let { existing ->
            return if (existing.signature == signature) {
                ReactiveExecutionOutcome(existing.receipt.result, existing.receipt)
            } else {
                recordOutcome(
                    control = control,
                    invocation = invocation,
                    nowMillis = nowMillis,
                    result = ReactiveActionResult.Failed("idempotency_key_reused", retryable = false),
                    metadata = mapOf("conflict" to "idempotency_key"),
                    idempotentSignature = null,
                )
            }
        }

        if (invocation.requestedAtMillis + invocation.timeoutMillis < nowMillis) {
            return recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Expired,
                idempotentSignature = signature,
            )
        }
        if (control.expiresAtMillis < nowMillis) {
            return recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Expired,
                idempotentSignature = signature,
            )
        }
        currentState?.let { state ->
            if (control.stateRevision != state.snapshotRevision) {
                return recordOutcome(
                    control = control,
                    invocation = invocation,
                    nowMillis = nowMillis,
                    result = ReactiveActionResult.Failed("stale_state_revision", retryable = false),
                    idempotentSignature = signature,
                )
            }
            val available = state.capabilities
                .filter { it.availability == CapabilityAvailability.Available }
                .map { it.capability }
                .toSet()
            if (!control.requiredCapabilities.all(available::contains)) {
                return recordOutcome(
                    control = control,
                    invocation = invocation,
                    nowMillis = nowMillis,
                    result = ReactiveActionResult.Unsupported("capability_unavailable"),
                    idempotentSignature = signature,
                )
            }
        }

        return when (val action = control.action) {
            is ReactiveAction.ExistingCatalog -> executeCatalog(
                control = control,
                authorization = authorization,
                nowMillis = nowMillis,
                invocation = invocation,
                idempotentSignature = signature,
                actionId = action.actionId,
                currentState = currentState,
            )
            is ReactiveAction.Hid -> executeHid(
                control = control,
                nowMillis = nowMillis,
                invocation = invocation,
                idempotentSignature = signature,
                command = action.command,
            )
            is ReactiveAction.Composite -> executeComposite(
                control = control,
                authorization = authorization,
                nowMillis = nowMillis,
                invocation = invocation,
                idempotentSignature = signature,
                actions = action.actions,
                currentState = currentState,
            )
            is ReactiveAction.Helper -> recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Unsupported("helper_not_implemented"),
                idempotentSignature = signature,
            )
            is ReactiveAction.BundledSshFallback -> recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Unsupported("bundled_ssh_fallback_not_implemented"),
                idempotentSignature = signature,
            )
            is ReactiveAction.ChangeMode -> recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Unsupported("mode_change_requires_viewmodel"),
                idempotentSignature = signature,
            )
        }
    }

    override suspend fun undo(
        receiptId: ReceiptId,
        nowMillis: Long,
        invocation: ReactiveActionInvocation?,
    ): ReactiveUndoOutcome {
        val receipt = receiptStore.findByReceiptId(receiptId)
            ?: return ReactiveUndoOutcome.Unsupported("receipt_missing")
        val undo = receipt.undo
            ?: return ReactiveUndoOutcome.Unsupported("undo_unavailable")
        if (undo.expiresAtMillis < nowMillis || (receipt.expiresAtMillis != null && receipt.expiresAtMillis < nowMillis)) {
            return ReactiveUndoOutcome.Expired
        }

        val undoControl = ReactiveControl(
            id = ControlId("undo-${receipt.id.value.take(48)}"),
            title = undo.label,
            subtitle = null,
            icon = ReactiveIcon.Generic,
            action = undo.action,
            source = ReactiveControlSource.UndoReceipt,
            basePriority = 0,
            reason = "Undo ${receipt.controlId.value}",
            requiredCapabilities = emptySet(),
            risk = ReactiveRisk.Safe,
            reversible = false,
            stateRevision = 0L,
            actionRevision = ActionRevision("undo-${receipt.actionRevision.value.take(64)}"),
            expiresAtMillis = undo.expiresAtMillis,
        )
        val undoInvocation = invocation ?: ReactiveActionInvocation.create(undoControl, nowMillis)
        val outcome = execute(undoControl, nowMillis = nowMillis, invocation = undoInvocation)
        return when (val result = outcome.result) {
            is ReactiveActionResult.Succeeded -> ReactiveUndoOutcome.Succeeded(
                outcome.receipt ?: return ReactiveUndoOutcome.Failed("undo_receipt_missing", retryable = false),
            )
            ReactiveActionResult.Expired -> ReactiveUndoOutcome.Expired
            is ReactiveActionResult.Unsupported -> ReactiveUndoOutcome.Unsupported(result.reasonCode)
            is ReactiveActionResult.Failed -> ReactiveUndoOutcome.Failed(result.errorCode, result.retryable)
            is ReactiveActionResult.RequiresConfirmation -> ReactiveUndoOutcome.Failed("undo_requires_confirmation", retryable = false)
            is ReactiveActionResult.RequiresReview -> ReactiveUndoOutcome.Failed("undo_requires_review", retryable = false)
        }
    }

    private suspend fun executeCatalog(
        control: ReactiveControl,
        authorization: ReactiveAuthorization,
        nowMillis: Long,
        invocation: ReactiveActionInvocation,
        idempotentSignature: String,
        actionId: String,
        currentState: MacStateSnapshot?,
    ): ReactiveExecutionOutcome {
        val action = actionRepository.allActions().firstOrNull { it.id == actionId }
            ?: return recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Unsupported("catalog_action_missing"),
                idempotentSignature = idempotentSignature,
            )

        val resolvedActionRevision = action.reactiveActionRevision()
        if (control.actionRevision != resolvedActionRevision) {
            return recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Failed("stale_action_revision", retryable = false),
                metadata = mapOf("actionId" to action.id),
                actionRevision = resolvedActionRevision,
                idempotentSignature = idempotentSignature,
            )
        }
        if (currentState != null && !action.targetSelector.matches(currentState.macId.value, deviceRepository)) {
            return recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Failed("target_changed", retryable = false),
                metadata = mapOf("actionId" to action.id),
                actionRevision = resolvedActionRevision,
                idempotentSignature = idempotentSignature,
            )
        }

        if (action.dangerous && authorization.confirmedActionRevision != resolvedActionRevision) {
            return recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.RequiresConfirmation(
                    actionRevision = resolvedActionRevision,
                    title = action.confirmationTitle ?: "Confirm ${action.label}",
                    body = action.confirmationBody ?: (action.riskReason ?: "This action needs explicit confirmation."),
                ),
                metadata = mapOf("actionId" to action.id),
                actionRevision = resolvedActionRevision,
                idempotentSignature = idempotentSignature,
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
            return recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.RequiresReview(
                    actionRevision = resolvedActionRevision,
                    reason = effectiveAction.riskReason ?: "Review this command before running",
                ),
                metadata = mapOf("actionId" to action.id),
                actionRevision = resolvedActionRevision,
                idempotentSignature = idempotentSignature,
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
            ActionResultStatus.Succeeded -> recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = result.timestampMillis.takeIf { it > 0 } ?: nowMillis,
                result = ReactiveActionResult.Succeeded("catalog_action_succeeded"),
                metadata = mapOf(
                    "actionId" to action.id,
                    "title" to action.label,
                    "message" to result.message,
                ),
                actionRevision = resolvedActionRevision,
                idempotentSignature = idempotentSignature,
            )
            ActionResultStatus.RequiresConfirmation -> recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = result.timestampMillis.takeIf { it > 0 } ?: nowMillis,
                result = ReactiveActionResult.RequiresConfirmation(
                    actionRevision = resolvedActionRevision,
                    title = action.confirmationTitle ?: "Confirm ${action.label}",
                    body = action.confirmationBody ?: (action.riskReason ?: "This action needs explicit confirmation."),
                ),
                metadata = mapOf("actionId" to action.id, "message" to result.message),
                actionRevision = resolvedActionRevision,
                idempotentSignature = idempotentSignature,
            )
            ActionResultStatus.RequiresReview -> recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = result.timestampMillis.takeIf { it > 0 } ?: nowMillis,
                result = ReactiveActionResult.RequiresReview(
                    actionRevision = resolvedActionRevision,
                    reason = result.message,
                ),
                metadata = mapOf("actionId" to action.id, "message" to result.message),
                actionRevision = resolvedActionRevision,
                idempotentSignature = idempotentSignature,
            )
            ActionResultStatus.Failed -> recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = result.timestampMillis.takeIf { it > 0 } ?: nowMillis,
                result = ReactiveActionResult.Failed(
                    errorCode = "catalog_action_failed",
                    retryable = true,
                ),
                metadata = mapOf("actionId" to action.id, "message" to result.message),
                actionRevision = resolvedActionRevision,
                idempotentSignature = idempotentSignature,
            )
        }
    }

    private fun executeHid(
        control: ReactiveControl,
        nowMillis: Long,
        invocation: ReactiveActionInvocation,
        idempotentSignature: String,
        command: SharedHidCommand,
    ): ReactiveExecutionOutcome {
        val hidState = hidRepository.state.value
        if (!hidState.isConnected) {
            return recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Failed(
                    errorCode = "hid_not_connected",
                    retryable = true,
                ),
                idempotentSignature = idempotentSignature,
            )
        }
        return runCatching {
            hidRepository.send(command.toHidCommand())
            recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Succeeded("hid_command_sent"),
                undo = command.undoAction(nowMillis),
                metadata = mapOf(
                    "actionId" to (control.action as? ReactiveAction.Hid)?.command?.name.orEmpty(),
                    "hidCommand" to command.name,
                ),
                idempotentSignature = idempotentSignature,
            )
        }.getOrElse {
            recordOutcome(
                control = control,
                invocation = invocation,
                nowMillis = nowMillis,
                result = ReactiveActionResult.Failed(
                    errorCode = "hid_command_failed",
                    retryable = true,
                ),
                idempotentSignature = idempotentSignature,
            )
        }
    }

    private suspend fun executeComposite(
        control: ReactiveControl,
        authorization: ReactiveAuthorization,
        nowMillis: Long,
        invocation: ReactiveActionInvocation,
        idempotentSignature: String,
        actions: List<ReactiveAction>,
        currentState: MacStateSnapshot?,
    ): ReactiveExecutionOutcome {
        var lastOutcome = ReactiveExecutionOutcome(ReactiveActionResult.Unsupported("composite_empty"))
        actions.forEachIndexed { index, action ->
            val stepControl = control.copy(action = action)
            val stepInvocation = ReactiveActionInvocation.create(
                control = stepControl,
                nowMillis = nowMillis,
                operationId = invocation.operationId,
                idempotencyKey = ReactiveIdempotencyKey("${invocation.idempotencyKey.value.take(80)}:$index"),
            )
            lastOutcome = execute(stepControl, authorization, nowMillis, currentState, stepInvocation)
            if (lastOutcome.result !is ReactiveActionResult.Succeeded) {
                return lastOutcome
            }
        }
        return recordOutcome(
            control = control,
            invocation = invocation,
            nowMillis = nowMillis,
            result = lastOutcome.result,
            metadata = mapOf("compositeSteps" to actions.size.toString()),
            idempotentSignature = idempotentSignature,
        )
    }

    private fun recordOutcome(
        control: ReactiveControl,
        invocation: ReactiveActionInvocation,
        nowMillis: Long,
        result: ReactiveActionResult,
        undo: ReactiveUndoAction? = null,
        expiresAtMillis: Long? = null,
        metadata: Map<String, String> = emptyMap(),
        actionRevision: ActionRevision = control.actionRevision,
        idempotentSignature: String?,
    ): ReactiveExecutionOutcome {
        if (result !is ReactiveActionResult.Succeeded) {
            return ReactiveExecutionOutcome(result = result, receipt = null)
        }
        val receipt = ReactiveActionReceipt(
            id = newReactiveReceiptId(),
            operationId = invocation.operationId,
            idempotencyKey = invocation.idempotencyKey,
            controlId = control.id,
            actionRevision = actionRevision,
            completedAtMillis = nowMillis,
            result = result,
            undo = undo,
            expiresAtMillis = expiresAtMillis,
            metadata = metadata,
        )
        if (idempotentSignature == null) {
            receiptStore.record(receipt)
        } else {
            receiptStore.recordIdempotent(idempotentSignature, receipt)
        }
        return ReactiveExecutionOutcome(result, receipt)
    }
}

private fun ReactiveControl.idempotencySignature(): String = listOf(
    id.value,
    actionRevision.value,
    stateRevision.toString(),
    action.signaturePart(),
    requiredCapabilities.map { it.name }.sorted().joinToString(","),
).joinToString("|")

private fun ReactiveAction.signaturePart(): String = when (this) {
    is ReactiveAction.ExistingCatalog -> "catalog:$actionId"
    is ReactiveAction.Hid -> "hid:${command.name}"
    is ReactiveAction.Helper -> "helper:$actionId:${arguments.toSortedMap()}"
    is ReactiveAction.BundledSshFallback -> "ssh:$bundleId:${arguments.toSortedMap()}"
    is ReactiveAction.Composite -> "composite:${actions.joinToString(",") { it.signaturePart() }}"
    is ReactiveAction.ChangeMode -> "mode:${mode.name}"
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

private fun SharedHidCommand.undoAction(nowMillis: Long): ReactiveUndoAction? {
    val inverse = when (this) {
        SharedHidCommand.BrowserBack -> SharedHidCommand.BrowserForward
        SharedHidCommand.BrowserForward -> SharedHidCommand.BrowserBack
        else -> return null
    }
    return ReactiveUndoAction(
        label = "Undo $name",
        action = ReactiveAction.Hid(inverse),
        expiresAtMillis = nowMillis + 30_000L,
    )
}
