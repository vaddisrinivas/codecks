package io.codecks.domain.privacy

private const val MaxDiagnosticAttempt = 1_000
private const val MaxDiagnosticDurationMs = 24L * 60L * 60L * 1_000L

enum class DiagnosticComponent(val persistedCode: String) {
    APP("app"),
    CONNECTION("connection"),
    HID("hid"),
    CLIPBOARD("clipboard"),
    AUTOMATION("automation"),
    BACKUP("backup"),
    UPDATE("update"),
    AI("ai"),
    SUPPORT("support"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): DiagnosticComponent =
            entries.firstOrNull { it.persistedCode == value } ?: UNKNOWN
    }
}

enum class DiagnosticEventCode(val persistedCode: String) {
    STATE_CHANGED("state_changed"),
    CAPABILITY_CHECKED("capability_checked"),
    ATTEMPT_STARTED("attempt_started"),
    ATTEMPT_FINISHED("attempt_finished"),
    RETRY_SCHEDULED("retry_scheduled"),
    RECEIPT_RECORDED("receipt_recorded"),
    EXPORT_CREATED("export_created"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): DiagnosticEventCode =
            entries.firstOrNull { it.persistedCode == value } ?: UNKNOWN
    }
}

enum class DiagnosticResultCode(val persistedCode: String) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    BLOCKED("blocked"),
    RETRYABLE("retryable"),
    SKIPPED("skipped"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): DiagnosticResultCode =
            entries.firstOrNull { it.persistedCode == value } ?: UNKNOWN
    }
}

data class DiagnosticEvent(
    val component: DiagnosticComponent,
    val event: DiagnosticEventCode,
    val result: DiagnosticResultCode,
    val attempt: Int,
    val durationMs: Long,
    val timestampEpochMs: Long,
) {
    init {
        require(attempt in 0..MaxDiagnosticAttempt) { "Attempt is outside the diagnostic bound." }
        require(durationMs in 0L..MaxDiagnosticDurationMs) { "Duration is outside the diagnostic bound." }
        require(timestampEpochMs >= 0L) { "Timestamp must not be negative." }
    }
}
