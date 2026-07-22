package io.codecks.ui.connection

import io.codecks.data.ConnectionConfig

enum class ConnectionHealthKind {
    NotConfigured,
    Scanning,
    Verifying,
    Connecting,
    Testing,
    Ready,
    NeedsFingerprint,
    NeedsKey,
    AuthFailed,
    FingerprintMismatch,
    Offline,
}

data class ConnectionHealth(
    val kind: ConnectionHealthKind,
    val title: String,
    val detail: String,
    val actionHint: String? = null,
)

val ConnectionHealth.isReady: Boolean
    get() = kind == ConnectionHealthKind.Ready

fun ConnectionHealth.statusLabel(): String =
    when (kind) {
        ConnectionHealthKind.NotConfigured -> "Setup needed"
        ConnectionHealthKind.Scanning -> "Checking…"
        ConnectionHealthKind.Verifying -> "Checking…"
        ConnectionHealthKind.Connecting -> "Connecting…"
        ConnectionHealthKind.Testing -> "Checking…"
        ConnectionHealthKind.Ready -> "Ready"
        ConnectionHealthKind.NeedsFingerprint -> "Setup needed"
        ConnectionHealthKind.NeedsKey -> "Setup needed"
        ConnectionHealthKind.AuthFailed -> "Failed"
        ConnectionHealthKind.FingerprintMismatch -> "Failed"
        ConnectionHealthKind.Offline -> "Offline"
    }

fun simpleConnectionHealth(connectionReady: Boolean): ConnectionHealth =
    if (connectionReady) {
        ConnectionHealth(
            kind = ConnectionHealthKind.Ready,
            title = "Ready for Mac controls",
            detail = "Saved connection key is active.",
        )
    } else {
        ConnectionHealth(
            kind = ConnectionHealthKind.NotConfigured,
            title = "Mac not configured",
            detail = "Add your Mac once to run Deck, Clipboard, and Rules.",
            actionHint = "Open connection setup.",
        )
    }

fun connectionHealth(
    config: ConnectionConfig,
    operation: ConnectionOperation,
    error: String?,
): ConnectionHealth {
    operation.toHealthOrNull()?.let { return it }
    error?.toConnectionHealth()?.let { return it }
    return when {
        !config.isConfigured -> ConnectionHealth(
            kind = ConnectionHealthKind.NotConfigured,
            title = "Mac not configured",
            detail = "Add your Mac once to run Deck, Clipboard, and Rules.",
            actionHint = "Scan or enter your Mac hostname.",
        )
        config.hostKey.isBlank() -> ConnectionHealth(
            kind = ConnectionHealthKind.NeedsFingerprint,
            title = "Mac not trusted",
            detail = "Trust this Mac before saving it.",
            actionHint = "Trust this Mac.",
        )
        !config.hasKey -> ConnectionHealth(
            kind = ConnectionHealthKind.NeedsKey,
            title = "Control key not installed",
            detail = "Install the Codecks SSH key once using your Mac password.",
            actionHint = "Save Mac connection.",
        )
        else -> ConnectionHealth(
            kind = ConnectionHealthKind.Ready,
            title = "Ready for Mac controls",
            detail = "Saved connection key is active. Password is not stored.",
            actionHint = "Test connection if controls stop responding.",
        )
    }
}

fun ConnectionUiState.connectionHealth(): ConnectionHealth =
    connectionHealth(config = config, operation = operation, error = error)

private fun ConnectionOperation.toHealthOrNull(): ConnectionHealth? =
    when (this) {
        ConnectionOperation.Idle -> null
        ConnectionOperation.Scanning -> ConnectionHealth(
            kind = ConnectionHealthKind.Scanning,
            title = "Looking for your Mac",
            detail = "Checking this network for Macs with Remote Login.",
        )
        ConnectionOperation.Verifying -> ConnectionHealth(
            kind = ConnectionHealthKind.Verifying,
            title = "Checking this Mac",
            detail = "Confirm this is your Mac before saving it.",
        )
        ConnectionOperation.Connecting -> ConnectionHealth(
            kind = ConnectionHealthKind.Connecting,
            title = "Securing this connection",
            detail = "Installing or rotating the Codecks control key.",
        )
        ConnectionOperation.Testing -> ConnectionHealth(
            kind = ConnectionHealthKind.Testing,
            title = "Testing Mac controls",
            detail = "Checking SSH and command execution.",
        )
    }

private fun String.toConnectionHealth(): ConnectionHealth {
    val normalized = lowercase()
    return when {
        "fingerprint" in normalized ||
            "host key" in normalized ||
            "man-in-the-middle" in normalized ||
            "remote host identification" in normalized -> ConnectionHealth(
                kind = ConnectionHealthKind.FingerprintMismatch,
                title = "Mac trust changed",
                detail = this,
                actionHint = "Reset trust only after checking the Mac.",
            )
        "permission denied" in normalized ||
            "authentication" in normalized ||
            "password" in normalized -> ConnectionHealth(
                kind = ConnectionHealthKind.AuthFailed,
                title = "Mac login failed",
                detail = this,
                actionHint = "Check username/password or rotate the key.",
            )
        else -> ConnectionHealth(
            kind = ConnectionHealthKind.Offline,
            title = "Mac unreachable",
            detail = this,
            actionHint = "Check network, Remote Login, then test again.",
        )
    }
}
