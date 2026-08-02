package io.codecks.domain.connection

private val CapabilityCodePattern = Regex("[a-z][a-z0-9_.-]{0,63}")

enum class CapabilityStatus(val persistedCode: String) {
    SATISFIED("satisfied"),
    RETRYABLE("retryable"),
    BLOCKED("blocked"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): CapabilityStatus =
            entries.firstOrNull { it.persistedCode == value } ?: UNKNOWN
    }
}

data class CapabilityCheck(
    val capabilityCode: String,
    val status: CapabilityStatus,
    val issueCode: ConnectionIssueCode?,
    val remediation: RemediationAction?,
    val checkedAtEpochMs: Long,
    val validUntilEpochMs: Long?,
) {
    init {
        require(capabilityCode.matches(CapabilityCodePattern)) {
            "Capability code must be a stable code."
        }
        require(checkedAtEpochMs >= 0L) { "Check timestamp must not be negative." }
        require(validUntilEpochMs == null || validUntilEpochMs >= checkedAtEpochMs) {
            "Capability expiry must not precede its check."
        }
        when (status) {
            CapabilityStatus.SATISFIED -> require(issueCode == null && remediation == null) {
                "Satisfied capability must not retain an issue or remediation."
            }
            CapabilityStatus.RETRYABLE,
            CapabilityStatus.BLOCKED,
            -> require(issueCode != null && remediation != null) {
                "Unavailable capability requires an issue and remediation."
            }
            CapabilityStatus.UNKNOWN -> Unit
        }
    }
}
