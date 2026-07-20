package io.codecks.runtime.actions

import io.codecks.domain.actions.ActionDefinition
import io.codecks.domain.actions.ActionInvocation
import io.codecks.domain.actions.ActionPlan
import io.codecks.domain.actions.ConfirmationRequest
import io.codecks.domain.actions.PlannedStep
import io.codecks.domain.actions.SafetyClass
import io.codecks.domain.targets.LiveState
import io.codecks.domain.targets.MacTarget
import io.codecks.domain.targets.TargetSelection
import io.codecks.domain.targets.TrustState

class ActionPlanner(
    private val confirmationWindowMillis: Long = 60_000,
) {
    fun plan(
        definition: ActionDefinition,
        invocation: ActionInvocation,
        currentTarget: MacTarget?,
        savedTargets: List<MacTarget>,
    ): ActionPlan {
        val target = resolveTarget(invocation.targetSelection, currentTarget, savedTargets)
        require(target.trustState == TrustState.TRUSTED) {
            "Target ${target.logicalId} is not trusted"
        }
        require(target.liveState == LiveState.ONLINE || target.liveState == LiveState.DEGRADED) {
            "Target ${target.logicalId} is not online"
        }
        require(target.supports(definition.capabilities)) {
            "Target ${target.logicalId} lacks ${definition.capabilities}"
        }

        val missingInputs = definition.inputs.filter { it.required && invocation.parameters[it.name].isNullOrBlank() }
        require(missingInputs.isEmpty()) {
            "Missing required inputs: ${missingInputs.joinToString { it.name }}"
        }

        val confirmation = when (definition.safetyClass) {
            SafetyClass.SAFE -> emptyList()
            SafetyClass.CONFIRM,
            SafetyClass.DANGEROUS,
            SafetyClass.DEVELOPER_ONLY -> listOf(
                ConfirmationRequest(
                    reason = "Confirm ${definition.title}",
                    expiresAtEpochMillis = invocation.requestedAtEpochMillis + confirmationWindowMillis,
                ),
            )
        }

        return ActionPlan(
            invocationId = invocation.invocationId,
            resolvedTargetId = target.logicalId,
            steps = listOf(
                PlannedStep(
                    stableId = definition.stableId,
                    executorKind = definition.executorKind,
                    safeSummary = definition.title,
                    timeoutPolicy = definition.timeoutPolicy,
                ),
            ),
            confirmations = confirmation,
            redactions = definition.inputs.filter { it.redacted }.map { it.name },
            expectedCapabilities = definition.capabilities,
        )
    }

    private fun resolveTarget(
        selection: TargetSelection,
        currentTarget: MacTarget?,
        savedTargets: List<MacTarget>,
    ): MacTarget = when (selection) {
        TargetSelection.Current -> currentTarget ?: error("No current target")
        is TargetSelection.Specific -> savedTargets.firstOrNull { it.logicalId == selection.logicalId }
            ?: error("Unknown target: ${selection.logicalId}")
        is TargetSelection.Group -> error("Groups are not in core v1 yet: ${selection.groupId}")
        TargetSelection.All -> error("All-target execution is not enabled in core v1")
        TargetSelection.Ask -> error("Target selection required")
    }
}
