package io.codecks.ui.ai

import io.codecks.domain.ai.ActionCapability
import io.codecks.domain.device.Capability
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetDevice
import io.codecks.domain.device.TransportId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiDeviceCapabilitiesTest {
    @Test
    fun readyDevicesAdvertiseOnlyCapabilitiesCommonToEveryTarget() {
        val capabilities = readyAiCapabilities(
            listOf(
                device("studio", online = true, capabilities = setOf("ssh", "clipboard")),
                device("laptop", online = true, capabilities = setOf("ssh")),
                device("offline", online = false, capabilities = setOf("ssh", "clipboard", "media")),
            ),
        )

        assertEquals(
            setOf(ActionCapability.Ssh, ActionCapability.Shell, ActionCapability.Advanced),
            capabilities,
        )
    }

    @Test
    fun hidIsNeverAdvertisedWithoutAnExplicitTransportCapability() {
        val capabilities = readyAiCapabilities(
            listOf(device("studio", online = true, capabilities = setOf("ssh", "hid_keyboard", "hid_mouse"))),
        )

        assertFalse(ActionCapability.HidKeyboard in capabilities)
        assertFalse(ActionCapability.HidMouse in capabilities)
    }

    private fun device(name: String, online: Boolean, capabilities: Set<String>) = TargetDevice(
        id = DeviceId(name),
        name = name,
        platform = "macOS",
        transports = setOf(TransportId("ssh")),
        capabilities = capabilities.map(::Capability).toSet(),
        online = online,
    )
}
