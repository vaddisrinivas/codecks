package io.codecks.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.UUID
import org.json.JSONObject

internal const val MAX_BACKUP_RECOVERY_BYTES = 8 * 1024 * 1024
private const val RECOVERY_RESIDUE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L

internal fun requireBoundedRecoveryPayload(payload: ByteArray): ByteArray = payload.also {
    require(it.size <= MAX_BACKUP_RECOVERY_BYTES) {
        "Current Deck and Rules are too large for a safe recovery snapshot"
    }
}

internal enum class BackupFailurePoint {
    AfterStage,
    AfterMigration,
    AfterValidation,
    AfterDeckApply,
    AfterAutomationsApply,
    BeforeVerification,
    BeforeCommit,
    RollbackDeck,
    RollbackAutomations,
}

internal fun interface BackupFailureInjector {
    fun failAt(point: BackupFailurePoint)

    data object None : BackupFailureInjector {
        override fun failAt(point: BackupFailurePoint) = Unit
    }
}

internal interface BackupRecoveryStore {
    fun save(sections: Map<String, String>): String
    fun clear(recoveryId: String)
    fun contains(recoveryId: String): Boolean
    fun load(recoveryId: String): Map<String, String>?
    fun pendingIds(): List<String>
    fun quarantine(recoveryId: String)
    fun cleanupResidue(nowMillis: Long = System.currentTimeMillis()): Int = 0
    fun residueFiles(): List<String> = emptyList()
    fun deleteResidue(fileName: String): Boolean = false
}

internal class InMemoryBackupRecoveryStore : BackupRecoveryStore {
    private val snapshots = mutableMapOf<String, Map<String, String>>()

    override fun save(sections: Map<String, String>): String =
        UUID.randomUUID().toString().also { snapshots[it] = sections.toMap() }

    override fun clear(recoveryId: String) {
        snapshots.remove(recoveryId)
    }

    override fun contains(recoveryId: String): Boolean = recoveryId in snapshots

    override fun load(recoveryId: String): Map<String, String>? = snapshots[recoveryId]?.toMap()

    override fun pendingIds(): List<String> = snapshots.keys.sorted()

    override fun quarantine(recoveryId: String) {
        snapshots.remove(recoveryId)
    }
}

/**
 * Recovery material lives only in app-private storage. It intentionally contains the exact prior
 * deck and automation JSON, including any user-authored command text. It is not a credential-store
 * export, but command text may itself contain sensitive values. Atomic rename prevents a partial
 * recovery snapshot becoming authoritative.
 */
internal class FileBackupRecoveryStore(context: Context) : BackupRecoveryStore {
    private val directory = File(context.filesDir, "backup-restore-recovery")

    override fun save(sections: Map<String, String>): String {
        val recoveryId = UUID.randomUUID().toString()
        check(directory.exists() || directory.mkdirs()) { "Cannot create restore recovery directory" }
        val target = recoveryFile(recoveryId)
        val staging = File(directory, ".$recoveryId.tmp")
        val payload = JSONObject()
            .put("schemaVersion", 1)
            .put("recoveryId", recoveryId)
            .put("deck", sections.getValue("deck"))
            .put("automations", sections.getValue("automations"))
            .toString()
            .toByteArray()
        requireBoundedRecoveryPayload(payload)
        FileOutputStream(staging).use { output ->
            output.write(payload)
            output.fd.sync()
        }
        if (!staging.renameTo(target)) {
            staging.delete()
            error("Cannot persist restore recovery material")
        }
        syncDirectoryBestEffort()
        return recoveryId
    }

    override fun clear(recoveryId: String) {
        val target = recoveryFile(recoveryId)
        check(!target.exists() || target.delete()) { "Cannot clear restore recovery material" }
        syncDirectoryBestEffort()
    }

    override fun contains(recoveryId: String): Boolean = recoveryFile(recoveryId).isFile

    override fun load(recoveryId: String): Map<String, String>? {
        val target = recoveryFile(recoveryId)
        if (!target.isFile) return null
        return try {
            if (target.length() > MAX_BACKUP_RECOVERY_BYTES) return null
            val payload = FileInputStream(target).use {
                it.readCodecksBackupBounded(MAX_BACKUP_RECOVERY_BYTES)
            }
            val root = JSONObject(payload.toString(Charsets.UTF_8))
            require(root.getInt("schemaVersion") == 1)
            require(root.getString("recoveryId") == recoveryId)
            linkedMapOf(
                "deck" to root.getString("deck"),
                "automations" to root.getString("automations"),
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun pendingIds(): List<String> = directory.listFiles()
        .orEmpty()
        .mapNotNull { file ->
            file.name.removeSuffix(".json")
                .takeIf { file.isFile && file.name.endsWith(".json") }
                ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
        }
        .sorted()

    override fun quarantine(recoveryId: String) {
        val target = recoveryFile(recoveryId)
        if (!target.exists()) return
        check(directory.exists() || directory.mkdirs()) { "Cannot create restore recovery directory" }
        val quarantined = File(directory, "$recoveryId.corrupt")
        check(!quarantined.exists() || quarantined.delete()) { "Cannot replace corrupt recovery quarantine" }
        check(target.renameTo(quarantined)) { "Cannot quarantine corrupt recovery material" }
        syncDirectoryBestEffort()
    }

    override fun residueFiles(): List<String> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".corrupt")) }
        .map(File::getName)
        .sorted()

    override fun cleanupResidue(nowMillis: Long): Int {
        var deleted = 0
        directory.listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".corrupt")) }
            .filter { nowMillis - it.lastModified() > RECOVERY_RESIDUE_MAX_AGE_MILLIS }
            .forEach { if (it.delete()) deleted += 1 }
        if (deleted > 0) syncDirectoryBestEffort()
        return deleted
    }

    override fun deleteResidue(fileName: String): Boolean {
        require(fileName == File(fileName).name) { "Invalid recovery residue name" }
        require(fileName.endsWith(".tmp") || fileName.endsWith(".corrupt")) {
            "Only recovery residue can be deleted"
        }
        val target = File(directory, fileName)
        val deleted = !target.exists() || target.delete()
        if (deleted) syncDirectoryBestEffort()
        return deleted && !target.exists()
    }

    private fun recoveryFile(recoveryId: String): File {
        require(runCatching { UUID.fromString(recoveryId) }.isSuccess) { "Invalid recovery identifier" }
        return File(directory, "$recoveryId.json")
    }

    private fun syncDirectoryBestEffort() {
        if (!directory.isDirectory) return
        try {
            val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(descriptor)
            } finally {
                Os.close(descriptor)
            }
        } catch (_: Exception) {
            // Some Android filesystems do not allow opening directories. File contents are fsynced.
        }
    }
}
