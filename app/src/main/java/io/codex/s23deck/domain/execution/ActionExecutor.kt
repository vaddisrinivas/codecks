package io.codex.s23deck.domain.execution

fun interface ActionExecutor {
    suspend fun execute(target: ExecutionTarget, plan: ExecutionPlan): String
}

