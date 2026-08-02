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
import io.codecks.domain.automation.AutomationLiveTestAssertion
import io.codecks.domain.automation.AutomationLiveTestCleanupCode
import io.codecks.domain.automation.AutomationLiveTestCleanup
import io.codecks.domain.automation.AutomationLiveTestOutcomeCode
import io.codecks.domain.automation.AutomationLiveTestReceipt
import io.codecks.domain.automation.AutomationLiveTestTerminalStatus
import io.codecks.domain.automation.AutomationStepTerminalStatus
import io.codecks.domain.automation.AutomationUndoGuarantee
import io.codecks.domain.automation.AutomationGateStamp
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationStage
import io.codecks.domain.automation.AutomationPreflightArea
import io.codecks.domain.automation.AutomationPreflightCheck
import io.codecks.domain.automation.AutomationPreflightReceipt
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationSafety
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.AutomationWorkerOutcome
import io.codecks.domain.automation.AutomationWorkerOutcomeCode
import io.codecks.domain.automation.AutomationWorkerRetryDisposition
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.automation.revisionFingerprint
import io.codecks.domain.automation.enforceRevisionGate
import io.codecks.domain.automation.normalizePersistedRevisionGate
import io.codecks.domain.automation.isOpaqueAutomationConnectionIdentity
import io.codecks.domain.automation.withoutImportedExecutionProof
import io.codecks.domain.automation.withLiveTestReceipt
import io.codecks.domain.automation.withPreflightReceipt
import io.codecks.domain.automation.withValidationResult
import io.codecks.domain.automation.redactedTerminal
import io.codecks.domain.automation.withWorkerOutcome
import io.codecks.domain.privacy.DiagnosticRedactor
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticResultCode
import io.codecks.domain.device.DeviceGroupId
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.connection.CapabilityCheck
import io.codecks.domain.connection.CapabilityStatus
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.domain.connection.RemediationAction
import io.codecks.domain.connection.persistedCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject

private val Context.automationDataStore by preferencesDataStore(name = "automations")
private const val RECIPES_SCHEMA_VERSION = 8
private const val TAG = "AutomationStorage"

interface AutomationRepository {
    val recipes: Flow<List<AutomationRecipe>>
    suspend fun save(recipe: AutomationRecipe)
    suspend fun delete(recipeId: String)
    suspend fun duplicate(recipeId: String)
    suspend fun recordRun(recipeId: String, result: ActionResult)
    suspend fun recordTest(recipeId: String, result: ActionResult, revision: String) = Unit
    suspend fun recordPreflight(recipeId: String, receipt: AutomationPreflightReceipt) = Unit
    suspend fun recordLiveTest(recipeId: String, receipt: AutomationLiveTestReceipt) = Unit
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
    override val recipes: Flow<List<AutomationRecipe>> = context.automationDataStore.data
        .onStart {
            migratePersistedTargetSelectors()
            sanitizePersistedExecutionProofs()
        }
        .map { preferences ->
            preferences[RECIPES]
                ?.let { raw ->
                    decodeRecipes(raw) ?: run {
                        reportRecipeDecodeFailure(raw)
                        null
                    }
                }
                ?.takeIf { it.isNotEmpty() }
                ?: defaultRecipes()
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

    override suspend fun recordLiveTest(recipeId: String, receipt: AutomationLiveTestReceipt) {
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id == recipeId) {
                    recipe.withLiveTestReceipt(receipt)
                } else {
                    recipe
                }
            }
        }
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
        context.automationDataStore.edit { preferences ->
            val raw = preferences[RECIPES]
            val decoded = raw?.let(::decodeRecipes)
            if (raw != null && decoded == null) {
                preferences[RECIPES_QUARANTINE] = quarantinePayload(raw, "recipes")
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

    /**
     * Every persisted schema is named here. Additive fields are decoded with fail-closed defaults;
     * execution gates are then rebuilt by normalizePersistedRevisionGate().
     */
    private fun migrateRecipeForDecode(item: JSONObject, sourceSchema: Int): JSONObject =
        when (sourceSchema) {
            1, 2, 3, 4, 5, 6, 7, RECIPES_SCHEMA_VERSION -> item
            else -> error("Unsupported automation schema $sourceSchema")
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
            val sanitized = decodeRecipes(raw) ?: return@edit
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
        Log.w(TAG, "Automation recipe decode failed; preserving raw value for recovery (${raw.length} chars)")
    }
}

private fun ActionResult.toStoredAutomationSummary(): AutomationRunSummary =
    AutomationRunSummary(
        status = status,
        message = DiagnosticRedactor.redact(message, maxLength = 240),
        logs = DiagnosticRedactor.redact(logs, maxLength = 1_200),
        timestampMillis = timestampMillis,
    )

private fun quarantinePayload(raw: String, store: String): String = JSONObject().apply {
    put("schemaVersion", 1)
    put("store", store)
    put("quarantinedAtMillis", System.currentTimeMillis())
    put("raw", raw)
}.toString()

private fun AutomationRunSummary.toJson(): JSONObject = JSONObject().apply {
    put("status", status.name)
    put("message", message)
    put("logs", logs)
    put("timestampMillis", timestampMillis)
}

private fun AutomationWorkerOutcome.toJson(): JSONObject = JSONObject().apply {
    put("code", code.persistedCode)
    put("checkedAtMillis", checkedAtMillis)
    put("scheduledRevision", scheduledRevision)
    put("currentRevision", currentRevision)
    put("retryDisposition", retryDisposition.persistedCode)
    put("workerAttempt", workerAttempt)
}

private fun JSONObject?.toAutomationWorkerOutcome(): AutomationWorkerOutcome? {
    if (this == null) return null
    return AutomationWorkerOutcome(
        code = AutomationWorkerOutcomeCode.fromPersistedCode(optString("code")),
        checkedAtMillis = optLong("checkedAtMillis", 0L),
        scheduledRevision = optString("scheduledRevision"),
        currentRevision = optString("currentRevision"),
        retryDisposition = AutomationWorkerRetryDisposition.fromPersistedCode(
            optString("retryDisposition"),
        ),
        workerAttempt = optInt("workerAttempt", 0),
    )
}

private fun JSONObject.toAutomationRunSummary(): AutomationRunSummary =
    AutomationRunSummary(
        status = optString("status").let { status ->
            ActionResultStatus.entries.firstOrNull { it.name == status }
                ?: ActionResultStatus.Failed
        },
        message = optString("message"),
        logs = optString("logs").ifBlank { optString("message") },
        timestampMillis = optLong("timestampMillis", System.currentTimeMillis()),
    )

private fun JSONObject.putCommonTrust(spec: ActionSpec) {
    put("commandOrigin", spec.commandOrigin.name)
    put("commandReview", spec.review.toJson())
    put("confirmationTitle", spec.confirmationTitle)
    put("confirmationBody", spec.confirmationBody)
    put("riskReason", spec.riskReason)
    put("executionAuthorization", spec.authorization.toJson())
}

private fun CommandReview.toJson(): JSONObject = JSONObject().apply {
    put("reviewedRevision", reviewedRevision)
    put("checkedRevision", checkedRevision)
}

private fun AutomationPreflightReceipt.toJson(): JSONObject = JSONObject().apply {
    require(isOpaqueAutomationConnectionIdentity(macIdentity)) {
        "Automation preflight identity must be opaque."
    }
    put("receiptId", receiptId)
    put("recipeRevision", recipeRevision)
    put("checkedAtMillis", checkedAtMillis)
    put("macIdentity", macIdentity)
    put("targetId", targetId)
    put("requiredCapabilities", JSONArray().apply {
        requiredCapabilities.forEach { put(it.name) }
    })
    put("commandTools", JSONArray().apply {
        commandTools.forEach { put(it) }
    })
    put("commandPaths", JSONArray().apply {
        commandPaths.forEach { put(it) }
    })
    put("permissionSnapshot", JSONArray().apply {
        permissionSnapshot.forEach { put(it) }
    })
    put("checks", JSONArray().apply {
        checks.forEach { check ->
            put(
                JSONObject().apply {
                    put("area", check.area.name)
                    put("passed", check.passed)
                    put("message", check.message)
                    put("capabilityCode", check.capability.capabilityCode)
                    put("capabilityStatus", check.capability.status.persistedCode)
                    check.capability.issueCode?.let { put("issueCode", it.persistedCode) }
                    check.capability.remediation?.let { remediation ->
                        put("remediationCode", remediation.persistedCode)
                        if (remediation is RemediationAction.OpenMissingToolInstructions) {
                            put("remediationToolCode", remediation.toolCode)
                        }
                    }
                    put("checkedAtEpochMs", check.capability.checkedAtEpochMs)
                    check.capability.validUntilEpochMs?.let { put("validUntilEpochMs", it) }
                    put("mandatory", check.mandatory)
                },
            )
        }
    })
}

private fun JSONObject.toAutomationPreflightReceipt(): AutomationPreflightReceipt? {
    val preflight = this
    val macIdentity = preflight.optString("macIdentity")
        .takeIf(::isOpaqueAutomationConnectionIdentity)
        ?: return null
    return AutomationPreflightReceipt(
        recipeRevision = preflight.optString("recipeRevision").ifBlank { return null },
        checkedAtMillis = preflight.optLong("checkedAtMillis", 0L),
        macIdentity = macIdentity,
        targetId = preflight.optString("targetId").ifBlank { "current" },
        requiredCapabilities = preflight.optJSONArray("requiredCapabilities")?.toSmartCapabilities().orEmpty(),
        checks = preflight.optJSONArray("checks")?.toPreflightChecks().orEmpty(),
        commandTools = preflight.optJSONArray("commandTools")?.toStringSet().orEmpty(),
        commandPaths = preflight.optJSONArray("commandPaths")?.toStringSet().orEmpty(),
        permissionSnapshot = preflight.optJSONArray("permissionSnapshot")?.toStringSet().orEmpty(),
        receiptId = preflight.optString("receiptId").ifBlank {
            io.codecks.domain.automation.automationReceiptId(
                "preflight",
                preflight.optString("recipeRevision"),
                preflight.optLong("checkedAtMillis", 0L),
            )
        },
    )
}

private fun JSONObject.toAutomationLiveTestReceipt(): AutomationLiveTestReceipt? {
    return AutomationLiveTestReceipt(
        recipeRevision = optString("recipeRevision").ifBlank { return null },
        checkedAtMillis = optLong("checkedAtMillis", 0L),
        preflightCheckedAtMillis = optLong("preflightCheckedAtMillis", 0L),
        assertions = optJSONArray("assertions")?.toLiveTestAssertions().orEmpty(),
        cleanup = optJSONObject("cleanup")?.toAutomationLiveTestCleanup() ?: return null,
        macIdentity = "",
        normalizedPlanHash = optString("normalizedPlanHash"),
        preflightReceiptId = optString("preflightReceiptId"),
        timeoutPolicyCode = optString("timeoutPolicyCode"),
        terminalStatus = AutomationLiveTestTerminalStatus.fromPersistedCode(
            optString("terminalStatus"),
        ),
        recoveryRequired = optBoolean("recoveryRequired", false),
        completedAtMillis = optLong(
            "completedAtMillis",
            optLong("checkedAtMillis", 0L),
        ),
        receiptId = optString("receiptId"),
    ).redactedTerminal()
}

private fun AutomationLiveTestReceipt.toJson(): JSONObject = JSONObject().apply {
    val terminal = redactedTerminal()
    put("receiptId", terminal.receiptId)
    put("recipeRevision", terminal.recipeRevision)
    put("checkedAtMillis", terminal.checkedAtMillis)
    put("completedAtMillis", terminal.completedAtMillis)
    put("preflightCheckedAtMillis", terminal.preflightCheckedAtMillis)
    put("normalizedPlanHash", terminal.normalizedPlanHash)
    put("preflightReceiptId", terminal.preflightReceiptId)
    put("timeoutPolicyCode", terminal.timeoutPolicyCode)
    put("terminalStatus", terminal.terminalStatus.persistedCode)
    put("recoveryRequired", terminal.recoveryRequired)
    put("assertions", JSONArray().apply {
        terminal.assertions.forEach { assertion ->
            put(
                JSONObject().apply {
                    put("assertionId", assertion.assertionId)
                    put("actionRevision", assertion.actionRevision)
                    put("ordinal", assertion.ordinal)
                    put("passed", assertion.passed)
                    put("outcomeCode", assertion.outcomeCode.persistedCode)
                    put("stepTerminalStatus", assertion.terminalStatus.persistedCode)
                },
            )
        }
    })
    put("cleanup", terminal.cleanup.toJson())
}

private fun AutomationGateStamp.toJson(): JSONObject = JSONObject().apply {
    put("revisionId", revisionId)
    put("policyVersion", policyVersion)
    put("capabilityFingerprint", capabilityFingerprint)
    hostTrustVersion?.let { put("hostTrustVersion", it) }
    validationReceiptId?.let { put("validationReceiptId", it) }
    preflightReceiptId?.let { put("preflightReceiptId", it) }
    liveTestReceiptId?.let { put("liveTestReceiptId", it) }
}

private fun JSONObject.toAutomationGateStamp(): AutomationGateStamp? =
    AutomationGateStamp(
        revisionId = optString("revisionId").ifBlank { return null },
        policyVersion = optInt("policyVersion", 0),
        capabilityFingerprint = optString("capabilityFingerprint"),
        hostTrustVersion = optString("hostTrustVersion").takeIf(String::isNotBlank),
        validationReceiptId = optString("validationReceiptId").takeIf(String::isNotBlank),
        preflightReceiptId = optString("preflightReceiptId").takeIf(String::isNotBlank),
        liveTestReceiptId = optString("liveTestReceiptId").takeIf(String::isNotBlank),
    )

private fun AutomationLiveTestCleanup.toJson(): JSONObject = JSONObject().apply {
    put("passed", passed)
    put("outcomeCode", outcomeCode.persistedCode)
    put("cleanupId", cleanupId)
    put("actionRevision", actionRevision)
    put("undoGuarantee", undoGuarantee.persistedCode)
}

private fun JSONObject.toAutomationLiveTestCleanup(): AutomationLiveTestCleanup? {
    return AutomationLiveTestCleanup(
        command = "",
        passed = optBoolean("passed", false),
        message = AutomationLiveTestCleanupCode.fromPersistedCode(
            optString("outcomeCode"),
        ).persistedCode,
        outcomeCode = AutomationLiveTestCleanupCode.fromPersistedCode(
            optString("outcomeCode"),
        ),
        cleanupId = optString("cleanupId"),
        actionRevision = optString("actionRevision"),
        undoGuarantee = AutomationUndoGuarantee.fromPersistedCode(
            optString("undoGuarantee"),
        ),
    )
}

private fun JSONArray.toStringSet(): Set<String> = buildSet {
    repeat(length()) { index ->
        val value = optString(index).ifBlank { return@repeat }
        add(value)
    }
}

private fun JSONArray.toSmartCapabilities(): Set<SmartCapability> = buildSet {
    repeat(length()) { index ->
        val capability = runCatching {
            SmartCapability.valueOf(optString(index))
        }.getOrNull()
        capability?.let(::add)
    }
}

private fun JSONArray.toPreflightChecks(): List<AutomationPreflightCheck> = buildList {
    repeat(length()) { index ->
        val item = getJSONObject(index)
        val area = runCatching {
            AutomationPreflightArea.valueOf(item.optString("area"))
        }.getOrNull() ?: AutomationPreflightArea.Identity
        val capabilityCode = item.optString("capabilityCode")
        if (capabilityCode.isBlank()) {
            add(
                AutomationPreflightCheck(
                    area = area,
                    passed = item.optBoolean("passed", false),
                    message = item.optString("message").ifBlank { "check failed" },
                ),
            )
            return@repeat
        }
        val status = CapabilityStatus.fromPersistedCode(item.optString("capabilityStatus"))
        val issue = item.optString("issueCode")
            .takeIf(String::isNotBlank)
            ?.let(ConnectionIssueCode::fromPersistedCode)
            ?: ConnectionIssueCode.UNKNOWN.takeIf {
                status == CapabilityStatus.RETRYABLE || status == CapabilityStatus.BLOCKED
            }
        val remediation = item.toRemediationAction(issue)
        add(
            AutomationPreflightCheck(
                area = area,
                passed = status == CapabilityStatus.SATISFIED,
                message = item.optString("message").ifBlank { "check failed" },
                capability = CapabilityCheck(
                    capabilityCode = capabilityCode,
                    status = status,
                    issueCode = issue,
                    remediation = remediation,
                    checkedAtEpochMs = item.optLong("checkedAtEpochMs", 0L),
                    validUntilEpochMs = item.optLong("validUntilEpochMs")
                        .takeIf { item.has("validUntilEpochMs") },
                ),
                mandatory = item.optBoolean("mandatory", true),
            ),
        )
    }
}

private fun JSONObject.toRemediationAction(
    issue: ConnectionIssueCode?,
): RemediationAction? {
    val decoded = when (optString("remediationCode")) {
        "request_bluetooth_permission" -> RemediationAction.RequestBluetoothPermission
        "open_bluetooth_settings" -> RemediationAction.OpenBluetoothSettings
        "open_system_pairing" -> RemediationAction.OpenSystemPairing
        "retry_hid_registration" -> RemediationAction.RetryHidRegistration
        "retry_connection_now" -> RemediationAction.RetryConnectionNow
        "open_mac_wake_help" -> RemediationAction.OpenMacWakeHelp
        "reenter_ssh_credentials" -> RemediationAction.ReenterSshCredentials
        "review_changed_host_key" -> RemediationAction.ReviewChangedHostKey
        "open_missing_tool_instructions" -> RemediationAction.OpenMissingToolInstructions(
            optString("remediationToolCode").ifBlank { "unknown" },
        )
        "contact_support" -> RemediationAction.ContactSupport
        else -> null
    }
    return decoded
        ?: issue?.remediations?.firstOrNull()
        ?: RemediationAction.ContactSupport.takeIf {
            CapabilityStatus.fromPersistedCode(optString("capabilityStatus")) in
                setOf(CapabilityStatus.RETRYABLE, CapabilityStatus.BLOCKED)
        }
}

private fun JSONArray.toLiveTestAssertions(): List<AutomationLiveTestAssertion> = buildList {
    repeat(length()) { index ->
        val item = getJSONObject(index)
        add(
            AutomationLiveTestAssertion(
                stepId = item.optString("assertionId").ifBlank { "terminal" },
                stepTitle = if (item.optInt("ordinal", -1) >= 0) {
                    "Action ${item.optInt("ordinal") + 1}"
                } else {
                    "Live test"
                },
                passed = item.optBoolean("passed", false),
                message = AutomationLiveTestOutcomeCode.fromPersistedCode(
                    item.optString("outcomeCode"),
                ).persistedCode,
                assertionId = item.optString("assertionId"),
                actionRevision = item.optString("actionRevision"),
                outcomeCode = AutomationLiveTestOutcomeCode.fromPersistedCode(
                    item.optString("outcomeCode"),
                ),
                terminalStatus = AutomationStepTerminalStatus.fromPersistedCode(
                    item.optString("stepTerminalStatus"),
                ),
                ordinal = item.optInt("ordinal", -1),
            ),
        )
    }
}

private fun JSONObject?.toCommandReview(): CommandReview {
    if (this == null) return CommandReview()
    return CommandReview(
        reviewedRevision = optString("reviewedRevision").takeIf(String::isNotBlank),
        checkedRevision = optString("checkedRevision").takeIf(String::isNotBlank),
    )
}

private fun ExecutionAuthorization.toJson(): JSONObject = JSONObject().apply {
    put("dangerousRevisionConfirmed", dangerousRevisionConfirmed)
}

private fun JSONObject?.toExecutionAuthorization(): ExecutionAuthorization {
    if (this == null) return ExecutionAuthorization()
    return ExecutionAuthorization(
        dangerousRevisionConfirmed = optString("dangerousRevisionConfirmed").takeIf(String::isNotBlank),
    )
}

private fun JSONObject.optCommandOrigin(fallback: CommandOrigin): CommandOrigin =
    optString("commandOrigin").takeIf(String::isNotBlank)
        ?.let { runCatching { CommandOrigin.valueOf(it) }.getOrNull() }
        ?: fallback

private fun TargetSelector.toJson(): JSONObject = JSONObject().apply {
    when (val selector = this@toJson) {
        TargetSelector.CurrentDevice -> put("type", "current")
        TargetSelector.AllCompatibleDevices -> put("type", "all")
        TargetSelector.AskAtRunTime -> put("type", "ask")
        is TargetSelector.SpecificDevice -> {
            put("type", "device")
            put("id", selector.deviceId.value)
        }
        is TargetSelector.DeviceGroup -> {
            put("type", "group")
            put("id", selector.groupId.value)
        }
    }

}

private fun JSONObject?.toTargetSelector(): TargetSelector {
    if (this == null) return TargetSelector.CurrentDevice
    return when (optString("type")) {
        "all" -> TargetSelector.AllCompatibleDevices
        "ask" -> TargetSelector.AskAtRunTime
        "device" -> optString("id").takeIf(String::isNotBlank)
            ?.let { TargetSelector.SpecificDevice(DeviceId(it)) }
            ?: TargetSelector.CurrentDevice
        "group" -> optString("id").takeIf(String::isNotBlank)
            ?.let { TargetSelector.DeviceGroup(DeviceGroupId(it)) }
            ?: TargetSelector.CurrentDevice
        else -> TargetSelector.CurrentDevice
    }
}

private fun AutomationTrigger.toJson(): JSONObject = JSONObject().apply {
    when (this@toJson) {
        AutomationTrigger.Manual -> put("type", "manual")
        is AutomationTrigger.AiSuggested -> {
            put("type", "ai")
            put("prompt", prompt)
        }
        is AutomationTrigger.TimeOfDay -> {
            put("type", "time")
            put("hour", hour)
            put("minute", minute)
            put("days", JSONArray().apply { days.forEach(::put) })
        }
        is AutomationTrigger.ActiveApp -> {
            put("type", "app")
            put("appName", appName)
        }
        is AutomationTrigger.ClipboardContains -> {
            put("type", "clipboard")
            put("text", text)
        }
        is AutomationTrigger.WifiSsid -> {
            put("type", "wifi")
            put("ssid", ssid)
        }
        AutomationTrigger.MacAwake -> put("type", "mac_awake")
        is AutomationTrigger.FileChanged -> {
            put("type", "file")
            put("path", path)
        }
        is AutomationTrigger.BatteryBelow -> {
            put("type", "battery")
            put("percent", percent)
        }
    }
}

private fun JSONObject.trigger(): AutomationTrigger {
    val raw = opt("trigger")
    return when (raw) {
        is JSONObject -> raw.toTrigger()
        is String -> raw.toTrigger()
        else -> AutomationTrigger.Manual
    }
}

private fun JSONObject.toTrigger(): AutomationTrigger =
    when (optString("type")) {
        "ai" -> AutomationTrigger.AiSuggested(optString("prompt"))
        "time" -> AutomationTrigger.TimeOfDay(
            hour = optInt("hour", 9),
            minute = optInt("minute", 0),
            days = optJSONArray("days")?.let { days ->
                buildSet {
                    repeat(days.length()) { index -> add(days.optString(index)) }
                }
            }.orEmpty(),
        )
        "app" -> AutomationTrigger.ActiveApp(optString("appName").ifBlank { "App" })
        "clipboard" -> AutomationTrigger.ClipboardContains(optString("text"))
        "wifi" -> AutomationTrigger.WifiSsid(optString("ssid").ifBlank { "Wi-Fi" })
        "mac_awake" -> AutomationTrigger.MacAwake
        "file" -> AutomationTrigger.FileChanged(optString("path").ifBlank { "~" })
        "battery" -> AutomationTrigger.BatteryBelow(optInt("percent", 20).coerceIn(1, 100))
        else -> AutomationTrigger.Manual
    }

private fun String.toTrigger(): AutomationTrigger =
    if (startsWith("ai:")) AutomationTrigger.AiSuggested(removePrefix("ai:")) else AutomationTrigger.Manual
