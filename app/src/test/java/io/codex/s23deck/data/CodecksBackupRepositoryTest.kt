package io.codex.s23deck.data

import io.codex.s23deck.core.actions.ActionResult
import io.codex.s23deck.data.automation.AutomationRepository
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.domain.automation.AutomationRecipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecksBackupRepositoryTest {
    @Test
    fun exportAndImportRoundTripOnlyDeckAndAutomations() = runTest {
        val deck = FakeBackupActionRepository("{\"schemaVersion\":3,\"items\":[]}")
        val automations = FakeBackupAutomationRepository("{\"schemaVersion\":3,\"items\":[]}")
        val repository = CodecksBackupRepository(deck, automations)

        val payload = repository.export().getOrThrow()
        val root = JSONObject(payload)

        assertFalse(root.getBoolean("credentialStoresIncluded"))
        assertFalse(payload.contains("apiKey"))
        assertFalse(payload.contains("privateKey"))
        repository.import(payload).getOrThrow()
        assertEquals(root.getString("deck"), deck.imported)
        assertEquals(root.getString("automations"), automations.imported)
    }

    @Test
    fun importRejectsPayloadMarkedAsContainingSecrets() = runTest {
        val repository = CodecksBackupRepository(
            FakeBackupActionRepository("{}"),
            FakeBackupAutomationRepository("{}"),
        )
        val payload = JSONObject().apply {
            put("schemaVersion", 1)
            put("credentialStoresIncluded", true)
            put("deck", "{}")
            put("automations", "{}")
        }.toString()

        assertTrue(repository.import(payload).isFailure)
    }
}

private class FakeBackupActionRepository(private val exported: String) : ActionRepository {
    var imported: String? = null
    override fun favorites(): List<DeckAction> = emptyList()
    override fun observeFavorites(): Flow<List<DeckAction>> = flowOf(emptyList())
    override fun allActions(): List<DeckAction> = emptyList()
    override suspend fun saveFavorites(actions: List<DeckAction>) = Unit
    override suspend fun run(action: DeckAction): Result<String> = Result.success("")
    override suspend fun test(action: DeckAction): Result<String> = Result.success("")
    override suspend fun exportLayout(): Result<String> = Result.success(exported)
    override suspend fun importLayout(payload: String): Result<Unit> {
        imported = payload
        return Result.success(Unit)
    }
}

private class FakeBackupAutomationRepository(private val exported: String) : AutomationRepository {
    var imported: String? = null
    override val recipes: Flow<List<AutomationRecipe>> = flowOf(emptyList())
    override suspend fun save(recipe: AutomationRecipe) = Unit
    override suspend fun delete(recipeId: String) = Unit
    override suspend fun duplicate(recipeId: String) = Unit
    override suspend fun recordRun(recipeId: String, result: ActionResult) = Unit
    override suspend fun resetDefaults() = Unit
    override suspend fun exportRecipes(): Result<String> = Result.success(exported)
    override suspend fun importRecipes(payload: String): Result<Unit> {
        imported = payload
        return Result.success(Unit)
    }
}
