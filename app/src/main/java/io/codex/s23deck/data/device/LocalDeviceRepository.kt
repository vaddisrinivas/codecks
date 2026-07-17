package io.codex.s23deck.data.device

import io.codex.s23deck.data.ConnectionRepository
import io.codex.s23deck.domain.device.Capability
import io.codex.s23deck.domain.device.DeviceGroup
import io.codex.s23deck.domain.device.DeviceGroupId
import io.codex.s23deck.domain.device.DeviceId
import io.codex.s23deck.domain.device.DeviceRepository
import io.codex.s23deck.domain.device.TargetDevice
import io.codex.s23deck.domain.device.TransportId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class LocalDeviceRepository @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) : DeviceRepository {
    override suspend fun devices(): List<TargetDevice> {
        return connectionRepository.savedTargets()
            .filter { it.isConfigured }
            .map { target ->
            TargetDevice(
                id = DeviceId(target.id),
                name = target.host,
                platform = "macOS",
                transports = setOf(TransportId("ssh")),
                capabilities = setOf(
                    Capability("deck"),
                    Capability("ssh"),
                    Capability("automation"),
                    Capability("clipboard"),
                ),
                online = target.isReady,
            )
        }
    }

    override suspend fun groups(): List<DeviceGroup> {
        val members = devices().map(TargetDevice::id)
        if (members.isEmpty()) return emptyList()
        return listOf(
            DeviceGroup(
                id = DeviceGroupId("all_macs"),
                name = "All Macs",
                memberIds = members,
            ),
        )
    }

    override suspend fun currentDeviceId(): DeviceId? {
        val config = connectionRepository.config.first()
        if (!config.isConfigured) return null
        return DeviceId(currentDeviceIdValue(config.host, config.user))
    }

    private fun currentDeviceIdValue(host: String, user: String): String =
        "mac_${user}_${host}"
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "mac_current" }
}
