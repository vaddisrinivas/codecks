package io.codecks.domain.connection

private const val UnknownToolCode = "unknown"
private val StableCodePattern = Regex("[a-z][a-z0-9_.-]{0,63}")

enum class ConnectionIssueSeverity(val persistedCode: String) {
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
}

sealed interface RemediationAction {
    data object RequestBluetoothPermission : RemediationAction
    data object OpenBluetoothSettings : RemediationAction
    data object OpenSystemPairing : RemediationAction
    data object RetryHidRegistration : RemediationAction
    data object RetryConnectionNow : RemediationAction
    data object OpenMacWakeHelp : RemediationAction
    data object ReenterSshCredentials : RemediationAction
    data object ReviewChangedHostKey : RemediationAction

    data class OpenMissingToolInstructions(val toolCode: String) : RemediationAction {
        init {
            require(toolCode.matches(StableCodePattern)) { "Tool code must be a stable code." }
        }
    }

    data object ContactSupport : RemediationAction
}

enum class ConnectionIssueCode(
    val persistedCode: String,
    val severity: ConnectionIssueSeverity,
    val remediations: List<RemediationAction>,
) {
    BLUETOOTH_PERMISSION_DENIED(
        persistedCode = "bluetooth_permission_denied",
        severity = ConnectionIssueSeverity.ERROR,
        remediations = listOf(RemediationAction.RequestBluetoothPermission),
    ),
    BLUETOOTH_DISABLED(
        persistedCode = "bluetooth_disabled",
        severity = ConnectionIssueSeverity.ERROR,
        remediations = listOf(RemediationAction.OpenBluetoothSettings),
    ),
    HID_PROFILE_UNREGISTERED(
        persistedCode = "hid_profile_unregistered",
        severity = ConnectionIssueSeverity.ERROR,
        remediations = listOf(RemediationAction.RetryHidRegistration),
    ),
    HID_PROFILE_REGISTRATION_FAILED(
        persistedCode = "hid_profile_registration_failed",
        severity = ConnectionIssueSeverity.ERROR,
        remediations = listOf(
            RemediationAction.RetryHidRegistration,
            RemediationAction.ContactSupport,
        ),
    ),
    HOST_UNPAIRED(
        persistedCode = "host_unpaired",
        severity = ConnectionIssueSeverity.ERROR,
        remediations = listOf(RemediationAction.OpenSystemPairing),
    ),
    CONNECTING(
        persistedCode = "connecting",
        severity = ConnectionIssueSeverity.INFO,
        remediations = listOf(RemediationAction.RetryConnectionNow),
    ),
    CONNECT_BACKOFF(
        persistedCode = "connect_backoff",
        severity = ConnectionIssueSeverity.WARNING,
        remediations = listOf(RemediationAction.RetryConnectionNow),
    ),
    MAC_OFFLINE_OR_ASLEEP(
        persistedCode = "mac_offline_or_asleep",
        severity = ConnectionIssueSeverity.WARNING,
        remediations = listOf(
            RemediationAction.OpenMacWakeHelp,
            RemediationAction.RetryConnectionNow,
        ),
    ),
    SSH_AUTH_FAILED(
        persistedCode = "ssh_auth_failed",
        severity = ConnectionIssueSeverity.ERROR,
        remediations = listOf(RemediationAction.ReenterSshCredentials),
    ),
    SSH_HOST_KEY_MISMATCH(
        persistedCode = "ssh_host_key_mismatch",
        severity = ConnectionIssueSeverity.ERROR,
        remediations = listOf(RemediationAction.ReviewChangedHostKey),
    ),
    MAC_TOOL_MISSING(
        persistedCode = "mac_tool_missing",
        severity = ConnectionIssueSeverity.ERROR,
        remediations = listOf(RemediationAction.OpenMissingToolInstructions(UnknownToolCode)),
    ),
    UNKNOWN(
        persistedCode = "unknown",
        severity = ConnectionIssueSeverity.WARNING,
        remediations = listOf(RemediationAction.ContactSupport),
    ),
    ;

    companion object {
        fun fromPersistedCode(value: String?): ConnectionIssueCode =
            entries.firstOrNull { it.persistedCode == value } ?: UNKNOWN
    }
}

val RemediationAction.persistedCode: String
    get() = when (this) {
        RemediationAction.RequestBluetoothPermission -> "request_bluetooth_permission"
        RemediationAction.OpenBluetoothSettings -> "open_bluetooth_settings"
        RemediationAction.OpenSystemPairing -> "open_system_pairing"
        RemediationAction.RetryHidRegistration -> "retry_hid_registration"
        RemediationAction.RetryConnectionNow -> "retry_connection_now"
        RemediationAction.OpenMacWakeHelp -> "open_mac_wake_help"
        RemediationAction.ReenterSshCredentials -> "reenter_ssh_credentials"
        RemediationAction.ReviewChangedHostKey -> "review_changed_host_key"
        is RemediationAction.OpenMissingToolInstructions -> "open_missing_tool_instructions"
        RemediationAction.ContactSupport -> "contact_support"
    }
