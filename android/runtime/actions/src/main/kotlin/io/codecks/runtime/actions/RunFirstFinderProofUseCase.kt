package io.codecks.runtime.actions

import io.codecks.core.common.CodecksClock
import io.codecks.core.common.SystemCodecksClock
import io.codecks.domain.actions.ActionInvocation
import io.codecks.domain.actions.ActionPlan
import io.codecks.domain.actions.ActionReceipt
import io.codecks.domain.actions.CoreActionCatalog
import io.codecks.domain.actions.MacActionExecutor
import io.codecks.domain.actions.ReceiptState
import io.codecks.domain.actions.RepairHint
import io.codecks.domain.targets.MacTarget
import io.codecks.domain.targets.TargetSelection

class FakeMacActionExecutor(
    private val succeeds: Boolean = true,
    private val safeSummary: String = "Finder proof",
) : MacActionExecutor {
    override suspend fun execute(
        plan: ActionPlan,
        target: MacTarget,
        startedAtEpochMillis: Long,
    ): ActionReceipt {
        return ActionReceipt(
            invocationId = plan.invocationId,
            state = if (succeeds) ReceiptState.SUCCESS else ReceiptState.FAILURE,
            targetId = target.logicalId,
            startedAtEpochMillis = startedAtEpochMillis,
            endedAtEpochMillis = startedAtEpochMillis + 120,
            safeSummary = if (succeeds) "$safeSummary succeeded on ${target.displayName}" else "$safeSummary failed on ${target.displayName}",
            repair = if (succeeds) {
                null
            } else {
                RepairHint(
                    title = "Check SSH trust, key install, and Finder availability.",
                    actionLabel = "Repair connection",
                )
            },
        )
    }
}

class RunFirstFinderProofUseCase(
    private val planner: ActionPlanner = ActionPlanner(),
    private val executor: MacActionExecutor,
    private val clock: CodecksClock = SystemCodecksClock,
) {
    suspend operator fun invoke(target: MacTarget): ActionReceipt {
        val now = clock.nowEpochMillis()
        val invocation = ActionInvocation(
            invocationId = "finder-proof-$now",
            actionId = "mac.finder.open",
            targetSelection = TargetSelection.Current,
            origin = "connection-setup",
            requestedAtEpochMillis = now,
        )
        val plan = planner.plan(
            definition = CoreActionCatalog.requireDefinition(invocation.actionId),
            invocation = invocation,
            currentTarget = target,
            savedTargets = listOf(target),
        )
        return executor.execute(plan, target, now)
    }
}
