package io.codecks.domain.execution

fun interface ActionExecutor {
    suspend fun execute(target: ExecutionTarget, plan: ExecutionPlan): String
}

