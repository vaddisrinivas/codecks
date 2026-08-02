package io.codecks.domain.backup

data class BackupSectionManifest(
    val name: String,
    val path: String,
    val sha256: String,
    val uncompressedBytes: Long,
)

data class BackupManifest(
    val schemaVersion: Int,
    val sourceAppVersion: String,
    val sections: List<BackupSectionManifest>,
)

enum class BackupRejectionReason {
    FutureSchema,
    UnsupportedOldSchema,
    CorruptManifest,
    CorruptChecksum,
    UnsafePath,
    DuplicateEntry,
    ExcessiveEntryCount,
    ExcessiveUncompressedSize,
    MissingEntry,
    UnexpectedEntry,
}

sealed interface CompatibilityVerdict {
    data class Compatible(val manifest: BackupManifest) : CompatibilityVerdict
    data class MigrationRequired(
        val sourceSchemaVersion: Int,
        val sections: Set<String>,
    ) : CompatibilityVerdict
    data class Rejected(val reason: BackupRejectionReason) : CompatibilityVerdict
}
