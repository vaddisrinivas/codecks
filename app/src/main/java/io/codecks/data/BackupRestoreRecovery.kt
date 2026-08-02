package io.codecks.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONObject

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
}

/**
 * Recovery material lives only in app-private storage. It contains the prior deck and automation
 * JSON, never credentials. Atomic rename prevents a partial recovery snapshot becoming authoritative.
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
        FileOutputStream(staging).use { output ->
            output.write(payload.toByteArray())
            output.fd.sync()
        }
        if (!staging.renameTo(target)) {
            staging.delete()
            error("Cannot persist restore recovery material")
        }
        return recoveryId
    }

    override fun clear(recoveryId: String) {
        val target = recoveryFile(recoveryId)
        check(!target.exists() || target.delete()) { "Cannot clear restore recovery material" }
    }

    override fun contains(recoveryId: String): Boolean = recoveryFile(recoveryId).isFile

    override fun load(recoveryId: String): Map<String, String>? {
        val target = recoveryFile(recoveryId)
        if (!target.isFile) return null
        return runCatching {
            val root = JSONObject(target.readText())
            require(root.getInt("schemaVersion") == 1)
            require(root.getString("recoveryId") == recoveryId)
            linkedMapOf(
                "deck" to root.getString("deck"),
                "automations" to root.getString("automations"),
            )
        }.getOrNull()
    }

    override fun pendingIds(): List<String> = directory.listFiles()
        .orEmpty()
        .mapNotNull { file ->
            file.name.removeSuffix(".json")
                .takeIf { file.isFile && file.name.endsWith(".json") }
                ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
        }
        .sorted()

    private fun recoveryFile(recoveryId: String): File {
        require(runCatching { UUID.fromString(recoveryId) }.isSuccess) { "Invalid recovery identifier" }
        return File(directory, "$recoveryId.json")
    }
}
