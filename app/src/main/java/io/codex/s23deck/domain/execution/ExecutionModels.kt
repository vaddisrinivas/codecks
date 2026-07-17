package io.codex.s23deck.domain.execution

import io.codex.s23deck.domain.device.Capability
import io.codex.s23deck.domain.device.DeviceId
import io.codex.s23deck.domain.device.TargetDevice
import io.codex.s23deck.domain.device.TargetSelector
import io.codex.s23deck.domain.device.TransportId

data class ExecutionPlan(
    val actionId: String,
    val selector: TargetSelector,
    val requiredCapabilities: Set<Capability>,
    val targets: List<ExecutionTarget>,
    val steps: List<String> = listOf(actionId),
    val maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    init {
        require(actionId.isNotBlank()) { "ExecutionPlan actionId must not be blank." }
        require(targets.distinctBy { it.device.id }.size == targets.size) {
            "ExecutionPlan must contain at most one target per device."
        }
        require(steps.isNotEmpty()) { "ExecutionPlan must contain at least one step." }
        require(maxConcurrency > 0) { "ExecutionPlan maxConcurrency must be positive." }
        require(timeoutMillis > 0) { "ExecutionPlan timeoutMillis must be positive." }
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENCY = 4
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}

data class ExecutionTarget(
    val device: TargetDevice,
    val transportId: TransportId,
)

data class RetryPolicy(
    val maxRetries: Int = 0,
) {
    init {
        require(maxRetries >= 0) { "RetryPolicy maxRetries must not be negative." }
    }
}

sealed interface ExecutionStatus {
    data object Succeeded : ExecutionStatus
    data class Failed(val message: String) : ExecutionStatus
    data class TimedOut(val timeoutMillis: Long) : ExecutionStatus
    data object Canceled : ExecutionStatus
    data class Incompatible(val missingCapabilities: Set<Capability>) : ExecutionStatus
}

data class PerDeviceExecutionResult(
    val deviceId: DeviceId,
    val transportId: TransportId?,
    val status: ExecutionStatus,
    val attempts: Int,
    val output: String? = null,
)

data class ExecutionResult(
    val actionId: String,
    val results: List<PerDeviceExecutionResult>,
) {
    val byDeviceId: Map<DeviceId, PerDeviceExecutionResult> = results.associateBy(PerDeviceExecutionResult::deviceId)
    val succeeded: List<PerDeviceExecutionResult> = results.filter { it.status is ExecutionStatus.Succeeded }
    val failed: List<PerDeviceExecutionResult> = results.filterNot { it.status is ExecutionStatus.Succeeded }
    val isSuccess: Boolean = results.isNotEmpty() && failed.isEmpty()
    val isPartialSuccess: Boolean = succeeded.isNotEmpty() && failed.isNotEmpty()
}

