package io.codecks.data

import io.codecks.core.actions.ActionResult
import io.codecks.data.automation.AutomationRepository
import io.codecks.domain.DeckAction
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.backup.BackupRestoreResult
import io.codecks.domain.backup.CompatibilityVerdict
import io.codecks.domain.backup.RestorePlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecksBackupRepositoryTest {
    @Test
    fun archiveRoundTripRequiresPreviewAndConfirmedTransactionalRestore() = runTest {
        val deckPayload = "{\"schemaVersion\":3,\"items\":[]}"
        val automationPayload = "{\"schemaVersion\":3,\"items\":[]}"
        val source = CodecksBackupRepository(
            FakeBackupActionRepository(deckPayload),
            FakeBackupAutomationRepository(automationPayload),
        )
        val targetDeck = FakeBackupActionRepository("{}")
        val targetAutomations = FakeBackupAutomationRepository("{}")
        val target = CodecksBackupRepository(targetDeck, targetAutomations)

        val archive = source.exportArchive().getOrThrow()
        val plan = target.createRestorePlan(archive).getOrThrow() as RestorePlan.Ready
        val outcome = target.restoreConfirmed(plan.planId, archive).getOrThrow()

        assertTrue(source.compatibilityVerdict(archive) is CompatibilityVerdict.Compatible)
        assertTrue(outcome is BackupRestoreResult.Committed)
        assertEquals(deckPayload, targetDeck.imported)
        assertEquals(automationPayload, targetAutomations.imported)
    }

    @Test
    fun previewBlocksLegacyPayloadMarkedAsContainingCredentialStores() = runTest {
        val repository = CodecksBackupRepository(
            FakeBackupActionRepository("{}"),
            FakeBackupAutomationRepository("{}"),
        )
        val payload = """
            {
              "schemaVersion": 1,
              "credentialStoresIncluded": true,
              "deck": "{}",
              "automations": "{}"
            }
        """.trimIndent().toByteArray()

        assertTrue(repository.createRestorePlan(payload).getOrThrow() is RestorePlan.Blocked)
    }

    @Test
    fun repositoryExposesNoDirectStringImportOrExportBypass() {
        val source = java.io.File("src/main/java/io/codecks/data/CodecksBackupRepository.kt").readText()

        assertTrue(!source.contains("suspend fun export():"))
        assertTrue(!source.contains("suspend fun import(payload: String)"))
    }
}

private class FakeBackupActionRepository(exported: String) : ActionRepository {
    private var current = exported
    var imported: String? = null
    override fun favorites(): List<DeckAction> = emptyList()
    override fun observeFavorites(): Flow<List<DeckAction>> = flowOf(emptyList())
    override fun allActions(): List<DeckAction> = emptyList()
    override suspend fun saveFavorites(actions: List<DeckAction>) = Unit
    override suspend fun run(action: DeckAction): Result<String> = Result.success("")
    override suspend fun test(action: DeckAction): Result<String> = Result.success("")
    override suspend fun exportLayout(): Result<String> = Result.success(current)
    override suspend fun validateLayout(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun importLayout(payload: String): Result<Unit> {
        imported = payload
        current = payload
        return Result.success(Unit)
    }
}

private class FakeBackupAutomationRepository(exported: String) : AutomationRepository {
    private var current = exported
    var imported: String? = null
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
    override suspend fun exportRecipes(): Result<String> = Result.success(current)
    override suspend fun validateRecipes(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun importRecipes(payload: String): Result<Unit> {
        imported = payload
        current = payload
        return Result.success(Unit)
    }
}
