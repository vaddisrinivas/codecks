package io.codecks.domain.backup

enum class RestoreStage {
    Stage,
    Migrate,
    Validate,
    ApplyDeck,
    ApplyAutomations,
    Verify,
    Commit,
    Rollback,
}

enum class RestoreTerminalResult {
    Committed,
    RolledBack,
    RecoveryRequired,
}

data class RestoreReceipt(
    val terminalResult: RestoreTerminalResult,
    val completedStages: List<RestoreStage>,
    val failedStage: RestoreStage?,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
) {
    init {
        require(startedAtMillis >= 0L)
        require(completedAtMillis >= startedAtMillis)
    }
}

sealed interface BackupRestoreResult {
    val receipt: RestoreReceipt

    data class Committed(override val receipt: RestoreReceipt) : BackupRestoreResult
    data class RolledBack(override val receipt: RestoreReceipt) : BackupRestoreResult
    data class RecoveryRequired(
        override val receipt: RestoreReceipt,
        val recoveryId: String,
    ) : BackupRestoreResult
}
