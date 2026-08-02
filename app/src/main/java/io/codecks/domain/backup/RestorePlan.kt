package io.codecks.domain.backup

enum class RestoreSectionChangeKind {
    Added,
    Replaced,
    Removed,
    Unchanged,
}

data class RestoreSectionChange(
    val section: String,
    val kind: RestoreSectionChangeKind,
)

sealed interface RestorePlan {
    val canConfirm: Boolean

    data class Ready(
        val planId: String,
        val backupSha256: String,
        val currentStateSha256: String,
        val sourceSchemaVersion: Int,
        val sections: List<RestoreSectionChange>,
        val migrations: List<String>,
        val warnings: List<String>,
    ) : RestorePlan {
        override val canConfirm: Boolean = true
    }

    data class Blocked(
        val reason: BackupRejectionReason,
        val warnings: List<String>,
    ) : RestorePlan {
        override val canConfirm: Boolean = false
    }
}
