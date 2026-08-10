package io.codecks.data.automation

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.ShellTrustLevel
import io.codecks.data.ActionRepository
import io.codecks.data.ConnectionRepository
import io.codecks.data.PersistedTargetSelectorMigration
import io.codecks.data.migrateAutomationTargetSelectorPayload
import io.codecks.data.privacy.DiagnosticEventStore
import io.codecks.data.privacy.recordTerminal
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.ExecutionAuthorization
import io.codecks.domain.automation.AutomationCatalog
import io.codecks.domain.automation.AutomationCleanupDefinition
import io.codecks.domain.automation.AutomationCleanupTrigger
import io.codecks.domain.automation.AutomationLiveTestReceipt
import io.codecks.domain.automation.AutomationUndoGuarantee
import io.codecks.domain.automation.AutomationGateStamp
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationStage
import io.codecks.domain.automation.AutomationPreflightReceipt
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationSafety
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.AutomationWorkerOutcome
import io.codecks.domain.automation.AutomationWorkerOutcomeCode
import io.codecks.domain.automation.enforceRevisionGate
import io.codecks.domain.automation.normalizePersistedRevisionGate
import io.codecks.domain.automation.withoutImportedExecutionProof
import io.codecks.domain.automation.withLiveTestReceipt
import io.codecks.domain.automation.withPreflightReceipt
import io.codecks.domain.automation.withValidationResult
import io.codecks.domain.automation.withWorkerOutcome
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticResultCode
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.connection.persistedCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject

private val Context.automationDataStore by preferencesDataStore(name = "automations")
private const val RECIPES_SCHEMA_VERSION = 9
private const val TAG = "AutomationStorage"

interface AutomationRepository {
    val recipes: Flow<List<AutomationRecipe>>
    val recoveryRequired: Flow<Boolean> get() = flowOf(false)
    suspend fun save(recipe: AutomationRecipe)
    suspend fun delete(recipeId: String)
    suspend fun duplicate(recipeId: String)
    suspend fun recordRun(recipeId: String, result: ActionResult)
    suspend fun recordTest(recipeId: String, result: ActionResult, revision: String) = Unit
    suspend fun recordPreflight(recipeId: String, receipt: AutomationPreflightReceipt) = Unit
    suspend fun recordLiveTest(recipeId: String, receipt: AutomationLiveTestReceipt): Boolean
    suspend fun recordWorkerOutcome(recipeId: String, outcome: AutomationWorkerOutcome) = Unit
    suspend fun clearPendingApproval(recipeId: String) = Unit
    suspend fun exportRecipes(): Result<String>
    suspend fun validateRecipes(payload: String): Result<Unit>
    suspend fun importRecipes(payload: String): Result<Unit>
    suspend fun resetDefaults()
}

@Singleton
class DefaultAutomationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actionRepository: ActionRepository,
    private val connectionRepository: ConnectionRepository,
) : AutomationRepository {
    private val diagnosticEventStore = DiagnosticEventStore(context)
    override val recoveryRequired: Flow<Boolean> = context.automationDataStore.data.map { preferences ->
        preferences[RECIPES]?.let(::decodeRecipes) == null && preferences[RECIPES] != null
    }
    override val recipes: Flow<List<AutomationRecipe>> = context.automationDataStore.data
        .onStart {
            migratePersistedTargetSelectors()
            sanitizePersistedExecutionProofs()
        }
        .map { preferences ->
            val raw = preferences[RECIPES] ?: return@map defaultRecipes()
            val decoded = decodeRecipes(raw) ?: run {
                reportRecipeDecodeFailure(raw)
                return@map emptyList()
            }
            decoded.ifEmpty { defaultRecipes() }
        }

    override suspend fun save(recipe: AutomationRecipe) {
        mutate { recipes ->
            val existing = recipes.indexOfFirst { it.id == recipe.id }
            val previous = recipes.getOrNull(existing)
            val safeRecipe = recipe.enforceRevisionGate(previous)
            if (existing >= 0) recipes.toMutableList().also { it[existing] = safeRecipe } else recipes + safeRecipe
        }
    }

    override suspend fun delete(recipeId: String) {
        mutate { recipes -> recipes.filterNot { it.id == recipeId } }
    }

    override suspend fun duplicate(recipeId: String) {
        mutate { recipes ->
            val source = recipes.firstOrNull { it.id == recipeId } ?: return@mutate recipes
            val duplicate = source.copy(
                id = "${source.id}_copy_${System.currentTimeMillis()}",
                title = "${source.title} Copy",
                enabled = false,
                lastRun = null,
                runHistory = emptyList(),
                lastTest = null,
                lastTestRevision = null,
                lastPreflight = null,
                lastLiveTest = null,
                recoveryRequired = false,
                lastWorkerOutcome = null,
                pendingApproval = null,
                stage = AutomationStage.DRAFT,
                gateStamp = null,
            ).enforceRevisionGate(previous = null)
            recipes + duplicate
        }
    }

    override suspend fun recordRun(recipeId: String, result: ActionResult) {
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id == recipeId) {
                    val summary = result.toStoredAutomationSummary()
                    recipe.copy(
                        lastRun = summary,
                        runHistory = (listOf(summary) + recipe.runHistory).take(MAX_RUN_HISTORY),
                        pendingApproval = if (result.status == ActionResultStatus.RequiresConfirmation || result.status == ActionResultStatus.RequiresReview) summary else null,
                    )
                } else {
                    recipe
                }
            }
        }
        diagnosticEventStore.recordTerminal(
            component = DiagnosticComponent.AUTOMATION,
            result = when (result.status) {
                ActionResultStatus.Succeeded -> DiagnosticResultCode.SUCCEEDED
                ActionResultStatus.Failed -> DiagnosticResultCode.FAILED
                ActionResultStatus.RequiresConfirmation,
                ActionResultStatus.RequiresReview,
                -> DiagnosticResultCode.BLOCKED
            },
            timestampEpochMs = result.timestampMillis,
        )
    }

    override suspend fun recordTest(recipeId: String, result: ActionResult, revision: String) {
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id == recipeId) {
                    recipe.withValidationResult(result, revision)
                } else {
                    recipe
                }
            }
        }
    }

    override suspend fun recordPreflight(recipeId: String, receipt: AutomationPreflightReceipt) {
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id == recipeId) {
                    recipe.withPreflightReceipt(receipt)
                } else {
                    recipe
                }
            }
        }
    }

    override suspend fun recordLiveTest(recipeId: String, receipt: AutomationLiveTestReceipt): Boolean {
        var promoted = false
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id == recipeId) {
                    recipe.withLiveTestReceipt(receipt).also { updated ->
                        promoted = updated.lastLiveTest?.receiptId == receipt.receiptId &&
                            updated.stage == AutomationStage.LIVE_TEST_PASSED
                    }
                } else {
                    recipe
                }
            }
        }
        return promoted
    }

    override suspend fun recordWorkerOutcome(
        recipeId: String,
        outcome: AutomationWorkerOutcome,
    ) {
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id != recipeId) return@map recipe
                recipe.withWorkerOutcome(outcome)
            }
        }
        diagnosticEventStore.recordTerminal(
            component = DiagnosticComponent.AUTOMATION,
            result = when (outcome.code) {
                AutomationWorkerOutcomeCode.EXECUTED,
                AutomationWorkerOutcomeCode.WORKER_RECREATED,
                -> DiagnosticResultCode.SUCCEEDED
                AutomationWorkerOutcomeCode.INTERRUPTED,
                AutomationWorkerOutcomeCode.RETRY_SCHEDULED,
                -> DiagnosticResultCode.RETRYABLE
                AutomationWorkerOutcomeCode.DISABLED -> DiagnosticResultCode.SKIPPED
                AutomationWorkerOutcomeCode.ELIGIBLE -> DiagnosticResultCode.SUCCEEDED
                else -> DiagnosticResultCode.BLOCKED
            },
            attempt = outcome.workerAttempt,
            timestampEpochMs = outcome.checkedAtMillis,
        )
    }

    override suspend fun clearPendingApproval(recipeId: String) {
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id == recipeId) recipe.copy(pendingApproval = null) else recipe
            }
        }
    }

    override suspend fun exportRecipes(): Result<String> = runCatching {
        encodeRecipes(recipes.first())
    }

    override suspend fun importRecipes(payload: String): Result<Unit> = runCatching {
        val migratedPayload = migratePayload(payload, connectionRepository.legacyTargetIdMigrations())
        validateRecipes(migratedPayload).getOrThrow()
        val imported = requireNotNull(decodeRecipes(migratedPayload))
            .map(AutomationRecipe::withoutImportedExecutionProof)
        context.automationDataStore.edit { preferences ->
            preferences[RECIPES] = encodeRecipes(imported)
        }
    }

    override suspend fun validateRecipes(payload: String): Result<Unit> = runCatching {
        requireNotNull(decodeRecipes(payload)?.takeIf { it.isNotEmpty() }) {
            "Backup contains no valid automations"
        }
        Unit
    }

    override suspend fun resetDefaults() {
        context.automationDataStore.edit { it.remove(RECIPES) }
    }

    private suspend fun mutate(transform: (List<AutomationRecipe>) -> List<AutomationRecipe>) {
        val legacyIds = connectionRepository.legacyTargetIdMigrations()
        val snapshot = context.automationDataStore.data.first()[RECIPES]
        if (snapshot != null && decodeRecipes(snapshot) == null) {
            context.automationDataStore.edit { preferences ->
                preferences[RECIPES_QUARANTINE] = quarantinePayload(snapshot, "recipes")
            }
            error("Automation storage is unreadable; reset or restore it before making changes")
        }
        context.automationDataStore.edit { preferences ->
            val raw = preferences[RECIPES]
            val decoded = raw?.let(::decodeRecipes)
            if (raw != null && decoded == null) {
                error("Automation storage is unreadable; reset or restore it before making changes")
            }
            val current = decoded?.takeIf { it.isNotEmpty() } ?: defaultRecipes()
            val encoded = encodeRecipes(transform(current))
            preferences[RECIPES] = migratePayload(encoded, legacyIds)
        }
    }

    private fun defaultRecipes(): List<AutomationRecipe> {
        val actionsById = actionRepository.allActions().associateBy { it.id }
        val focused = AutomationCatalog.focusedActionIds.mapNotNull { id ->
            val action = actionsById[id] ?: return@mapNotNull null
            AutomationRecipe(
                id = id,
                title = action.label,
                description = action.description,
                enabled = true,
                trigger = AutomationTrigger.Manual,
                steps = listOf(ActionSpec.DeckActionSpec(action)),
                safety = AutomationSafety(requiresConfirmation = action.dangerous),
            )
        }
        return (focused + AutomationCatalog.defaultRecipes(actionsById))
            .distinctBy(AutomationRecipe::id)
            .map { it.enforceRevisionGate(previous = null) }
    }

    private fun encodeRecipes(recipes: List<AutomationRecipe>): String = JSONObject().apply {
        put("schemaVersion", RECIPES_SCHEMA_VERSION)
        put("items", JSONArray().apply {
            recipes.forEach { recipe ->
                put(JSONObject().apply {
                    put("id", recipe.id)
                    put("title", recipe.title)
                    put("description", recipe.description)
                    put("enabled", recipe.enabled)
                    put("trigger", recipe.trigger.toJson())
                    put("requiresConfirmation", recipe.safety.requiresConfirmation)
                    put("steps", JSONArray().apply { recipe.steps.forEach { put(it.toJson()) } })
                    put("cleanupDefinition", recipe.cleanupDefinition.toJson())
                    put("recoveryRequired", recipe.recoveryRequired)
                    recipe.lastWorkerOutcome?.let { put("lastWorkerOutcome", it.toJson()) }
                    recipe.lastRun?.let { run -> put("lastRun", run.toJson()) }
                    recipe.lastTest?.let { test -> put("lastTest", test.toJson()) }
                    recipe.lastTestRevision?.let { revision -> put("lastTestRevision", revision) }
                    recipe.lastPreflight?.let { preflight -> put("lastPreflight", preflight.toJson()) }
                    recipe.lastLiveTest?.let { receipt -> put("lastLiveTest", receipt.toJson()) }
                    put("stage", recipe.stage.name)
                    recipe.gateStamp?.let { stamp -> put("gateStamp", stamp.toJson()) }
                    recipe.pendingApproval?.let { approval -> put("pendingApproval", approval.toJson()) }
                    put("runHistory", JSONArray().apply {
                        recipe.runHistory.forEach { run ->
                            put(run.toJson())
                        }
                    })
                })
            }
        })
    }.toString()

    private fun decodeRecipes(raw: String): List<AutomationRecipe>? = runCatching<List<AutomationRecipe>?> {
        val trimmed = raw.trimStart()
        val schemaVersion: Int
        val array = if (trimmed.startsWith("{")) {
            val root = JSONObject(raw)
            schemaVersion = root.optInt("schemaVersion", 1)
            require(schemaVersion in 1..RECIPES_SCHEMA_VERSION) {
                "Unsupported automation schema $schemaVersion"
            }
            root.optJSONArray("items") ?: JSONArray()
        } else {
            schemaVersion = 1
            JSONArray(raw)
        }
        buildList<AutomationRecipe> {
            repeat(array.length()) { index ->
                val item = migrateRecipeForDecode(array.getJSONObject(index), schemaVersion)
                val steps = item.getJSONArray("steps")
                val decodedSteps = List(steps.length()) { stepIndex ->
                    requireNotNull(steps.getJSONObject(stepIndex).toActionSpec()) {
                        "Automation ${item.optString("id")} has an unsupported step at $stepIndex"
                    }
                }
                val decoded = AutomationRecipe(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        description = item.optString("description"),
                        enabled = item.optBoolean("enabled", true),
                        trigger = item.trigger(),
                        steps = decodedSteps,
                        safety = AutomationSafety(item.optBoolean("requiresConfirmation", false)),
                        cleanupDefinition = item.optJSONObject("cleanupDefinition").toCleanupDefinition(),
                        recoveryRequired = item.optBoolean("recoveryRequired", false),
                        lastWorkerOutcome = item.optJSONObject("lastWorkerOutcome").toAutomationWorkerOutcome(),
                        lastRun = item.optJSONObject("lastRun")?.let { run ->
                            run.toAutomationRunSummary()
                        },
                        lastTest = item.optJSONObject("lastTest")?.toAutomationRunSummary(),
                        lastTestRevision = item.optString("lastTestRevision").takeIf(String::isNotBlank),
                        lastPreflight = item.optJSONObject("lastPreflight")?.toAutomationPreflightReceipt(),
                        lastLiveTest = item.optJSONObject("lastLiveTest")?.toAutomationLiveTestReceipt(),
                        pendingApproval = item.optJSONObject("pendingApproval")?.toAutomationRunSummary(),
                        stage = runCatching {
                            AutomationStage.valueOf(item.optString("stage"))
                        }.getOrDefault(AutomationStage.DRAFT),
                        gateStamp = item.optJSONObject("gateStamp")?.toAutomationGateStamp(),
                        runHistory = item.optJSONArray("runHistory")?.let { history ->
                            buildList<AutomationRunSummary> {
                                repeat(history.length()) { historyIndex ->
                                    history.optJSONObject(historyIndex)
                                        ?.toAutomationRunSummary()
                                        ?.let(::add)
                                }
                            }
                        }.orEmpty(),
                    )
                add(decoded.normalizePersistedRevisionGate())
            }
        }
    }.getOrNull()

    /** Explicit, ordered migrations. Proof is deliberately invalidated by the v8 -> v9 policy. */
    private fun migrateRecipeForDecode(item: JSONObject, sourceSchema: Int): JSONObject {
        require(sourceSchema in 1..RECIPES_SCHEMA_VERSION)
        val migrated = JSONObject(item.toString())
        for (version in sourceSchema until RECIPES_SCHEMA_VERSION) {
            when (version) {
                1 -> migrated.putIfMissing("description", "")
                2 -> migrated.putIfMissing("requiresConfirmation", false)
                3 -> migrated.putIfMissing("cleanupDefinition", JSONObject())
                4 -> migrated.putIfMissing("runHistory", JSONArray())
                5 -> migrated.putIfMissing("recoveryRequired", false)
                6 -> migrated.putIfMissing("stage", AutomationStage.DRAFT.name)
                7 -> migrated.putIfMissing("enabled", false)
                8 -> {
                    migrated.put("enabled", false)
                    migrated.put("stage", AutomationStage.DRAFT.name)
                    listOf(
                        "lastTest",
                        "lastTestRevision",
                        "lastPreflight",
                        "lastLiveTest",
                        "lastWorkerOutcome",
                        "pendingApproval",
                        "gateStamp",
                    ).forEach { key -> migrated.remove(key) }
                }
                else -> error("Missing migration from automation schema $version")
            }
        }
        return migrated
    }

    private suspend fun migratePersistedTargetSelectors() {
        val legacyIds = connectionRepository.legacyTargetIdMigrations()
        if (legacyIds.isEmpty()) return
        context.automationDataStore.edit { preferences ->
            val raw = preferences[RECIPES] ?: return@edit
            when (val migration = migrateAutomationTargetSelectorPayload(raw, legacyIds)) {
                is PersistedTargetSelectorMigration.Migrated -> {
                    if (decodeRecipes(migration.payload) == null) {
                        preferences[RECIPES_QUARANTINE] = quarantinePayload(raw, "recipes")
                    } else {
                        preferences[RECIPES] = migration.payload
                    }
                }
                PersistedTargetSelectorMigration.Undecodable -> {
                    preferences[RECIPES_QUARANTINE] = quarantinePayload(raw, "recipes")
                }
                PersistedTargetSelectorMigration.Unchanged -> Unit
            }
        }
    }

    private suspend fun sanitizePersistedExecutionProofs() {
        context.automationDataStore.edit { preferences ->
            val raw = preferences[RECIPES] ?: return@edit
            val sanitized = decodeRecipes(raw) ?: run {
                preferences[RECIPES_QUARANTINE] = quarantinePayload(raw, "recipes")
                return@edit
            }
            val encoded = encodeRecipes(sanitized)
            if (encoded != raw) preferences[RECIPES] = encoded
        }
    }

    private fun migratePayload(
        raw: String,
        legacyIds: Map<String, String>,
    ): String = when (val migration = migrateAutomationTargetSelectorPayload(raw, legacyIds)) {
        is PersistedTargetSelectorMigration.Migrated -> migration.payload
        PersistedTargetSelectorMigration.Unchanged -> raw
        PersistedTargetSelectorMigration.Undecodable -> error("Automation payload could not be decoded")
    }

    private fun ActionSpec.toJson(): JSONObject = JSONObject().apply {
        when (this@toJson) {
            is ActionSpec.DeckActionSpec -> {
                put("type", "deck")
                put("id", action.id)
                put("title", action.label)
                put("dangerous", action.dangerous)
                put("target", targetSelector.toJson())
                putCommonTrust(this@toJson)
            }
            is ActionSpec.CatalogAction -> {
                put("type", "catalog")
                put("id", id)
                put("title", title)
                put("dangerous", dangerous)
                put("target", targetSelector.toJson())
                putCommonTrust(this@toJson)
            }
            is ActionSpec.ShellCommand -> {
                put("type", "shell")
                put("id", id)
                put("title", title)
                put("command", command)
                put("trustLevel", trustLevel.name)
                put("dangerous", dangerous)
                put("target", targetSelector.toJson())
                putCommonTrust(this@toJson)
            }
            is ActionSpec.LocalRoute -> {
                put("type", "local")
                put("id", id)
                put("title", title)
                put("route", route)
                put("target", targetSelector.toJson())
                putCommonTrust(this@toJson)
            }
        }
    }

    private fun JSONObject.toActionSpec(): ActionSpec? {
        val id = optString("id").takeIf(String::isNotBlank) ?: return null
        val title = optString("title").ifBlank { id }
        val dangerous = optBoolean("dangerous", false)
        val target = optJSONObject("target").toTargetSelector()
        return when (optString("type")) {
            "deck", "catalog" -> ActionSpec.CatalogAction(
                id = id,
                title = title,
                dangerous = dangerous,
                targetSelector = target,
                commandOrigin = optCommandOrigin(CommandOrigin.Bundled),
                review = optJSONObject("commandReview").toCommandReview(),
                confirmationTitle = optString("confirmationTitle").takeIf(String::isNotBlank),
                confirmationBody = optString("confirmationBody").takeIf(String::isNotBlank),
                riskReason = optString("riskReason").takeIf(String::isNotBlank),
                authorization = optJSONObject("executionAuthorization").toExecutionAuthorization(),
            )
            "shell" -> ActionSpec.ShellCommand(
                id = id,
                title = title,
                command = optString("command"),
                trustLevel = optString("trustLevel")
                    .let { raw -> ShellTrustLevel.entries.firstOrNull { it.name == raw } }
                    ?: ShellTrustLevel.UserReviewed,
                dangerous = dangerous,
                targetSelector = target,
                commandOrigin = optCommandOrigin(CommandOrigin.UserAuthored),
                review = optJSONObject("commandReview").toCommandReview(),
                confirmationTitle = optString("confirmationTitle").takeIf(String::isNotBlank),
                confirmationBody = optString("confirmationBody").takeIf(String::isNotBlank),
                riskReason = optString("riskReason").takeIf(String::isNotBlank),
                authorization = optJSONObject("executionAuthorization").toExecutionAuthorization(),
            )
            "local" -> ActionSpec.LocalRoute(
                id = id,
                title = title,
                route = optString("route"),
                targetSelector = target,
                commandOrigin = optCommandOrigin(CommandOrigin.UserAuthored),
                review = optJSONObject("commandReview").toCommandReview(),
                confirmationTitle = optString("confirmationTitle").takeIf(String::isNotBlank),
                confirmationBody = optString("confirmationBody").takeIf(String::isNotBlank),
                riskReason = optString("riskReason").takeIf(String::isNotBlank),
                authorization = optJSONObject("executionAuthorization").toExecutionAuthorization(),
            )
            else -> null
        }
    }

    private fun AutomationCleanupDefinition.toJson(): JSONObject = JSONObject().apply {
        action?.let { put("action", it.toJson()) }
        put("runAfter", JSONArray().apply {
            runAfter.map(AutomationCleanupTrigger::persistedCode).sorted().forEach(::put)
        })
        put("undoGuarantee", undoGuarantee.persistedCode)
    }

    private fun JSONObject?.toCleanupDefinition(): AutomationCleanupDefinition {
        if (this == null) return AutomationCleanupDefinition()
        val action = optJSONObject("action")?.toActionSpec() as? ActionSpec.ShellCommand
        if (action == null) return AutomationCleanupDefinition()
        val triggers = buildSet {
            val values = optJSONArray("runAfter") ?: JSONArray()
            repeat(values.length()) { index ->
                AutomationCleanupTrigger.entries
                    .firstOrNull { it.persistedCode == values.optString(index) }
                    ?.let(::add)
            }
        }
        return runCatching {
            AutomationCleanupDefinition(
                action = action,
                runAfter = triggers,
                undoGuarantee = AutomationUndoGuarantee.fromPersistedCode(
                    optString("undoGuarantee"),
                ),
            )
        }.getOrDefault(AutomationCleanupDefinition())
    }

    private companion object {
        val RECIPES = stringPreferencesKey("recipes")
        val RECIPES_QUARANTINE = stringPreferencesKey("recipes_quarantine")
        const val MAX_RUN_HISTORY = 10
    }

    private fun reportRecipeDecodeFailure(raw: String) {
        Log.w(TAG, "Automation recipe decode failed; stored hash-only recovery metadata (${raw.length} chars)")
    }
}
