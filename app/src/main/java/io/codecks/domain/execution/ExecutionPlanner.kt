package io.codecks.domain.execution

import io.codecks.domain.device.Capability
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.device.TransportRegistry
import io.codecks.domain.device.resolveTargets

class ExecutionPlanner(
    private val deviceRepository: DeviceRepository,
    private val transportRegistry: TransportRegistry,
) {
    suspend fun plan(
        actionId: String,
        selector: TargetSelector,
        requiredCapabilities: Set<Capability>,
        preferredTransports: List<io.codecks.domain.device.TransportId> = emptyList(),
        steps: List<String> = listOf(actionId),
        maxConcurrency: Int = ExecutionPlan.DEFAULT_MAX_CONCURRENCY,
        timeoutMillis: Long = ExecutionPlan.DEFAULT_TIMEOUT_MILLIS,
        retryPolicy: RetryPolicy = RetryPolicy(),
    ): ExecutionPlan {
        val devices = deviceRepository.devices()
        val resolution = selector.resolveTargets(
            devices = devices,
            groups = deviceRepository.groups(),
            currentDeviceId = deviceRepository.currentDeviceId(),
            requiredCapabilities = requiredCapabilities,
        )
        val targets = resolution.compatible.mapNotNull { device ->
            transportRegistry.selectTransport(device, requiredCapabilities, preferredTransports)?.let { transportId ->
                ExecutionTarget(device, transportId)
            }
        }

        return ExecutionPlan(
            actionId = actionId,
            selector = selector,
            requiredCapabilities = requiredCapabilities,
            targets = targets,
            steps = steps,
            maxConcurrency = maxConcurrency,
            timeoutMillis = timeoutMillis,
            retryPolicy = retryPolicy,
        )
    }
}
