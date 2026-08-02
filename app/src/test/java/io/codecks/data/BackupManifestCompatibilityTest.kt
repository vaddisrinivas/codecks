package io.codecks.data

import io.codecks.core.actions.ActionResult
import io.codecks.data.automation.AutomationRepository
import io.codecks.domain.DeckAction
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.backup.BackupRejectionReason
import io.codecks.domain.backup.CompatibilityVerdict
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManifestCompatibilityTest {
    @Test
    fun currentArchiveCarriesVersionSectionsAndChecksums() = runTest {
        val repositories = BackupFakes()
        val repository = repositories.repository()

        val archive = repository.exportArchive().getOrThrow()
        val verdict = repository.compatibilityVerdict(archive)

        assertTrue(verdict is CompatibilityVerdict.Compatible)
        val manifest = (verdict as CompatibilityVerdict.Compatible).manifest
        assertEquals(2, manifest.schemaVersion)
        assertEquals("9.9.9-test", manifest.sourceAppVersion)
        assertEquals(setOf("deck", "automations"), manifest.sections.map { it.name }.toSet())
        assertTrue(manifest.sections.all { it.sha256.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test
    fun committedLegacyFixtureRequiresMigrationWithoutRepositoryMutation() {
        val repositories = BackupFakes()
        val repository = repositories.repository()
        val fixture = resource("backups/legacy-v1.json")

        val verdict = repository.compatibilityVerdict(fixture)

        assertTrue(verdict is CompatibilityVerdict.MigrationRequired)
        assertEquals(0, repositories.deck.importCalls)
        assertEquals(0, repositories.automations.importCalls)
    }

    @Test
    fun futureAndUnsupportedOldArchiveSchemasFailClosed() {
        val repository = BackupFakes().repository()
        val fixture = resource("backups/current-v2-manifest.json").toString(Charsets.UTF_8)
        val content = "{\"schemaVersion\":3,\"items\":[]}".toByteArray()

        val future = zip(
            "manifest.json" to fixture.replace("\"schemaVersion\": 2", "\"schemaVersion\": 3").toByteArray(),
            "sections/deck.json" to content,
            "sections/automations.json" to content,
        )
        val old = zip(
            "manifest.json" to fixture.replace("\"schemaVersion\": 2", "\"schemaVersion\": 1").toByteArray(),
            "sections/deck.json" to content,
            "sections/automations.json" to content,
        )

        assertEquals(
            BackupRejectionReason.FutureSchema,
            (repository.compatibilityVerdict(future) as CompatibilityVerdict.Rejected).reason,
        )
        assertEquals(
            BackupRejectionReason.UnsupportedOldSchema,
            (repository.compatibilityVerdict(old) as CompatibilityVerdict.Rejected).reason,
        )
    }
}

internal class BackupFakes {
    val deck = FakeArchiveActionRepository()
    val automations = FakeArchiveAutomationRepository()
    fun repository(
        failureInjector: BackupFailureInjector = BackupFailureInjector.None,
        recoveryStore: BackupRecoveryStore = InMemoryBackupRecoveryStore(),
        nowMillis: () -> Long = { 1_000L },
        terminalEvent: (io.codecks.domain.privacy.DiagnosticResultCode, Long, Long) -> Unit = { _, _, _ -> },
    ) = CodecksBackupRepository(
        actionRepository = deck,
        automationRepository = automations,
        sourceAppVersion = { "9.9.9-test" },
        failureInjector = failureInjector,
        recoveryStore = recoveryStore,
        nowMillis = nowMillis,
        terminalEvent = terminalEvent,
    )
}

internal fun resource(path: String): ByteArray =
    checkNotNull(
        Thread.currentThread().contextClassLoader?.getResourceAsStream(path),
    ) { "Test resource not found: $path" }.use { it.readBytes() }

internal fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

internal class FakeArchiveActionRepository : ActionRepository {
    var importCalls = 0
    var exported = "{\"schemaVersion\":3,\"items\":[]}"
    var throwOnExport: Throwable? = null
    override fun favorites(): List<DeckAction> = emptyList()
    override fun observeFavorites(): Flow<List<DeckAction>> = flowOf(emptyList())
    override fun allActions(): List<DeckAction> = emptyList()
    override suspend fun saveFavorites(actions: List<DeckAction>) = Unit
    override suspend fun run(action: DeckAction): Result<String> = Result.success("")
    override suspend fun test(action: DeckAction): Result<String> = Result.success("")
    override suspend fun exportLayout(): Result<String> {
        throwOnExport?.let { throw it }
        return Result.success(exported)
    }
    override suspend fun validateLayout(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun importLayout(payload: String): Result<Unit> {
        importCalls += 1
        exported = payload
        return Result.success(Unit)
    }
}

internal class FakeArchiveAutomationRepository : AutomationRepository {
    var importCalls = 0
    var failNextImport = false
    var exported = "{\"schemaVersion\":3,\"items\":[]}"
    override val recipes: Flow<List<AutomationRecipe>> = flowOf(emptyList())
    override suspend fun save(recipe: AutomationRecipe) = Unit
    override suspend fun delete(recipeId: String) = Unit
    override suspend fun duplicate(recipeId: String) = Unit
    override suspend fun recordRun(recipeId: String, result: ActionResult) = Unit
    override suspend fun recordLiveTest(
        recipeId: String,
        receipt: io.codecks.domain.automation.AutomationLiveTestReceipt,
    ): Boolean = false
    override suspend fun resetDefaults() = Unit
    override suspend fun exportRecipes(): Result<String> = Result.success(exported)
    override suspend fun validateRecipes(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun importRecipes(payload: String): Result<Unit> {
        importCalls += 1
        if (failNextImport) {
            failNextImport = false
            return Result.failure(IllegalStateException("Injected automation import failure"))
        }
        exported = payload
        return Result.success(Unit)
    }
}
