package io.codecks.domain.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

class BoundedParallelExecutor(
    private val actionExecutor: ActionExecutor,
) {
    suspend fun execute(plan: ExecutionPlan): ExecutionResult = supervisorScope {
        val semaphore = Semaphore(plan.maxConcurrency)
        val deferred = plan.targets.map { target ->
            async {
                semaphore.withPermit {
                    executeTarget(plan, target)
                }
            }
        }

        ExecutionResult(
            actionId = plan.actionId,
            results = deferred.awaitAll(),
        )
    }

    private suspend fun executeTarget(
        plan: ExecutionPlan,
        target: ExecutionTarget,
    ): PerDeviceExecutionResult {
        var attempts = 0
        var lastFailure: ExecutionStatus.Failed? = null

        repeat(plan.retryPolicy.maxRetries + 1) {
            attempts += 1
            try {
                val output = withTimeout(plan.timeoutMillis) {
                    actionExecutor.execute(target, plan)
                }
                return PerDeviceExecutionResult(
                    deviceId = target.device.id,
                    transportId = target.transportId,
                    status = ExecutionStatus.Succeeded,
                    attempts = attempts,
                    output = output,
                )
            } catch (_: TimeoutCancellationException) {
                return PerDeviceExecutionResult(
                    deviceId = target.device.id,
                    transportId = target.transportId,
                    status = ExecutionStatus.TimedOut(plan.timeoutMillis),
                    attempts = attempts,
                )
            } catch (_: CancellationException) {
                return PerDeviceExecutionResult(
                    deviceId = target.device.id,
                    transportId = target.transportId,
                    status = ExecutionStatus.Canceled,
                    attempts = attempts,
                )
            } catch (error: Throwable) {
                lastFailure = ExecutionStatus.Failed(error.message ?: error::class.simpleName.orEmpty())
            }
        }

        return PerDeviceExecutionResult(
            deviceId = target.device.id,
            transportId = target.transportId,
            status = lastFailure ?: ExecutionStatus.Failed("Unknown failure"),
            attempts = attempts,
        )
    }
}

