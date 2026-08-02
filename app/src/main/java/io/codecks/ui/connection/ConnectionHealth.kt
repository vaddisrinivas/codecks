package io.codecks.ui.connection

import io.codecks.data.ConnectionConfig
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.domain.connection.RemediationAction

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

enum class ConnectionHealthRetryClass {
    None,
    Transient,
    RepairRequired,
}

data class ConnectionHealth(
    val kind: ConnectionHealthKind,
    val title: String,
    val detail: String,
    val actionHint: String? = null,
    val issueOverride: ConnectionIssueCode? = null,
)

val ConnectionHealth.isReady: Boolean
    get() = kind == ConnectionHealthKind.Ready

val ConnectionHealth.retryClass: ConnectionHealthRetryClass
    get() {
        if (issueOverride == ConnectionIssueCode.MAC_TOOL_MISSING ||
            issueOverride == ConnectionIssueCode.UNKNOWN
        ) {
            return ConnectionHealthRetryClass.RepairRequired
        }
        return when (kind) {
            ConnectionHealthKind.Offline -> ConnectionHealthRetryClass.Transient
            ConnectionHealthKind.AuthFailed,
            ConnectionHealthKind.FingerprintMismatch,
            ConnectionHealthKind.NotConfigured,
            ConnectionHealthKind.NeedsFingerprint,
            ConnectionHealthKind.NeedsKey,
            -> ConnectionHealthRetryClass.RepairRequired
            ConnectionHealthKind.Scanning,
            ConnectionHealthKind.Verifying,
            ConnectionHealthKind.Connecting,
            ConnectionHealthKind.Testing,
            ConnectionHealthKind.Ready,
            -> ConnectionHealthRetryClass.None
        }
    }

val ConnectionHealth.issueCode: ConnectionIssueCode?
    get() = issueOverride ?: when (kind) {
        ConnectionHealthKind.Scanning,
        ConnectionHealthKind.Verifying,
        ConnectionHealthKind.Connecting,
        ConnectionHealthKind.Testing,
        -> ConnectionIssueCode.CONNECTING
        ConnectionHealthKind.AuthFailed -> ConnectionIssueCode.SSH_AUTH_FAILED
        ConnectionHealthKind.FingerprintMismatch -> ConnectionIssueCode.SSH_HOST_KEY_MISMATCH
        ConnectionHealthKind.Offline -> ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP
        ConnectionHealthKind.NotConfigured,
        ConnectionHealthKind.Ready,
        ConnectionHealthKind.NeedsFingerprint,
        ConnectionHealthKind.NeedsKey,
        -> null
    }

val ConnectionHealth.remediations: List<RemediationAction>
    get() = issueCode?.remediations.orEmpty()

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

fun ConnectionUiState.connectionHealth(
    nowEpochMs: Long = System.currentTimeMillis(),
): ConnectionHealth {
    val configuredHealth = connectionHealth(config = config, operation = operation, error = error)
    if (!configuredHealth.isReady) return configuredHealth
    val receipt = sshTerminalReceipt
    val targetMatches = receipt?.macTargetId == config.setupTargetId()
    val revisionMatches = receipt?.setupRevision == setupSnapshot.revisionToken()
    val currentCapabilities = macCapabilityReceipts
        .filter { it.result == io.codecks.domain.connection.MacCapabilityCheckResult.Passed }
        .associate { it.capability to it.checkedAtEpochMs }
    val capabilityProofCurrent = receipt != null &&
        receipt.confirmedCapabilities.containsAll(REQUIRED_CORE_MAC_CAPABILITIES) &&
        receipt.completedAtEpochMs.isFreshSetupProofAt(nowEpochMs) &&
        currentCapabilities == receipt.capabilityCheckedAtEpochMs &&
        macCapabilityReceipts.all { it.checkedAtEpochMs.isFreshSetupProofAt(nowEpochMs) }
    return if (targetMatches && revisionMatches && capabilityProofCurrent) {
        configuredHealth.copy(
            detail = "Mac controls were verified recently. Password is not stored.",
        )
    } else {
        ConnectionHealth(
            kind = ConnectionHealthKind.Offline,
            title = "Mac controls need verification",
            detail = "Saved setup exists, but no current live connection proof is available.",
            actionHint = "Test connection.",
        )
    }
}

internal fun ConnectionUiState.nextSetupProofExpiryAtEpochMs(): Long? = buildList {
    sshTerminalReceipt?.completedAtEpochMs?.let {
        add(it + SETUP_TERMINAL_RECEIPT_MAX_AGE_MS + 1L)
    }
    macCapabilityReceipts.forEach {
        add(it.checkedAtEpochMs + SETUP_TERMINAL_RECEIPT_MAX_AGE_MS + 1L)
    }
}.minOrNull()

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
        "backoff" in normalized ||
            "retry after" in normalized -> ConnectionHealth(
                kind = ConnectionHealthKind.Offline,
                title = "Waiting before retry",
                detail = "Codecks is waiting before the next connection attempt.",
                actionHint = "Retry now if the Mac is available.",
                issueOverride = ConnectionIssueCode.CONNECT_BACKOFF,
            )
        "command not found" in normalized ||
            "required tool" in normalized ||
            "missing tool" in normalized -> ConnectionHealth(
                kind = ConnectionHealthKind.NotConfigured,
                title = "Mac tool missing",
                detail = "Install the required Mac tool, then test again.",
                actionHint = "Install the required Mac tool, then test again.",
                issueOverride = ConnectionIssueCode.MAC_TOOL_MISSING,
            )
        "fingerprint" in normalized ||
            "host key" in normalized ||
            "mac identity changed" in normalized ||
            "man-in-the-middle" in normalized ||
            "remote host identification" in normalized -> ConnectionHealth(
                kind = ConnectionHealthKind.FingerprintMismatch,
                title = "Mac trust changed",
                detail = "The saved Mac identity no longer matches.",
                actionHint = "Reset trust only after checking the Mac.",
            )
        "permission denied" in normalized ||
            "authentication" in normalized ||
            "password" in normalized -> ConnectionHealth(
                kind = ConnectionHealthKind.AuthFailed,
                title = "Mac login failed",
                detail = "The saved username or control key was rejected.",
                actionHint = "Check username/password or rotate the key.",
            )
        "timeout" in normalized ||
            "timed out" in normalized ||
            "no route" in normalized ||
            "unreachable" in normalized ||
            "refused" in normalized ||
            "connection closed" in normalized ||
            "asleep" in normalized ||
            "offline" in normalized ||
            "network" in normalized -> ConnectionHealth(
            kind = ConnectionHealthKind.Offline,
            title = "Mac unreachable",
            detail = "The Mac may be asleep, offline, or unavailable on this network.",
            actionHint = "Check network, Remote Login, then test again.",
        )
        else -> ConnectionHealth(
            kind = ConnectionHealthKind.Offline,
            title = "Connection needs attention",
            detail = "Codecks could not identify a safe automatic repair.",
            actionHint = "Review setup or contact support.",
            issueOverride = ConnectionIssueCode.UNKNOWN,
        )
    }
}
