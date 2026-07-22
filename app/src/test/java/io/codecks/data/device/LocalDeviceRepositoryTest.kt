package io.codecks.data.device

import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionTarget
import io.codecks.domain.device.DeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDeviceRepositoryTest {
    @Test
    fun currentTargetIdUsesPersistedIdentityForDefaultSshPort() {
        val config = readyConfig(port = 22)
        val target = readyTarget(id = "mac_user_studio_local_22", port = 22)

        assertEquals(
            DeviceId(target.id),
            resolveCurrentTargetDeviceId(config, listOf(target)),
        )
    }

    @Test
    fun currentTargetIdMatchesHostUserAndCustomPort() {
        val port22 = readyTarget(id = "mac_user_studio_local_22", port = 22)
        val port2222 = readyTarget(id = "custom-persisted-id", port = 2222)

        assertEquals(
            DeviceId(port2222.id),
            resolveCurrentTargetDeviceId(readyConfig(port = 2222), listOf(port22, port2222)),
        )
    }

    @Test
    fun currentTargetIdIsAbsentWhenConfigHasNoPersistedTarget() {
        assertNull(resolveCurrentTargetDeviceId(readyConfig(port = 22), emptyList()))
    }

    private fun readyConfig(port: Int) = ConnectionConfig(
        host = "studio.local",
        port = port,
        user = "user",
        hasKey = true,
        hostKey = "ssh-ed25519 test",
    )

    private fun readyTarget(id: String, port: Int) = ConnectionTarget(
        id = id,
        host = "studio.local",
        port = port,
        user = "user",
        hasKey = true,
        hostKey = "ssh-ed25519 test",
    )
}
