package io.codecks.core.reactive

import io.codecks.domain.DeckAction
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.reactive.ActionRevision
import java.security.MessageDigest

/** Stable revision of the resolved action contract, excluding prior authorization state. */
internal fun DeckAction.reactiveActionRevision(): ActionRevision = ActionRevision(
    sha256Hex(
        listOf(
            id,
            label,
            kind.name,
            icon.name,
            description,
            route.orEmpty(),
            command.orEmpty(),
            testCommand.orEmpty(),
            dangerous.toString(),
            liveSafe.toString(),
            requiresTest.toString(),
            targetSelector.canonicalToken(),
            commandOrigin.name,
            confirmationTitle.orEmpty(),
            confirmationBody.orEmpty(),
            riskReason.orEmpty(),
        ).joinToString("\u0000"),
    ),
)

private fun TargetSelector.canonicalToken(): String = when (this) {
    TargetSelector.CurrentDevice -> "current"
    TargetSelector.AllCompatibleDevices -> "all"
    TargetSelector.AskAtRunTime -> "ask"
    is TargetSelector.SpecificDevice -> "specific:${deviceId.value}"
    is TargetSelector.DeviceGroup -> "group:${groupId.value}"
}

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
