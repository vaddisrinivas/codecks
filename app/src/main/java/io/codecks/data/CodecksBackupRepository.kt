package io.codecks.data

import io.codecks.data.automation.AutomationRepository
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** Local, user-directed backup. Credentials and connection secrets are never included. */
@Singleton
class CodecksBackupRepository @Inject constructor(
    private val actionRepository: ActionRepository,
    private val automationRepository: AutomationRepository,
) {
    suspend fun export(): Result<String> = runCatching {
        val deck = actionRepository.exportLayout().getOrThrow()
        val automations = automationRepository.exportRecipes().getOrThrow()
        JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("product", "Codecks")
            put("exportedAtMillis", System.currentTimeMillis())
            put("credentialStoresIncluded", false)
            put("deck", deck)
            put("automations", automations)
        }.toString(2)
    }

    suspend fun import(payload: String): Result<Unit> = runCatching {
        val root = JSONObject(payload)
        require(root.optInt("schemaVersion", 0) in 1..SCHEMA_VERSION) { "Unsupported Codecks backup" }
        require(!root.optBoolean("credentialStoresIncluded", true)) { "Backups containing credential stores are rejected" }
        val deck = root.getString("deck")
        val automations = root.getString("automations")
        require(deck.startsWith("{") && automations.startsWith("{")) { "Invalid Codecks backup" }
        actionRepository.validateLayout(deck).getOrThrow()
        automationRepository.validateRecipes(automations).getOrThrow()
        actionRepository.importLayout(deck).getOrThrow()
        automationRepository.importRecipes(automations).getOrThrow()
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
