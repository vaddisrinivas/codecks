package io.codecks.domain.execution

import io.codecks.domain.device.Capability
import io.codecks.domain.device.DeviceGroup
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.device.TargetDevice
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.device.TransportId
import io.codecks.domain.device.TransportRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionPlannerTargetAliasTest {
    @Test
    fun unmigratedLegacySpecificTargetIsNotAcceptedAtRuntime() = runTest {
        val legacyId = DeviceId("mac_alice_studio_local_22")
        val opaqueId = DeviceId("8ee80175-c04f-4b03-ae3c-ad18f129a553")
        val device = TargetDevice(
            id = opaqueId,
            name = "Studio",
            platform = "macOS",
            transports = setOf(TransportId("ssh")),
            capabilities = setOf(Capability("deck")),
            online = true,
        )
        val repository = AliasDeviceRepository(
            device = device,
        )

        val plan = ExecutionPlanner(repository, object : TransportRegistry {})
            .plan(
                actionId = "saved-action",
                selector = TargetSelector.SpecificDevice(legacyId),
                requiredCapabilities = setOf(Capability("deck")),
            )

        assertEquals(emptyList<ExecutionTarget>(), plan.targets)
        assertEquals(TargetSelector.SpecificDevice(legacyId), plan.selector)
    }
}

private class AliasDeviceRepository(
    private val device: TargetDevice,
) : DeviceRepository {
    override suspend fun devices(): List<TargetDevice> = listOf(device)
    override suspend fun groups(): List<DeviceGroup> = emptyList()
    override suspend fun currentDeviceId(): DeviceId = device.id
}
