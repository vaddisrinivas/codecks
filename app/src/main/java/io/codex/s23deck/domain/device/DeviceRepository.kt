package io.codex.s23deck.domain.device

interface DeviceRepository {
    suspend fun devices(): List<TargetDevice>
    suspend fun groups(): List<DeviceGroup>
    suspend fun currentDeviceId(): DeviceId?
    suspend fun summaries(): List<DeviceSummary> = devices().map(TargetDevice::toSummary)
}

