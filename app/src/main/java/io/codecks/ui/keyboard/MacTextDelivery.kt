package io.codecks.ui.keyboard

import io.codecks.data.ConnectionRepository
import javax.inject.Inject

/**
 * Explicit control-plane boundary for keyboard pasteboard delivery.
 *
 * HID dispatch stays synchronous and free of SSH. Pasteboard delivery is a
 * separately named user action and all remote work remains behind this port.
 */
class MacTextDelivery @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) {
    suspend fun copy(text: String): Result<Unit> =
        connectionRepository.writeMacClipboard(text).map { }
}
