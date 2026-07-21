package io.codecks.ui.connection

import io.codecks.HidLifecycle
import io.codecks.HidState

enum class HidHealthKind {
    PermissionMissing,
    Unavailable,
    Stopped,
    Starting,
    ReadyNoTarget,
    ReadyToConnect,
    Reconnecting,
    Connecting,
    Connected,
    Failed,
}

data class HidHealth(
    val kind: HidHealthKind,
    val title: String,
    val detail: String,
    val actionHint: String? = null,
)

val HidHealth.canSendInput: Boolean
    get() = kind == HidHealthKind.Connected

fun HidHealth.statusLabel(): String =
    when (kind) {
        HidHealthKind.PermissionMissing -> "Allow"
        HidHealthKind.Unavailable -> "Unavailable"
        HidHealthKind.Stopped -> "Start"
        HidHealthKind.Starting -> "Starting"
        HidHealthKind.ReadyNoTarget -> "Choose"
        HidHealthKind.ReadyToConnect -> "Configured"
        HidHealthKind.Reconnecting -> "Retrying"
        HidHealthKind.Connecting -> "Connecting"
        HidHealthKind.Connected -> "Connected"
        HidHealthKind.Failed -> "Failed"
    }

fun HidState.hidHealth(permissionGranted: Boolean): HidHealth {
    val normalized = status.lowercase()
    val target = selectedHidHostLabel()

    return when {
        !permissionGranted || lifecycle == HidLifecycle.PermissionMissing -> HidHealth(
            kind = HidHealthKind.PermissionMissing,
            title = "Bluetooth permission needed",
            detail = "Allow Bluetooth so Codecks can use Trackpad mouse and keyboard.",
            actionHint = "Allow Bluetooth permission.",
        )
        lifecycle == HidLifecycle.Unavailable || normalized == "bluetooth unavailable" -> HidHealth(
            kind = HidHealthKind.Unavailable,
            title = "Bluetooth unavailable",
            detail = "This device cannot expose the HID mouse and keyboard service right now.",
            actionHint = "Check Bluetooth and device support.",
        )
        lifecycle == HidLifecycle.Failed || "failed" in normalized -> HidHealth(
            kind = HidHealthKind.Failed,
            title = "Bluetooth HID failed",
            detail = status.ifBlank { "The HID service failed to start or connect." },
            actionHint = "Restart Bluetooth HID.",
        )
        isConnected || lifecycle == HidLifecycle.Connected -> HidHealth(
            kind = HidHealthKind.Connected,
            title = target?.let { "Connected to $it" } ?: "Bluetooth target connected",
            detail = "Trackpad and keyboard events can send to the saved HID target.",
        )
        normalized.startsWith("connecting ") -> HidHealth(
            kind = HidHealthKind.Connecting,
            title = target?.let { "Connecting to $it" } ?: "Connecting Bluetooth target",
            detail = status,
        )
        normalized.startsWith("reconnecting ") || reconnectAttempt > 0 -> HidHealth(
            kind = HidHealthKind.Reconnecting,
            title = target?.let { "Reconnecting to $it" } ?: "Reconnecting Bluetooth target",
            detail = nextReconnectDetail(),
            actionHint = "Keep Codecks open once; the foreground session keeps retrying.",
        )
        lifecycle == HidLifecycle.Opening ||
            "opening" in normalized ||
            "registering" in normalized -> HidHealth(
            kind = HidHealthKind.Starting,
            title = "Starting Bluetooth HID",
            detail = "Registering Codecks as a Bluetooth mouse and keyboard.",
        )
        !isReady -> HidHealth(
            kind = HidHealthKind.Stopped,
            title = "Bluetooth HID stopped",
            detail = "Start the HID service before using Trackpad mouse and keyboard.",
            actionHint = "Start Bluetooth HID.",
        )
        selectedHostAddress == null && hosts.isEmpty() -> HidHealth(
            kind = HidHealthKind.ReadyNoTarget,
            title = "Pair a Mac target",
            detail = "HID service is ready, but no compatible paired Mac target is saved.",
            actionHint = "Pair your Mac, then refresh targets.",
        )
        selectedHostAddress == null -> HidHealth(
            kind = HidHealthKind.ReadyNoTarget,
            title = "Choose a Mac target",
            detail = "HID service is ready. Pick which paired Mac should receive Trackpad input.",
            actionHint = "Choose a Bluetooth target.",
        )
        else -> HidHealth(
            kind = HidHealthKind.ReadyToConnect,
            title = target?.let { "$it configured" } ?: "Bluetooth target configured",
            detail = "Saved HID target is configured but not connected.",
            actionHint = "Tap to reconnect.",
        )
    }
}

private fun HidState.selectedHidHostLabel(): String? =
    selectedHostAddress?.let { selected ->
        hosts.firstOrNull { it.address == selected }?.label
    }?.substringBefore(" (")
        ?.ifBlank { null }

private fun HidState.nextReconnectDetail(): String {
    val now = System.currentTimeMillis()
    val retryInSeconds = ((nextReconnectAtMillis - now).coerceAtLeast(0L) / 1_000L).toInt()
    return if (retryInSeconds > 0) {
        "Auto reconnect attempt $reconnectAttempt. Next retry in ${retryInSeconds}s."
    } else {
        "Auto reconnect attempt $reconnectAttempt is ready."
    }
}
