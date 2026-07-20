package io.codecks.domain.actions

import io.codecks.domain.targets.MacTarget

interface MacActionExecutor {
    suspend fun execute(
        plan: ActionPlan,
        target: MacTarget,
        startedAtEpochMillis: Long,
    ): ActionReceipt
}
