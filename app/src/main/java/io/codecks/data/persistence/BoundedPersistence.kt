package io.codecks.data.persistence

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

sealed interface PersistenceRead<out T> {
    data object Missing : PersistenceRead<Nothing>
    data class Value<T>(val value: T, val migratedFrom: Int? = null) : PersistenceRead<T>
    data class Corrupt(val reason: String) : PersistenceRead<Nothing>
    data class FutureVersion(val found: Int, val supported: Int) : PersistenceRead<Nothing>
    data object KeyUnavailable : PersistenceRead<Nothing>
}

class PersistenceUnavailableException(message: String) : IllegalStateException(message)

fun <T> PersistenceRead<T>.valueForMutation(storeName: String, empty: () -> T): T = when (this) {
    is PersistenceRead.Value -> value
    PersistenceRead.Missing -> empty()
    PersistenceRead.KeyUnavailable -> throw PersistenceUnavailableException("$storeName key is unavailable")
    is PersistenceRead.FutureVersion -> throw PersistenceUnavailableException("$storeName is from a newer app")
    is PersistenceRead.Corrupt -> throw PersistenceUnavailableException("$storeName is corrupt")
}

object BoundedPayload {
    fun utf8(raw: String, maxBytes: Int): String {
        require(maxBytes > 0)
        require(raw.toByteArray(Charsets.UTF_8).size <= maxBytes) { "Persisted payload exceeds $maxBytes bytes" }
        return raw
    }

    fun <T> decodeVersioned(
        raw: String?,
        maxBytes: Int,
        currentVersion: Int,
        versionOf: (String) -> Int,
        migrate: (String, Int) -> String?,
        decode: (String) -> T,
    ): PersistenceRead<T> {
        if (raw == null) return PersistenceRead.Missing
        if (raw.isBlank()) return PersistenceRead.Corrupt("blank")
        if (raw.toByteArray(Charsets.UTF_8).size > maxBytes) return PersistenceRead.Corrupt("oversized")
        val version = runCatching { versionOf(raw) }.getOrElse { return PersistenceRead.Corrupt("schema") }
        if (version > currentVersion) return PersistenceRead.FutureVersion(version, currentVersion)
        if (version <= 0) return PersistenceRead.Corrupt("schema")
        val decodedRaw = if (version == currentVersion) raw else {
            runCatching { migrate(raw, version) }.getOrNull() ?: return PersistenceRead.Corrupt("migration")
        }
        if (decodedRaw.toByteArray(Charsets.UTF_8).size > maxBytes) return PersistenceRead.Corrupt("oversized_migration")
        return runCatching { PersistenceRead.Value(decode(decodedRaw), version.takeIf { it != currentVersion }) }
            .getOrElse { PersistenceRead.Corrupt("decode") }
    }

    fun <T> decodeEncryptedOrLegacy(
        stored: String?,
        maxStoredBytes: Int,
        maxPlaintextBytes: Int,
        isLegacyPlaintext: (String) -> Boolean,
        decrypt: (String) -> String,
        decode: (String) -> T,
    ): PersistenceRead<T> {
        if (stored == null) return PersistenceRead.Missing
        if (stored.isBlank()) return PersistenceRead.Corrupt("blank")
        if (stored.toByteArray(Charsets.UTF_8).size > maxStoredBytes) return PersistenceRead.Corrupt("oversized")
        val plaintext = if (isLegacyPlaintext(stored)) {
            stored
        } else {
            runCatching { decrypt(stored) }.getOrElse { return PersistenceRead.KeyUnavailable }
        }
        if (plaintext.toByteArray(Charsets.UTF_8).size > maxPlaintextBytes) {
            return PersistenceRead.Corrupt("oversized_plaintext")
        }
        return runCatching { PersistenceRead.Value(decode(plaintext)) }
            .getOrElse { PersistenceRead.Corrupt("decode") }
    }
}

/** Small crash-safe file primitive. DataStore/SharedPreferences already provide their own atomic commit. */
internal class AtomicBoundedFileStore(
    private val target: File,
    private val maxBytes: Int,
    private val mover: (File, File) -> Unit = ::atomicReplace,
) {
    private val staging get() = File(target.parentFile, ".${target.name}.tmp")
    private val backup get() = File(target.parentFile, ".${target.name}.bak")

    @Synchronized
    fun read(): PersistenceRead<String> {
        recoverInterruptedCommit()
        if (!target.isFile) return PersistenceRead.Missing
        if (target.length() > maxBytes) return PersistenceRead.Corrupt("oversized")
        return runCatching {
            val value = target.readText(Charsets.UTF_8)
            if (value.toByteArray(Charsets.UTF_8).size > maxBytes) PersistenceRead.Corrupt("oversized")
            else PersistenceRead.Value(value)
        }.getOrElse { PersistenceRead.Corrupt("read") }
    }

    @Synchronized
    fun write(value: String) {
        val bounded = BoundedPayload.utf8(value, maxBytes)
        target.parentFile?.mkdirs()
        staging.delete()
        FileOutputStream(staging).use { output ->
            output.write(bounded.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        backup.delete()
        if (target.exists()) mover(target, backup)
        try {
            mover(staging, target)
            backup.delete()
        } catch (failure: Throwable) {
            if (!target.exists() && backup.exists()) runCatching { mover(backup, target) }
            staging.delete()
            throw failure
        }
    }

    @Synchronized
    fun clear() {
        staging.delete()
        backup.delete()
        target.delete()
    }

    private fun recoverInterruptedCommit() {
        if (!target.exists() && backup.exists()) mover(backup, target)
        staging.delete()
        if (target.exists()) backup.delete()
    }
}

internal class TransactionalFilePairStore(
    private val first: File,
    private val second: File,
    private val firstMaxBytes: Int,
    private val secondMaxBytes: Int,
    private val fault: (PairTransactionPoint) -> Unit = {},
) {
    private val marker = File(first.parentFile, ".${first.name}.${second.name}.txn")
    private val firstStage = File(first.parentFile, ".${first.name}.pair.tmp")
    private val secondStage = File(second.parentFile, ".${second.name}.pair.tmp")
    private val firstBackup = File(first.parentFile, ".${first.name}.pair.bak")
    private val secondBackup = File(second.parentFile, ".${second.name}.pair.bak")
    private val markerStage = File(first.parentFile, ".${first.name}.${second.name}.txn.tmp")

    @Synchronized
    fun recover(validate: (String, String) -> Unit) {
        if (!marker.exists()) {
            recoverWithoutJournal(validate)
            return
        }
        val journal = readJournal()
        if (journal == null) {
            recoverMalformedJournal(validate)
            return
        }
        when (journal.state) {
            PairTransactionState.PREPARED,
            PairTransactionState.INSTALLED_FIRST,
            -> rollbackOrPreserveNew(journal, validate)
            PairTransactionState.INSTALLED_BOTH,
            PairTransactionState.COMMITTED,
            -> finalizeNewOrRollback(journal, validate)
        }
    }

    @Synchronized
    fun commit(firstValue: String, secondValue: String, validate: (String, String) -> Unit) {
        recover(validate)
        val boundedFirst = BoundedPayload.utf8(firstValue, firstMaxBytes)
        val boundedSecond = BoundedPayload.utf8(secondValue, secondMaxBytes)
        validate(boundedFirst, boundedSecond)
        first.parentFile?.mkdirs()
        check(first.isFile == second.isFile) { "Incomplete persisted pair preserved; repair is required" }
        writeSynced(firstStage, boundedFirst)
        writeSynced(secondStage, boundedSecond)
        fault(PairTransactionPoint.AFTER_STAGING)
        val hadFirst = first.isFile
        val hadSecond = second.isFile
        if (hadFirst) copySynced(first, firstBackup)
        if (hadSecond) copySynced(second, secondBackup)
        fault(PairTransactionPoint.AFTER_BACKUPS)
        var journal = PairTransactionJournal(
            state = PairTransactionState.PREPARED,
            hadFirst = hadFirst,
            hadSecond = hadSecond,
            firstCandidateHash = sha256(boundedFirst),
            secondCandidateHash = sha256(boundedSecond),
            firstBackupHash = firstBackup.takeIf(File::isFile)?.let(::sha256File),
            secondBackupHash = secondBackup.takeIf(File::isFile)?.let(::sha256File),
        )
        writeJournal(journal)
        fault(PairTransactionPoint.AFTER_PREPARED_JOURNAL)
        try {
            atomicReplace(firstStage, first)
            fault(PairTransactionPoint.AFTER_FIRST_INSTALL)
            journal = journal.copy(state = PairTransactionState.INSTALLED_FIRST)
            writeJournal(journal)
            fault(PairTransactionPoint.AFTER_FIRST_JOURNAL)
            atomicReplace(secondStage, second)
            fault(PairTransactionPoint.AFTER_SECOND_INSTALL)
            journal = journal.copy(state = PairTransactionState.INSTALLED_BOTH)
            writeJournal(journal)
            fault(PairTransactionPoint.AFTER_SECOND_JOURNAL)
            val installed = readCoherent(first, second, validate)
                ?: error("Committed pair is unreadable")
            check(sha256(installed.first) == journal.firstCandidateHash)
            check(sha256(installed.second) == journal.secondCandidateHash)
            journal = journal.copy(state = PairTransactionState.COMMITTED)
            writeJournal(journal)
            fault(PairTransactionPoint.AFTER_COMMITTED_JOURNAL)
            finishCommittedCleanup()
        } catch (failure: Throwable) {
            runCatching { recover(validate) }.getOrElse { failure.addSuppressed(it) }
            throw failure
        }
    }

    private fun recoverWithoutJournal(validate: (String, String) -> Unit) {
        val live = readCoherent(first, second, validate)
        val backup = readCoherent(firstBackup, secondBackup, validate)
        when {
            live != null -> Unit
            backup != null -> installPair(backup)
            !first.exists() && !second.exists() && !firstBackup.exists() && !secondBackup.exists() -> Unit
            else -> throw PersistenceUnavailableException("Persisted pair is incomplete or corrupt; files preserved")
        }
        removeJournalDurably()
        cleanupArtifacts()
    }

    private fun recoverMalformedJournal(validate: (String, String) -> Unit) {
        val backup = readCoherent(firstBackup, secondBackup, validate)
        val live = readCoherent(first, second, validate)
        when {
            backup != null -> installPair(backup)
            live != null -> Unit
            !first.exists() && !second.exists() && !firstBackup.exists() && !secondBackup.exists() -> Unit
            else -> throw PersistenceUnavailableException("Pair transaction journal is corrupt; files preserved")
        }
        removeJournalDurably()
        cleanupArtifacts()
    }

    private fun rollbackOrPreserveNew(
        journal: PairTransactionJournal,
        validate: (String, String) -> Unit,
    ) {
        val backup = verifiedBackup(journal, validate)
        val candidate = verifiedCandidate(journal, validate)
        if (backup != null) {
            installPair(backup)
        } else if (candidate == null) {
            if (!journal.hadFirst && !journal.hadSecond) {
                first.delete()
                second.delete()
                syncDirectory(first.parentFile)
            } else {
                throw PersistenceUnavailableException("Pair transaction backup is unavailable; files preserved")
            }
        }
        removeJournalDurably()
        cleanupArtifacts()
    }

    private fun finalizeNewOrRollback(
        journal: PairTransactionJournal,
        validate: (String, String) -> Unit,
    ) {
        if (verifiedCandidate(journal, validate) == null) {
            val backup = verifiedBackup(journal, validate)
                ?: throw PersistenceUnavailableException("Neither transaction pair is valid; files preserved")
            installPair(backup)
        }
        removeJournalDurably()
        cleanupArtifacts()
    }

    private fun verifiedCandidate(
        journal: PairTransactionJournal,
        validate: (String, String) -> Unit,
    ): Pair<String, String>? = readCoherent(first, second, validate)?.takeIf {
        sha256(it.first) == journal.firstCandidateHash && sha256(it.second) == journal.secondCandidateHash
    }

    private fun verifiedBackup(
        journal: PairTransactionJournal,
        validate: (String, String) -> Unit,
    ): Pair<String, String>? {
        if (!journal.hadFirst && !journal.hadSecond) return null
        if (!journal.hadFirst || !journal.hadSecond) return null
        return readCoherent(firstBackup, secondBackup, validate)?.takeIf {
            sha256(it.first) == journal.firstBackupHash && sha256(it.second) == journal.secondBackupHash
        }
    }

    private fun readCoherent(
        firstFile: File,
        secondFile: File,
        validate: (String, String) -> Unit,
    ): Pair<String, String>? {
        if (!firstFile.isFile || !secondFile.isFile) return null
        if (firstFile.length() > firstMaxBytes || secondFile.length() > secondMaxBytes) return null
        val values = runCatching {
            firstFile.readText(Charsets.UTF_8) to secondFile.readText(Charsets.UTF_8)
        }.getOrNull() ?: return null
        return values.takeIf { runCatching { validate(it.first, it.second) }.isSuccess }
    }

    private fun installPair(values: Pair<String, String>) {
        writeSynced(firstStage, values.first)
        writeSynced(secondStage, values.second)
        atomicReplace(firstStage, first)
        atomicReplace(secondStage, second)
        syncDirectory(first.parentFile)
    }

    private fun finishCommittedCleanup() {
        removeJournalDurably()
        fault(PairTransactionPoint.AFTER_JOURNAL_REMOVAL)
        cleanupArtifacts()
    }

    private fun cleanupArtifacts() {
        firstBackup.delete()
        syncDirectory(first.parentFile)
        fault(PairTransactionPoint.AFTER_FIRST_BACKUP_DELETE)
        secondBackup.delete()
        syncDirectory(second.parentFile)
        fault(PairTransactionPoint.AFTER_SECOND_BACKUP_DELETE)
        firstStage.delete()
        syncDirectory(first.parentFile)
        fault(PairTransactionPoint.AFTER_FIRST_STAGE_DELETE)
        secondStage.delete()
        markerStage.delete()
        syncDirectory(second.parentFile)
        fault(PairTransactionPoint.AFTER_SECOND_STAGE_DELETE)
    }

    private fun removeJournalDurably() {
        markerStage.delete()
        if (marker.exists() && !marker.delete()) error("Could not remove pair transaction journal")
        syncDirectory(marker.parentFile)
    }

    private fun writeJournal(journal: PairTransactionJournal) {
        writeSynced(markerStage, journal.encode())
        atomicReplace(markerStage, marker)
        syncDirectory(marker.parentFile)
    }

    private fun readJournal(): PairTransactionJournal? = runCatching {
        if (marker.length() > MAX_JOURNAL_BYTES) return null
        PairTransactionJournal.decode(marker.readText(Charsets.UTF_8))
    }.getOrNull()

    private fun writeSynced(file: File, value: String) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            output.write(value.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun copySynced(source: File, destination: File) {
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        FileOutputStream(destination, true).use { it.fd.sync() }
    }

    private companion object {
        const val MAX_JOURNAL_BYTES = 2_048L
    }
}

internal enum class PairTransactionPoint {
    AFTER_STAGING,
    AFTER_BACKUPS,
    AFTER_PREPARED_JOURNAL,
    AFTER_FIRST_INSTALL,
    AFTER_FIRST_JOURNAL,
    AFTER_SECOND_INSTALL,
    AFTER_SECOND_JOURNAL,
    AFTER_COMMITTED_JOURNAL,
    AFTER_JOURNAL_REMOVAL,
    AFTER_FIRST_BACKUP_DELETE,
    AFTER_SECOND_BACKUP_DELETE,
    AFTER_FIRST_STAGE_DELETE,
    AFTER_SECOND_STAGE_DELETE,
}

private enum class PairTransactionState { PREPARED, INSTALLED_FIRST, INSTALLED_BOTH, COMMITTED }

private data class PairTransactionJournal(
    val state: PairTransactionState,
    val hadFirst: Boolean,
    val hadSecond: Boolean,
    val firstCandidateHash: String,
    val secondCandidateHash: String,
    val firstBackupHash: String?,
    val secondBackupHash: String?,
) {
    fun encode(): String {
        val payload = listOf(
            "version=1",
            "state=${state.name}",
            "hadFirst=$hadFirst",
            "hadSecond=$hadSecond",
            "firstCandidateHash=$firstCandidateHash",
            "secondCandidateHash=$secondCandidateHash",
            "firstBackupHash=${firstBackupHash ?: "-"}",
            "secondBackupHash=${secondBackupHash ?: "-"}",
        ).joinToString("\n")
        return "$payload\nchecksum=${sha256(payload)}"
    }

    companion object {
        private val hashPattern = Regex("[0-9a-f]{64}")

        fun decode(raw: String): PairTransactionJournal {
            val lines = raw.lines()
            require(lines.size == 9)
            val entries = lines.map {
                val parts = it.split('=', limit = 2)
                require(parts.size == 2)
                parts[0] to parts[1]
            }
            val keys = entries.map(Pair<String, String>::first)
            require(keys == listOf(
                "version", "state", "hadFirst", "hadSecond", "firstCandidateHash",
                "secondCandidateHash", "firstBackupHash", "secondBackupHash", "checksum",
            ))
            require(entries[0].second == "1")
            val payload = lines.dropLast(1).joinToString("\n")
            require(entries.last().second == sha256(payload))
            fun booleanAt(index: Int): Boolean = when (entries[index].second) {
                "true" -> true
                "false" -> false
                else -> error("Invalid journal boolean")
            }
            fun hashAt(index: Int): String = entries[index].second.also { require(hashPattern.matches(it)) }
            fun nullableHashAt(index: Int): String? = entries[index].second.takeUnless { it == "-" }
                ?.also { require(hashPattern.matches(it)) }
            return PairTransactionJournal(
                state = PairTransactionState.valueOf(entries[1].second),
                hadFirst = booleanAt(2),
                hadSecond = booleanAt(3),
                firstCandidateHash = hashAt(4),
                secondCandidateHash = hashAt(5),
                firstBackupHash = nullableHashAt(6),
                secondBackupHash = nullableHashAt(7),
            ).also {
                require(it.hadFirst == it.hadSecond)
                require(it.hadFirst == (it.firstBackupHash != null))
                require(it.hadSecond == (it.secondBackupHash != null))
            }
        }
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun sha256File(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString("") { "%02x".format(it) }

private fun syncDirectory(directory: File?) {
    if (directory == null || !directory.isDirectory) return
    runCatching {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }
}

private fun atomicReplace(source: File, target: File) {
    runCatching {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }.getOrElse {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    syncDirectory(target.parentFile)
}
