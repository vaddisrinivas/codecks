package io.codecks.ui.connection

import io.codecks.data.ConnectionConfig
import io.codecks.domain.connection.ConnectionIssueCode

data class ConnectionDiagnostic(
    val state: ConnectionPresentationState,
    val title: String,
    val detail: String,
    val issueCode: ConnectionIssueCode?,
    val attempt: Int = 0,
    val retryInSeconds: Int = 0,
    val repairActions: List<ConnectionRepair> = emptyList(),
    val supportCode: ConnectionSupportCode,
)

fun presentConnectionDiagnostic(
    config: ConnectionConfig,
    operation: ConnectionOperation,
    issueCode: ConnectionIssueCode?,
    attempt: Int = 0,
    retryAtMillis: Long = 0L,
    nowMillis: Long = 0L,
): ConnectionDiagnostic = presentConnectionDiagnostic(
    health = diagnosticHealth(config, operation, issueCode),
    attempt = attempt,
    retryAtMillis = retryAtMillis,
    nowMillis = nowMillis,
)

private fun presentConnectionDiagnostic(
    health: ConnectionHealth,
    attempt: Int,
    retryAtMillis: Long,
    nowMillis: Long,
): ConnectionDiagnostic {
    val presentation = health.toUnifiedConnectionPresentation()
    val safeAttempt = attempt.coerceAtLeast(0)
    val retryInSeconds = ((retryAtMillis - nowMillis).coerceAtLeast(0L) / 1_000L).toInt()
    return ConnectionDiagnostic(
        state = presentation.state,
        title = presentation.title,
        detail = presentation.detail,
        issueCode = health.issueCode,
        attempt = if (presentation.state in setOf(ConnectionPresentationState.Checking, ConnectionPresentationState.Reconnecting)) {
            safeAttempt.coerceAtLeast(1)
        } else {
            safeAttempt
        },
        retryInSeconds = retryInSeconds.takeIf {
            presentation.state == ConnectionPresentationState.Reconnecting
        } ?: 0,
        repairActions = presentation.repairs,
        supportCode = presentation.supportCode,
    )
}

fun ConnectionUiState.connectionDiagnostic(nowMillis: Long = 0L): ConnectionDiagnostic =
    presentConnectionDiagnostic(
        health = connectionHealth(),
        attempt = connectionAttempt,
        retryAtMillis = retryAtMillis,
        nowMillis = nowMillis,
    )

private fun diagnosticHealth(
    config: ConnectionConfig,
    operation: ConnectionOperation,
    issueCode: ConnectionIssueCode?,
): ConnectionHealth {
    operation.diagnosticOperationHealth()?.let { operationHealth ->
        return operationHealth.copy(issueOverride = issueCode ?: operationHealth.issueCode)
    }
    issueCode?.let { issue ->
        return ConnectionHealth(
            kind = when (issue) {
                ConnectionIssueCode.SSH_AUTH_FAILED,
                ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
                ConnectionIssueCode.HOST_UNPAIRED,
                -> ConnectionHealthKind.AuthFailed
                ConnectionIssueCode.SSH_HOST_KEY_MISMATCH -> ConnectionHealthKind.FingerprintMismatch
                ConnectionIssueCode.CONNECTING -> ConnectionHealthKind.Connecting
                else -> ConnectionHealthKind.Offline
            },
            title = "Connection status",
            detail = "Connection status is available through the redacted presentation.",
            issueOverride = issue,
        )
    }
    return when {
        !config.isConfigured -> ConnectionHealth(ConnectionHealthKind.NotConfigured, "", "")
        config.hostKey.isBlank() -> ConnectionHealth(ConnectionHealthKind.NeedsFingerprint, "", "")
        !config.hasKey -> ConnectionHealth(ConnectionHealthKind.NeedsKey, "", "")
        else -> ConnectionHealth(ConnectionHealthKind.Ready, "", "")
    }
}

private fun ConnectionOperation.diagnosticOperationHealth(): ConnectionHealth? = when (this) {
    ConnectionOperation.Idle -> null
    ConnectionOperation.Scanning -> ConnectionHealth(ConnectionHealthKind.Scanning, "", "")
    ConnectionOperation.Verifying -> ConnectionHealth(ConnectionHealthKind.Verifying, "", "")
    ConnectionOperation.Connecting -> ConnectionHealth(ConnectionHealthKind.Connecting, "", "")
    ConnectionOperation.Testing -> ConnectionHealth(ConnectionHealthKind.Testing, "", "")
}
