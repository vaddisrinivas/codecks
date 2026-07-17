package io.codex.s23deck.domain.device

interface TransportRegistry {
    fun transportsFor(device: TargetDevice): Set<TransportId> = device.transports

    fun supports(
        transportId: TransportId,
        device: TargetDevice,
        requiredCapabilities: Set<Capability>,
    ): Boolean = transportId in device.transports && device.capabilities.containsAll(requiredCapabilities)

    fun selectTransport(
        device: TargetDevice,
        requiredCapabilities: Set<Capability>,
        preferredTransports: List<TransportId> = emptyList(),
    ): TransportId? {
        val candidates = preferredTransports.filter { it in device.transports } +
            device.transports.filterNot { it in preferredTransports }
        return candidates.firstOrNull { supports(it, device, requiredCapabilities) }
    }
}

