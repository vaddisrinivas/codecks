package io.codecks.domain.device

sealed interface TargetSelector {
    data object CurrentDevice : TargetSelector
    data class SpecificDevice(val deviceId: DeviceId) : TargetSelector
    data class DeviceGroup(val groupId: DeviceGroupId) : TargetSelector
    data object AllCompatibleDevices : TargetSelector
    data object AskAtRunTime : TargetSelector
}

data class TargetResolution(
    val compatible: List<TargetDevice>,
    val unavailableDeviceIds: List<DeviceId> = emptyList(),
    val incompatible: List<IncompatibleTarget> = emptyList(),
) {
    val hasRunnableTargets: Boolean = compatible.isNotEmpty()
}

data class IncompatibleTarget(
    val device: TargetDevice,
    val missingCapabilities: Set<Capability>,
)

fun TargetSelector.resolveTargets(
    devices: List<TargetDevice>,
    groups: List<DeviceGroup>,
    currentDeviceId: DeviceId?,
    requiredCapabilities: Set<Capability>,
): TargetResolution {
    val devicesById = devices.associateBy(TargetDevice::id)
    val selectedIds = when (this) {
        TargetSelector.CurrentDevice -> currentDeviceId?.let(::listOf).orEmpty()
        is TargetSelector.SpecificDevice -> listOf(deviceId)
        is TargetSelector.DeviceGroup -> groups
            .firstOrNull { it.id == groupId }
            ?.memberIds
            .orEmpty()
        TargetSelector.AllCompatibleDevices -> devices.map(TargetDevice::id)
        TargetSelector.AskAtRunTime -> emptyList()
    }

    val selected = selectedIds.mapNotNull(devicesById::get)
    val unavailable = selectedIds.filter { id -> devicesById[id]?.online != true }
    val onlineSelected = selected.filter(TargetDevice::online)
    val compatible = onlineSelected.filter { device -> device.capabilities.containsAll(requiredCapabilities) }
    val incompatible = onlineSelected
        .filterNot { device -> device.capabilities.containsAll(requiredCapabilities) }
        .map { device ->
            IncompatibleTarget(
                device = device,
                missingCapabilities = requiredCapabilities - device.capabilities,
            )
        }

    return TargetResolution(
        compatible = compatible,
        unavailableDeviceIds = unavailable,
        incompatible = incompatible,
    )
}

