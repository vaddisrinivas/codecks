package io.codex.s23deck.domain.execution

import io.codex.s23deck.domain.device.Capability
import io.codex.s23deck.domain.device.DeviceId
import io.codex.s23deck.domain.device.TargetDevice
import io.codex.s23deck.domain.device.TargetSelector
import io.codex.s23deck.domain.device.TransportId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedParallelExecutorTest {
    private val ssh = TransportId("ssh")
    private val keyboard = Capability("keyboard")

    @Test
    fun zeroDevicesReturnsStableEmptyResult() = runTest {
        val result = BoundedParallelExecutor { _, _ -> "unused" }.execute(plan(emptyList()))

        assertEquals("type-text", result.actionId)
        assertTrue(result.results.isEmpty())
        assertFalse(result.isSuccess)
    }

    @Test
    fun manyDevicesRespectBoundedConcurrencyAndResultOrder() = runTest {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val targets = listOf(target("a"), target("b"), target("c"), target("d"))

        val result = BoundedParallelExecutor { target, _ ->
            val now = active.incrementAndGet()
            maxActive.updateAndGet { current -> maxOf(current, now) }
            delay(10)
            active.decrementAndGet()
            target.device.id.value
        }.execute(plan(targets, maxConcurrency = 2))

        assertEquals(2, maxActive.get())
        assertEquals(listOf(DeviceId("a"), DeviceId("b"), DeviceId("c"), DeviceId("d")), result.results.map { it.deviceId })
        assertTrue(result.isSuccess)
    }

    @Test
    fun partialFailureDoesNotCancelSiblingTargets() = runTest {
        val result = BoundedParallelExecutor { target, _ ->
            if (target.device.id == DeviceId("b")) error("boom")
            "ok-${target.device.id.value}"
        }.execute(plan(listOf(target("a"), target("b"), target("c"))))

        assertTrue(result.isPartialSuccess)
        assertEquals(ExecutionStatus.Succeeded, result.byDeviceId.getValue(DeviceId("a")).status)
        assertEquals(ExecutionStatus.Succeeded, result.byDeviceId.getValue(DeviceId("c")).status)
        assertEquals(ExecutionStatus.Failed("boom"), result.byDeviceId.getValue(DeviceId("b")).status)
    }

    @Test
    fun retryEventuallySucceeds() = runTest {
        val attempts = AtomicInteger(0)

        val result = BoundedParallelExecutor { _, _ ->
            if (attempts.incrementAndGet() == 1) error("first")
            "ok"
        }.execute(plan(listOf(target("a")), retryPolicy = RetryPolicy(maxRetries = 1)))

        assertEquals(ExecutionStatus.Succeeded, result.results.single().status)
        assertEquals(2, result.results.single().attempts)
    }

    @Test
    fun timeoutAndCancellationReturnPerDeviceStatuses() = runTest {
        val timeoutResult = BoundedParallelExecutor { _, _ ->
            delay(1_000)
            "late"
        }.execute(plan(listOf(target("slow")), timeoutMillis = 50))
        val canceledResult = BoundedParallelExecutor { _, _ ->
            throw CancellationException("target canceled")
        }.execute(plan(listOf(target("canceled"))))

        assertEquals(ExecutionStatus.TimedOut(50), timeoutResult.results.single().status)
        assertEquals(ExecutionStatus.Canceled, canceledResult.results.single().status)
    }

    private fun plan(
        targets: List<ExecutionTarget>,
        maxConcurrency: Int = 4,
        timeoutMillis: Long = 30_000,
        retryPolicy: RetryPolicy = RetryPolicy(),
    ): ExecutionPlan = ExecutionPlan(
        actionId = "type-text",
        selector = TargetSelector.AllCompatibleDevices,
        requiredCapabilities = setOf(keyboard),
        targets = targets,
        maxConcurrency = maxConcurrency,
        timeoutMillis = timeoutMillis,
        retryPolicy = retryPolicy,
    )

    private fun target(id: String): ExecutionTarget = ExecutionTarget(
        device = TargetDevice(
            id = DeviceId(id),
            name = id.uppercase(),
            platform = "test",
            transports = setOf(ssh),
            capabilities = setOf(keyboard),
        ),
        transportId = ssh,
    )
}

