package io.codecks.ui.ai

import io.codecks.domain.ai.ActionCapability
import io.codecks.domain.device.Capability
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.device.TargetDevice

/**
 * Returns only the capabilities that every ready target can actually support.
 *
 * AI drafts default to an "any connected Mac" target, so advertising the union
 * would let a draft claim support that disappears when it is run on another
 * ready Mac. HID is deliberately absent here: it is only advertised once a
 * device transport explicitly reports it.
 */
internal suspend fun DeviceRepository.availableAiCapabilities(): Set<ActionCapability> =
    readyAiCapabilities(devices())

internal fun readyAiCapabilities(devices: List<TargetDevice>): Set<ActionCapability> =
    devices
        .filter(TargetDevice::online)
        .map { target -> target.capabilities.toAiCapabilities() }
        .reduceOrNull { common, capabilities -> common intersect capabilities }
        .orEmpty()

private fun Set<Capability>.toAiCapabilities(): Set<ActionCapability> = buildSet {
    val values = map(Capability::value).map(String::lowercase).toSet()
    if ("ssh" in values) {
        // Codecks' reviewed-command executor runs through the ready SSH transport.
        add(ActionCapability.Ssh)
        add(ActionCapability.Shell)
        add(ActionCapability.Advanced)
    }
    if ("clipboard" in values) add(ActionCapability.Clipboard)
    if ("browser" in values) add(ActionCapability.Browser)
    if ("media" in values) add(ActionCapability.Media)
}
