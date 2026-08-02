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

    suspend fun pasteWithoutHid(): Result<Unit> =
        connectionRepository.runCommand(
            "osascript -e 'tell application \"System Events\" to keystroke \"v\" using command down'",
        ).map { }

    suspend fun enterWithoutHid(): Result<Unit> =
        connectionRepository.runCommand(
            "osascript -e 'tell application \"System Events\" to key code 36'",
        ).map { }
}
