package io.codecks.core.actions

import io.codecks.domain.CommandOrigin
import io.codecks.domain.DeckAction
import io.codecks.domain.device.DeviceGroupId
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import java.security.MessageDigest

private const val NULL_TOKEN = "<null>"

fun commandRevision(
    command: String,
    targetSelector: TargetSelector,
    origin: CommandOrigin,
    dangerous: Boolean,
    riskReason: String? = null,
    confirmationTitle: String? = null,
    confirmationBody: String? = null,
): String = canonicalSha256(
    listOf(
        "command=${trimmed(command)}",
        "target=${targetSelector.canonicalToken()}",
        "origin=${origin.name}",
        "dangerous=$dangerous",
        "risk=${trimmed(riskReason)}",
        "confirmTitle=${trimmed(confirmationTitle)}",
        "confirmBody=${trimmed(confirmationBody)}",
    ),
)

fun ActionSpec.commandRevision(): String? {
    return when (this) {
        is ActionSpec.DeckActionSpec -> action.commandRevision()
        is ActionSpec.ShellCommand -> commandRevision(
            command = command,
            targetSelector = targetSelector,
            origin = commandOrigin,
            dangerous = dangerous,
            riskReason = riskReason,
            confirmationTitle = confirmationTitle,
            confirmationBody = confirmationBody,
        )
        is ActionSpec.CatalogAction,
        is ActionSpec.LocalRoute -> null
    }
}

fun DeckAction.commandRevision(): String? = command?.let {
    commandRevision(
        command = it,
        targetSelector = targetSelector,
        origin = commandOrigin,
        dangerous = dangerous,
        riskReason = riskReason,
        confirmationTitle = confirmationTitle,
        confirmationBody = confirmationBody,
    )
}

private fun canonicalSha256(parts: List<String>): String {
    val input = parts.joinToString("\u0000")
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun TargetSelector.canonicalToken(): String = when (this) {
    TargetSelector.CurrentDevice -> "current"
    TargetSelector.AllCompatibleDevices -> "all"
    TargetSelector.AskAtRunTime -> "ask"
    is TargetSelector.SpecificDevice -> "specific:${deviceId.value}"
    is TargetSelector.DeviceGroup -> "group:${groupId.value}"
}

private fun trimmed(value: String?): String = value.orEmpty().trim().ifEmpty { NULL_TOKEN }
