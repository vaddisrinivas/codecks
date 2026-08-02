package io.codecks.ui.connection

import io.codecks.data.ConnectionConfig
import io.codecks.domain.connection.ConnectionIssueCode

enum class ConnectionDiagnosticState {
    SetupRequired,
    TrustRequired,
    KeyRequired,
    Connecting,
    Backoff,
    Ready,
    Offline,
    AuthenticationFailed,
    HostKeyMismatch,
    ToolMissing,
    Unknown,
}

enum class ConnectionRepairAction(val label: String) {
    FindMac("Find Mac"),
    TrustMac("Trust this Mac"),
    InstallControlKey("Save Mac"),
    RetryNow("Retry now"),
    ReenterCredentials("Re-enter credentials"),
    ReviewHostKey("Review Mac trust"),
    InstallRequiredTool("Install required Mac tool"),
    OpenSupport("Open support"),
}

data class ConnectionDiagnostic(
    val state: ConnectionDiagnosticState,
    val title: String,
    val detail: String,
    val issueCode: ConnectionIssueCode?,
    val attempt: Int = 0,
    val retryInSeconds: Int = 0,
    val repairActions: List<ConnectionRepairAction> = emptyList(),
)

fun presentConnectionDiagnostic(
    config: ConnectionConfig,
    operation: ConnectionOperation,
    issueCode: ConnectionIssueCode?,
    attempt: Int = 0,
    retryAtMillis: Long = 0L,
    nowMillis: Long = 0L,
): ConnectionDiagnostic {
    val safeAttempt = attempt.coerceAtLeast(0)
    val retryInSeconds = ((retryAtMillis - nowMillis).coerceAtLeast(0L) / 1_000L).toInt()
    if (operation != ConnectionOperation.Idle) {
        val operationLabel = when (operation) {
            ConnectionOperation.Idle -> error("Idle handled before operation presentation.")
            ConnectionOperation.Scanning -> "Looking for your Mac"
            ConnectionOperation.Verifying -> "Checking Mac identity"
            ConnectionOperation.Connecting -> "Connecting securely"
            ConnectionOperation.Testing -> "Testing Mac controls"
        }
        return ConnectionDiagnostic(
            state = ConnectionDiagnosticState.Connecting,
            title = operationLabel,
            detail = safeAttempt.takeIf { it > 0 }?.let { "Connection attempt $it is running." }
                ?: "Connection check is running.",
            issueCode = ConnectionIssueCode.CONNECTING,
            attempt = safeAttempt,
        )
    }
    issueCode?.let { issue ->
        return when (issue) {
            ConnectionIssueCode.CONNECTING -> ConnectionDiagnostic(
                ConnectionDiagnosticState.Connecting,
                "Connecting securely",
                "Connection attempt ${safeAttempt.coerceAtLeast(1)} is running.",
                issue,
                attempt = safeAttempt.coerceAtLeast(1),
            )
            ConnectionIssueCode.CONNECT_BACKOFF -> ConnectionDiagnostic(
                ConnectionDiagnosticState.Backoff,
                "Waiting before retry",
                if (retryInSeconds > 0) {
                    "Attempt ${safeAttempt.coerceAtLeast(1)} paused for ${retryInSeconds}s."
                } else {
                    "Attempt ${safeAttempt.coerceAtLeast(1)} can retry now."
                },
                issue,
                attempt = safeAttempt.coerceAtLeast(1),
                retryInSeconds = retryInSeconds,
                repairActions = listOf(ConnectionRepairAction.RetryNow),
            )
            ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP,
            ConnectionIssueCode.BLUETOOTH_DISABLED,
            -> ConnectionDiagnostic(
                ConnectionDiagnosticState.Offline,
                "Mac is unavailable",
                "Wake the Mac and check Bluetooth, Wi-Fi, and Remote Login.",
                issue,
                repairActions = listOf(ConnectionRepairAction.RetryNow),
            )
            ConnectionIssueCode.SSH_AUTH_FAILED,
            ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
            ConnectionIssueCode.HOST_UNPAIRED,
            -> ConnectionDiagnostic(
                ConnectionDiagnosticState.AuthenticationFailed,
                "Connection permission failed",
                "Check the saved Mac, pairing, username, and control key.",
                issue,
                repairActions = listOf(ConnectionRepairAction.ReenterCredentials),
            )
            ConnectionIssueCode.SSH_HOST_KEY_MISMATCH -> ConnectionDiagnostic(
                ConnectionDiagnosticState.HostKeyMismatch,
                "Mac identity changed",
                "Do not reconnect until you confirm this is the same Mac.",
                issue,
                repairActions = listOf(ConnectionRepairAction.ReviewHostKey),
            )
            ConnectionIssueCode.MAC_TOOL_MISSING -> ConnectionDiagnostic(
                ConnectionDiagnosticState.ToolMissing,
                "Required Mac tool is missing",
                "Install the required tool on the Mac, then test again.",
                issue,
                repairActions = listOf(ConnectionRepairAction.InstallRequiredTool),
            )
            ConnectionIssueCode.HID_TRANSPORT_TIMEOUT -> ConnectionDiagnostic(
                ConnectionDiagnosticState.Offline,
                "Bluetooth service timed out",
                "Toggle Bluetooth off and on, then retry Bluetooth input.",
                issue,
                repairActions = listOf(ConnectionRepairAction.RetryNow),
            )
            ConnectionIssueCode.HID_PROFILE_UNREGISTERED,
            ConnectionIssueCode.HID_PROFILE_REGISTRATION_FAILED,
            ConnectionIssueCode.UNKNOWN,
            -> ConnectionDiagnostic(
                ConnectionDiagnosticState.Unknown,
                "Connection needs attention",
                "Codecks could not identify a safe automatic repair.",
                issue,
                repairActions = listOf(ConnectionRepairAction.OpenSupport),
            )
        }
    }
    return when {
        !config.isConfigured -> ConnectionDiagnostic(
            ConnectionDiagnosticState.SetupRequired,
            "Mac not configured",
            "Find or enter your Mac to begin setup.",
            issueCode = null,
            repairActions = listOf(ConnectionRepairAction.FindMac),
        )
        config.hostKey.isBlank() -> ConnectionDiagnostic(
            ConnectionDiagnosticState.TrustRequired,
            "Mac identity not confirmed",
            "Confirm the Mac fingerprint before installing a control key.",
            issueCode = null,
            repairActions = listOf(ConnectionRepairAction.TrustMac),
        )
        !config.hasKey -> ConnectionDiagnostic(
            ConnectionDiagnosticState.KeyRequired,
            "Control key not installed",
            "Authorize once to install the Codecks control key.",
            issueCode = null,
            repairActions = listOf(ConnectionRepairAction.InstallControlKey),
        )
        else -> ConnectionDiagnostic(
            ConnectionDiagnosticState.Ready,
            "Mac controls ready",
            "The saved Mac identity and control key are active.",
            issueCode = null,
        )
    }
}

fun ConnectionUiState.connectionDiagnostic(nowMillis: Long = 0L): ConnectionDiagnostic = presentConnectionDiagnostic(
    config = config,
    operation = operation,
    issueCode = connectionHealth().issueCode,
    attempt = connectionAttempt,
    retryAtMillis = retryAtMillis,
    nowMillis = nowMillis,
)
