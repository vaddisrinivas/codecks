package io.codex.s23deck.domain.device

@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeviceId must not be blank." }
    }
}

@JvmInline
value class DeviceGroupId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeviceGroupId must not be blank." }
    }
}

@JvmInline
value class TransportId(val value: String) {
    init {
        require(value.isNotBlank()) { "TransportId must not be blank." }
    }
}

@JvmInline
value class Capability(val value: String) {
    init {
        require(value.isNotBlank()) { "Capability must not be blank." }
    }
}

data class TargetDevice(
    val id: DeviceId,
    val name: String,
    val platform: String,
    val transports: Set<TransportId>,
    val capabilities: Set<Capability>,
    val online: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "TargetDevice name must not be blank." }
        require(platform.isNotBlank()) { "TargetDevice platform must not be blank." }
    }
}

data class DeviceGroup(
    val id: DeviceGroupId,
    val name: String,
    val memberIds: List<DeviceId>,
) {
    init {
        require(name.isNotBlank()) { "DeviceGroup name must not be blank." }
    }
}

data class DeviceSummary(
    val id: DeviceId,
    val name: String,
    val platform: String,
    val online: Boolean,
    val capabilities: Set<Capability>,
)

fun TargetDevice.toSummary(): DeviceSummary = DeviceSummary(
    id = id,
    name = name,
    platform = platform,
    online = online,
    capabilities = capabilities.toSet(),
)

