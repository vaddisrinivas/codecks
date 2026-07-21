package io.codecks.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSelectorTest {
    private val ssh = TransportId("ssh")
    private val hid = TransportId("hid")
    private val clipboard = Capability("clipboard")
    private val keyboard = Capability("keyboard")
    private val mac = TargetDevice(
        id = DeviceId("mac"),
        name = "Mac Studio",
        platform = "macos",
        transports = setOf(ssh, hid),
        capabilities = setOf(clipboard, keyboard),
    )
    private val renamedMac = mac.copy(name = "Desk Mac")
    private val offlinePc = TargetDevice(
        id = DeviceId("pc"),
        name = "Gaming PC",
        platform = "windows",
        transports = setOf(ssh),
        capabilities = setOf(clipboard),
        online = false,
    )

    @Test
    fun currentDeviceResolvesThroughCurrentIdAndKeepsRenamedDeviceStable() {
        val resolution = TargetSelector.CurrentDevice.resolveTargets(
            devices = listOf(renamedMac),
            groups = emptyList(),
            currentDeviceId = DeviceId("mac"),
            requiredCapabilities = setOf(keyboard),
        )

        assertEquals(listOf(renamedMac), resolution.compatible)
        assertEquals("Desk Mac", resolution.compatible.single().name)
    }

    @Test
    fun groupKeepsOfflineAndRemovedMembersAsUnavailableIds() {
        val group = DeviceGroup(
            id = DeviceGroupId("desk"),
            name = "Desk",
            memberIds = listOf(DeviceId("mac"), DeviceId("pc"), DeviceId("removed")),
        )

        val resolution = TargetSelector.DeviceGroup(DeviceGroupId("desk")).resolveTargets(
            devices = listOf(mac, offlinePc),
            groups = listOf(group),
            currentDeviceId = null,
            requiredCapabilities = setOf(clipboard),
        )

        assertEquals(listOf(mac), resolution.compatible)
        assertEquals(listOf(DeviceId("pc"), DeviceId("removed")), resolution.unavailableDeviceIds)
        assertTrue(resolution.hasRunnableTargets)
    }

    @Test
    fun incompatibleActionsReportMissingCapabilities() {
        val resolution = TargetSelector.SpecificDevice(DeviceId("mac")).resolveTargets(
            devices = listOf(mac),
            groups = emptyList(),
            currentDeviceId = null,
            requiredCapabilities = setOf(Capability("screen-recording")),
        )

        assertTrue(resolution.compatible.isEmpty())
        assertEquals(DeviceId("mac"), resolution.incompatible.single().device.id)
        assertEquals(setOf(Capability("screen-recording")), resolution.incompatible.single().missingCapabilities)
        assertFalse(resolution.hasRunnableTargets)
    }

    @Test
    fun summariesAreImmutableCopiesOfDeviceCapabilitySets() {
        val summary = mac.toSummary()

        assertEquals(DeviceId("mac"), summary.id)
        assertEquals(setOf(clipboard, keyboard), summary.capabilities)
    }
}

