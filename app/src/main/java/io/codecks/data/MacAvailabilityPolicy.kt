package io.codecks.data

import io.codecks.domain.connection.ConnectionIssueCode

enum class MacAvailabilitySignal {
    BluetoothTransportLost,
    HostTemporarilyUnavailable,
    MacSleepingOrOffline,
    BluetoothReavailable,
    HostReavailable,
    MacAwake,
    PermissionDenied,
    HostUnpaired,
    AuthenticationFailed,
    HostKeyMismatch,
    RequiredToolMissing,
}

enum class ConnectionRetryClass {
    Transient,
    RepairRequired,
    Reavailable,
}

data class MacAvailabilityDecision(
    val retryClass: ConnectionRetryClass,
    val issueCode: ConnectionIssueCode?,
    val automaticRetryAllowed: Boolean,
    val scheduleHealthCheck: Boolean,
)

fun classifyMacAvailability(signal: MacAvailabilitySignal): MacAvailabilityDecision = when (signal) {
    MacAvailabilitySignal.BluetoothTransportLost -> MacAvailabilityDecision(
        retryClass = ConnectionRetryClass.Transient,
        issueCode = ConnectionIssueCode.BLUETOOTH_DISABLED,
        automaticRetryAllowed = true,
        scheduleHealthCheck = false,
    )
    MacAvailabilitySignal.HostTemporarilyUnavailable,
    MacAvailabilitySignal.MacSleepingOrOffline,
    -> MacAvailabilityDecision(
        retryClass = ConnectionRetryClass.Transient,
        issueCode = ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP,
        automaticRetryAllowed = true,
        scheduleHealthCheck = false,
    )
    MacAvailabilitySignal.BluetoothReavailable,
    MacAvailabilitySignal.HostReavailable,
    MacAvailabilitySignal.MacAwake,
    -> MacAvailabilityDecision(
        retryClass = ConnectionRetryClass.Reavailable,
        issueCode = null,
        automaticRetryAllowed = false,
        scheduleHealthCheck = true,
    )
    MacAvailabilitySignal.PermissionDenied -> MacAvailabilityDecision(
        retryClass = ConnectionRetryClass.RepairRequired,
        issueCode = ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
        automaticRetryAllowed = false,
        scheduleHealthCheck = false,
    )
    MacAvailabilitySignal.HostUnpaired -> MacAvailabilityDecision(
        retryClass = ConnectionRetryClass.RepairRequired,
        issueCode = ConnectionIssueCode.HOST_UNPAIRED,
        automaticRetryAllowed = false,
        scheduleHealthCheck = false,
    )
    MacAvailabilitySignal.AuthenticationFailed -> MacAvailabilityDecision(
        retryClass = ConnectionRetryClass.RepairRequired,
        issueCode = ConnectionIssueCode.SSH_AUTH_FAILED,
        automaticRetryAllowed = false,
        scheduleHealthCheck = false,
    )
    MacAvailabilitySignal.HostKeyMismatch -> MacAvailabilityDecision(
        retryClass = ConnectionRetryClass.RepairRequired,
        issueCode = ConnectionIssueCode.SSH_HOST_KEY_MISMATCH,
        automaticRetryAllowed = false,
        scheduleHealthCheck = false,
    )
    MacAvailabilitySignal.RequiredToolMissing -> MacAvailabilityDecision(
        retryClass = ConnectionRetryClass.RepairRequired,
        issueCode = ConnectionIssueCode.MAC_TOOL_MISSING,
        automaticRetryAllowed = false,
        scheduleHealthCheck = false,
    )
}
