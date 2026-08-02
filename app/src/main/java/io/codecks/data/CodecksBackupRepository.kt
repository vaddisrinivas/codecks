package io.codecks.data

import android.content.Context
import io.codecks.BuildConfig
import io.codecks.data.automation.AutomationRepository
import io.codecks.domain.backup.BackupManifest
import io.codecks.domain.backup.BackupRejectionReason
import io.codecks.domain.backup.BackupSectionManifest
import io.codecks.domain.backup.CompatibilityVerdict
import io.codecks.domain.backup.RestorePlan
import io.codecks.domain.backup.BackupRestoreResult
import io.codecks.domain.backup.RestoreReceipt
import io.codecks.domain.backup.RestoreSectionChange
import io.codecks.domain.backup.RestoreSectionChangeKind
import io.codecks.domain.backup.RestoreStage
import io.codecks.domain.backup.RestoreTerminalResult
import io.codecks.data.privacy.DiagnosticEventStore
import io.codecks.data.privacy.recordTerminal
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticResultCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Local, user-directed backup. Credentials and connection secrets are never included. */
@Singleton
class CodecksBackupRepository internal constructor(
    private val actionRepository: ActionRepository,
    private val automationRepository: AutomationRepository,
    private val sourceAppVersion: () -> String,
    private val failureInjector: BackupFailureInjector = BackupFailureInjector.None,
    private val recoveryStore: BackupRecoveryStore = InMemoryBackupRecoveryStore(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val terminalEvent: (DiagnosticResultCode, Long, Long) -> Unit = { _, _, _ -> },
) {
    @Inject
    constructor(
        actionRepository: ActionRepository,
        automationRepository: AutomationRepository,
        @ApplicationContext context: Context,
    ) : this(
        actionRepository,
        automationRepository,
        { BuildConfig.VERSION_NAME },
        recoveryStore = FileBackupRecoveryStore(context),
        terminalEvent = { result, duration, timestamp ->
            DiagnosticEventStore(context).recordTerminal(
                component = DiagnosticComponent.BACKUP,
                result = result,
                durationMs = duration,
                timestampEpochMs = timestamp,
            )
        },
    )

    constructor(
        actionRepository: ActionRepository,
        automationRepository: AutomationRepository,
    ) : this(actionRepository, automationRepository, { BuildConfig.VERSION_NAME })

    suspend fun exportArchive(): Result<ByteArray> = runCatching {
        val entries = linkedMapOf(
            DECK_PATH to actionRepository.exportLayout().getOrThrow().toByteArray(),
            AUTOMATIONS_PATH to automationRepository.exportRecipes().getOrThrow().toByteArray(),
        )
        require(entries.values.none { it.toString(Charsets.UTF_8).containsSecretShapedValue() }) {
            "Backup blocked: deck or automation command contains secret-shaped text"
        }
        val manifest = BackupManifest(
            schemaVersion = ARCHIVE_SCHEMA_VERSION,
            sourceAppVersion = sourceAppVersion(),
            sections = listOf(
                entries.section("deck", DECK_PATH),
                entries.section("automations", AUTOMATIONS_PATH),
            ),
        )
        BackupArchiveCodec.encode(manifest, entries)
    }

    /** Parses and validates without calling either mutable repository. */
    fun compatibilityVerdict(bytes: ByteArray): CompatibilityVerdict =
        BackupArchiveCodec.inspect(bytes)

    suspend fun createRestorePlan(bytes: ByteArray): Result<RestorePlan> = runCatching {
        require(pendingRecoveryId() == null) { "Complete pending backup recovery before another restore" }
        val snapshot = bytes.copyOf()
        val verdict = compatibilityVerdict(snapshot)
        if (verdict is CompatibilityVerdict.Rejected) {
            return@runCatching RestorePlan.Blocked(
                reason = verdict.reason,
                warnings = listOf("This backup cannot be restored safely."),
            )
        }
        val backupSections = BackupArchiveCodec.sectionPayloads(snapshot, verdict)
        val currentSections = currentSections()
        val sectionChanges = (backupSections.keys + currentSections.keys)
            .sorted()
            .map { section ->
                RestoreSectionChange(
                    section = section,
                    kind = when {
                        section !in currentSections -> RestoreSectionChangeKind.Added
                        section !in backupSections -> RestoreSectionChangeKind.Removed
                        backupSections.getValue(section).toByteArray().sha256() !=
                            currentSections.getValue(section).toByteArray().sha256() ->
                            RestoreSectionChangeKind.Replaced
                        else -> RestoreSectionChangeKind.Unchanged
                    },
                )
            }
        val backupSha256 = snapshot.sha256()
        val currentStateSha256 = currentSections.stateSha256()
        val sourceSchema = when (verdict) {
            is CompatibilityVerdict.Compatible -> verdict.manifest.schemaVersion
            is CompatibilityVerdict.MigrationRequired -> verdict.sourceSchemaVersion
            is CompatibilityVerdict.Rejected -> error("Rejected verdict returned above")
        }
        val migrations = if (verdict is CompatibilityVerdict.MigrationRequired) {
            listOf("Backup schema ${verdict.sourceSchemaVersion} will migrate to archive schema $ARCHIVE_SCHEMA_VERSION.")
        } else {
            emptyList()
        }
        val warnings = buildList {
            if (sectionChanges.any { it.kind == RestoreSectionChangeKind.Removed }) {
                add("Sections absent from the backup will be removed.")
            }
            if (sectionChanges.any { it.kind == RestoreSectionChangeKind.Replaced }) {
                add("Existing sections will be replaced.")
            }
        }
        val canonicalChanges = sectionChanges.joinToString("|") { "${it.section}:${it.kind.name}" }
        RestorePlan.Ready(
            planId = "$backupSha256:$currentStateSha256:$sourceSchema:$canonicalChanges".toByteArray().sha256(),
            backupSha256 = backupSha256,
            currentStateSha256 = currentStateSha256,
            sourceSchemaVersion = sourceSchema,
            sections = sectionChanges,
            migrations = migrations,
            warnings = warnings,
        )
    }

    suspend fun restoreConfirmed(
        planId: String,
        bytes: ByteArray,
    ): Result<BackupRestoreResult> {
        return try {
            val snapshot = bytes.copyOf()
            val rebound = createRestorePlan(snapshot).getOrThrow() as? RestorePlan.Ready
                ?: error("This backup cannot be confirmed")
            require(rebound.planId == planId) { "Restore preview is stale; review the backup again" }
            val sections = BackupArchiveCodec.sectionPayloads(snapshot, compatibilityVerdict(snapshot))
            val outcome = executeTransactionalRestore(
                sourceSchemaVersion = rebound.sourceSchemaVersion,
                sections = sections,
                expectedCurrentStateSha256 = rebound.currentStateSha256,
            )
            outcome.recordTerminalBestEffort()
            Result.success(outcome)
        } catch (error: Throwable) {
            error.rethrowIfCancellationOrFatal()
            Result.failure(error)
        }
    }

    fun pendingRecoveryId(): String? = recoveryStore.pendingIds().firstOrNull()

    suspend fun recoverPending(recoveryId: String): Result<Unit> = runCatching {
        require(recoveryId == pendingRecoveryId()) { "Recovery request is stale" }
        val previous = recoveryStore.load(recoveryId) ?: error("Recovery material is unreadable")
        withContext(NonCancellable) {
            actionRepository.importLayout(previous.getValue("deck")).getOrThrow()
            automationRepository.importRecipes(previous.getValue("automations")).getOrThrow()
            require(currentSections() == previous) { "Recovery verification failed" }
            recoveryStore.clear(recoveryId)
        }
    }

    private suspend fun currentSections(): Map<String, String> = linkedMapOf(
        "deck" to actionRepository.exportLayout().getOrThrow(),
        "automations" to automationRepository.exportRecipes().getOrThrow(),
    )

    private suspend fun executeTransactionalRestore(
        sourceSchemaVersion: Int,
        sections: Map<String, String>,
        expectedCurrentStateSha256: String,
    ): BackupRestoreResult {
        val startedAt = nowMillis()
        val completed = mutableListOf<RestoreStage>()
        var currentStage = RestoreStage.Stage
        val previous = currentSections()
        require(previous.stateSha256() == expectedCurrentStateSha256) {
            "Restore preview is stale; review the backup again"
        }
        val recoveryId = recoveryStore.save(previous)
        try {
            completed += RestoreStage.Stage
            failureInjector.failAt(BackupFailurePoint.AfterStage)

            currentStage = RestoreStage.Migrate
            val migrated = migrateSections(sourceSchemaVersion, sections)
            completed += RestoreStage.Migrate
            failureInjector.failAt(BackupFailurePoint.AfterMigration)

            currentStage = RestoreStage.Validate
            val deck = migrated["deck"] ?: error("Deck section missing")
            val automations = migrated["automations"] ?: error("Automations section missing")
            actionRepository.validateLayout(deck).getOrThrow()
            automationRepository.validateRecipes(automations).getOrThrow()
            completed += RestoreStage.Validate
            failureInjector.failAt(BackupFailurePoint.AfterValidation)

            currentStage = RestoreStage.ApplyDeck
            actionRepository.importLayout(deck).getOrThrow()
            completed += RestoreStage.ApplyDeck
            failureInjector.failAt(BackupFailurePoint.AfterDeckApply)

            currentStage = RestoreStage.ApplyAutomations
            automationRepository.importRecipes(automations).getOrThrow()
            completed += RestoreStage.ApplyAutomations
            failureInjector.failAt(BackupFailurePoint.AfterAutomationsApply)

            currentStage = RestoreStage.Verify
            failureInjector.failAt(BackupFailurePoint.BeforeVerification)
            require(actionRepository.exportLayout().getOrThrow() == deck) { "Deck verification failed" }
            require(automationRepository.exportRecipes().getOrThrow() == automations) {
                "Automations verification failed"
            }
            completed += RestoreStage.Verify

            currentStage = RestoreStage.Commit
            failureInjector.failAt(BackupFailurePoint.BeforeCommit)
            val completedAt = nowMillis()
            val committed = BackupRestoreResult.Committed(
                receipt = RestoreReceipt(
                    terminalResult = RestoreTerminalResult.Committed,
                    completedStages = completed.toList() + RestoreStage.Commit,
                    failedStage = null,
                    startedAtMillis = startedAt,
                    completedAtMillis = completedAt,
                ),
            )
            recoveryStore.clear(recoveryId)
            return committed
        } catch (error: Throwable) {
            val recoveryCleared = withContext(NonCancellable) {
                val rollbackSucceeded = rollback(previous)
                completed += RestoreStage.Rollback
                rollbackSucceeded && runCatching { recoveryStore.clear(recoveryId) }.isSuccess
            }
            val receipt = RestoreReceipt(
                terminalResult = if (recoveryCleared) {
                    RestoreTerminalResult.RolledBack
                } else {
                    RestoreTerminalResult.RecoveryRequired
                },
                completedStages = completed.toList(),
                failedStage = currentStage,
                startedAtMillis = startedAt,
                completedAtMillis = nowMillis(),
            )
            val outcome = if (recoveryCleared) {
                BackupRestoreResult.RolledBack(receipt)
            } else {
                BackupRestoreResult.RecoveryRequired(receipt, recoveryId)
            }
            error.rethrowIfCancellationOrFatal()
            return outcome
        }
    }

    private fun BackupRestoreResult.recordTerminalBestEffort() {
        val code = when (this) {
            is BackupRestoreResult.Committed -> DiagnosticResultCode.SUCCEEDED
            is BackupRestoreResult.RolledBack -> DiagnosticResultCode.FAILED
            is BackupRestoreResult.RecoveryRequired -> DiagnosticResultCode.BLOCKED
        }
        runCatching {
            terminalEvent(
                code,
                receipt.completedAtMillis - receipt.startedAtMillis,
                receipt.completedAtMillis,
            )
        }
    }

    private fun migrateSections(
        sourceSchemaVersion: Int,
        sections: Map<String, String>,
    ): Map<String, String> {
        require(sourceSchemaVersion in 1..ARCHIVE_SCHEMA_VERSION) { "Unsupported migration source" }
        return sections.toSortedMap()
    }

    private suspend fun rollback(previous: Map<String, String>): Boolean {
        var succeeded = true
        runCatching {
            failureInjector.failAt(BackupFailurePoint.RollbackDeck)
            actionRepository.importLayout(previous.getValue("deck")).getOrThrow()
        }.onFailure { succeeded = false }
        runCatching {
            failureInjector.failAt(BackupFailurePoint.RollbackAutomations)
            automationRepository.importRecipes(previous.getValue("automations")).getOrThrow()
        }.onFailure { succeeded = false }
        val exact = runCatching {
            actionRepository.exportLayout().getOrThrow() == previous.getValue("deck") &&
                automationRepository.exportRecipes().getOrThrow() == previous.getValue("automations")
        }.getOrDefault(false)
        return succeeded && exact
    }

    private companion object {
        const val ARCHIVE_SCHEMA_VERSION = 2
        const val DECK_PATH = "sections/deck.json"
        const val AUTOMATIONS_PATH = "sections/automations.json"
    }
}

private fun Throwable.rethrowIfCancellationOrFatal() {
    when (this) {
        is CancellationException,
        is VirtualMachineError,
        is ThreadDeath,
        is LinkageError,
        -> throw this
    }
}

internal object BackupArchiveCodec {
    private const val MANIFEST_PATH = "manifest.json"
    private const val CURRENT_SCHEMA = 2
    private const val LEGACY_SCHEMA = 1
    internal const val MAX_ENTRIES = 16
    internal const val MAX_UNCOMPRESSED_BYTES = 2 * 1024 * 1024

    fun encode(manifest: BackupManifest, entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, MANIFEST_PATH, encodeManifest(manifest).toByteArray())
            entries.forEach { (path, content) -> writeEntry(zip, path, content) }
        }
        return output.toByteArray()
    }

    fun inspect(bytes: ByteArray): CompatibilityVerdict {
        if (bytes.size > MAX_UNCOMPRESSED_BYTES) {
            return CompatibilityVerdict.Rejected(BackupRejectionReason.ExcessiveUncompressedSize)
        }
        if (!bytes.startsWithZipSignature()) return inspectLegacy(bytes)
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!isSafeEntryName(name)) {
                        return CompatibilityVerdict.Rejected(BackupRejectionReason.UnsafePath)
                    }
                    if (name in entries) {
                        return CompatibilityVerdict.Rejected(BackupRejectionReason.DuplicateEntry)
                    }
                    if (entries.size >= MAX_ENTRIES) {
                        return CompatibilityVerdict.Rejected(BackupRejectionReason.ExcessiveEntryCount)
                    }
                    val content = readBounded(zip, MAX_UNCOMPRESSED_BYTES - totalBytes)
                        ?: return CompatibilityVerdict.Rejected(BackupRejectionReason.ExcessiveUncompressedSize)
                    totalBytes += content.size
                    entries[name] = content
                    zip.closeEntry()
                }
            }
        } catch (_: Throwable) {
            return CompatibilityVerdict.Rejected(BackupRejectionReason.CorruptManifest)
        }
        val manifestBytes = entries[MANIFEST_PATH]
            ?: return CompatibilityVerdict.Rejected(BackupRejectionReason.MissingEntry)
        val manifest = decodeManifest(manifestBytes.toString(Charsets.UTF_8))
            ?: return CompatibilityVerdict.Rejected(BackupRejectionReason.CorruptManifest)
        if (manifest.schemaVersion > CURRENT_SCHEMA) {
            return CompatibilityVerdict.Rejected(BackupRejectionReason.FutureSchema)
        }
        if (manifest.schemaVersion < CURRENT_SCHEMA) {
            return CompatibilityVerdict.Rejected(BackupRejectionReason.UnsupportedOldSchema)
        }
        val declaredPaths = manifest.sections.map(BackupSectionManifest::path).toSet()
        if (declaredPaths.size != manifest.sections.size || manifest.sections.map { it.name }.toSet().size != manifest.sections.size) {
            return CompatibilityVerdict.Rejected(BackupRejectionReason.CorruptManifest)
        }
        if (declaredPaths.any { !isSafeEntryName(it) }) {
            return CompatibilityVerdict.Rejected(BackupRejectionReason.UnsafePath)
        }
        if (entries.keys != declaredPaths + MANIFEST_PATH) {
            val reason = if (declaredPaths.any { it !in entries }) {
                BackupRejectionReason.MissingEntry
            } else {
                BackupRejectionReason.UnexpectedEntry
            }
            return CompatibilityVerdict.Rejected(reason)
        }
        manifest.sections.forEach { section ->
            val content = entries.getValue(section.path)
            if (content.size.toLong() != section.uncompressedBytes || content.sha256() != section.sha256) {
                return CompatibilityVerdict.Rejected(BackupRejectionReason.CorruptChecksum)
            }
        }
        return CompatibilityVerdict.Compatible(manifest)
    }

    fun sectionPayloads(
        bytes: ByteArray,
        verdict: CompatibilityVerdict,
    ): Map<String, String> = when (verdict) {
        is CompatibilityVerdict.Rejected -> error("Rejected backups have no readable sections")
        is CompatibilityVerdict.MigrationRequired -> {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            linkedMapOf(
                "deck" to root.getString("deck"),
                "automations" to root.getString("automations"),
            )
        }
        is CompatibilityVerdict.Compatible -> {
            val byPath = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    byPath[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
            verdict.manifest.sections.associate { section ->
                section.name to byPath.getValue(section.path).toString(Charsets.UTF_8)
            }
        }
    }

    private fun inspectLegacy(bytes: ByteArray): CompatibilityVerdict {
        val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return CompatibilityVerdict.Rejected(BackupRejectionReason.CorruptManifest)
        val schema = root.optInt("schemaVersion", -1)
        return when {
            schema > LEGACY_SCHEMA -> CompatibilityVerdict.Rejected(BackupRejectionReason.FutureSchema)
            schema < LEGACY_SCHEMA -> CompatibilityVerdict.Rejected(BackupRejectionReason.UnsupportedOldSchema)
            root.optBoolean("credentialStoresIncluded", true) ->
                CompatibilityVerdict.Rejected(BackupRejectionReason.CorruptManifest)
            !root.has("deck") || !root.has("automations") ->
                CompatibilityVerdict.Rejected(BackupRejectionReason.MissingEntry)
            else -> CompatibilityVerdict.MigrationRequired(schema, setOf("deck", "automations"))
        }
    }

    private fun encodeManifest(manifest: BackupManifest): String = JSONObject()
        .put("schemaVersion", manifest.schemaVersion)
        .put("sourceAppVersion", manifest.sourceAppVersion)
        .put("credentialStoresIncluded", false)
        .put(
            "sections",
            org.json.JSONArray().apply {
                manifest.sections.forEach { section ->
                    put(
                        JSONObject()
                            .put("name", section.name)
                            .put("path", section.path)
                            .put("sha256", section.sha256)
                            .put("uncompressedBytes", section.uncompressedBytes),
                    )
                }
            },
        )
        .toString()

    private fun decodeManifest(value: String): BackupManifest? = runCatching {
        val root = JSONObject(value)
        require(!root.optBoolean("credentialStoresIncluded", true))
        val sections = root.getJSONArray("sections")
        BackupManifest(
            schemaVersion = root.getInt("schemaVersion"),
            sourceAppVersion = root.getString("sourceAppVersion").also { require(it.isNotBlank()) },
            sections = List(sections.length()) { index ->
                val section = sections.getJSONObject(index)
                BackupSectionManifest(
                    name = section.getString("name").also { require(it.isNotBlank()) },
                    path = section.getString("path"),
                    sha256 = section.getString("sha256").also { require(it.matches(Regex("[0-9a-f]{64}"))) },
                    uncompressedBytes = section.getLong("uncompressedBytes").also { require(it >= 0L) },
                )
            },
        )
    }.getOrNull()

    private fun writeEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        zip.write(content)
        zip.closeEntry()
    }

    private fun readBounded(zip: ZipInputStream, remaining: Long): ByteArray? {
        if (remaining < 0L) return null
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            if (output.size().toLong() + read > remaining) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank() || name.startsWith('/') || '\\' in name || ':' in name) return false
        val segments = name.split('/')
        return segments.none { it.isBlank() || it == "." || it == ".." }
    }
}

private fun Map<String, ByteArray>.section(name: String, path: String): BackupSectionManifest {
    val content = getValue(path)
    return BackupSectionManifest(name, path, content.sha256(), content.size.toLong())
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }

private fun ByteArray.startsWithZipSignature(): Boolean =
    size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4b.toByte() &&
        this[2] == 0x03.toByte() && this[3] == 0x04.toByte()

private fun Map<String, String>.stateSha256(): String = entries
    .sortedBy(Map.Entry<String, String>::key)
    .joinToString("|") { (section, value) -> "$section:${value.toByteArray().sha256()}" }
    .toByteArray()
    .sha256()

private fun String.containsSecretShapedValue(): Boolean {
    if (contains("-----BEGIN " + "PRIVATE" + " KEY-----", ignoreCase = true)) return true
    if (Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/-]{12,}""").containsMatchIn(this)) return true
    return Regex(
        """(?i)\b(api[_-]?key|access[_-]?token|password|client[_-]?secret)\b\s*[:=]\s*["']?[A-Za-z0-9._~+/-]{8,}""",
    ).containsMatchIn(this)
}
