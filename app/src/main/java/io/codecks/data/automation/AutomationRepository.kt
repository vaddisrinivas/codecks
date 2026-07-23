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
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.ExecutionAuthorization
import io.codecks.domain.automation.AutomationCatalog
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationSafety
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.revisionFingerprint
import io.codecks.domain.device.DeviceGroupId
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject

private val Context.automationDataStore by preferencesDataStore(name = "automations")
private const val RECIPES_SCHEMA_VERSION = 3
private const val TAG = "AutomationStorage"

interface AutomationRepository {
    val recipes: Flow<List<AutomationRecipe>>
    suspend fun save(recipe: AutomationRecipe)
    suspend fun delete(recipeId: String)
    suspend fun duplicate(recipeId: String)
    suspend fun recordRun(recipeId: String, result: ActionResult)
    suspend fun recordTest(recipeId: String, result: ActionResult, revision: String) = Unit
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
    override val recipes: Flow<List<AutomationRecipe>> = context.automationDataStore.data
        .onStart { migratePersistedTargetSelectors() }
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
            val safeRecipe = if (previous != null && previous.revisionFingerprint() != recipe.revisionFingerprint()) {
                recipe.copy(lastTest = null, lastTestRevision = null, pendingApproval = null)
            } else {
                recipe
            }
            if (existing >= 0) recipes.toMutableList().also { it[existing] = safeRecipe } else recipes + safeRecipe
        }
    }

    override suspend fun delete(recipeId: String) {
        mutate { recipes -> recipes.filterNot { it.id == recipeId } }
    }

    override suspend fun duplicate(recipeId: String) {
        mutate { recipes ->
            val source = recipes.firstOrNull { it.id == recipeId } ?: return@mutate recipes
            recipes + source.copy(
                id = "${source.id}_copy_${System.currentTimeMillis()}",
                title = "${source.title} Copy",
                lastRun = null,
                runHistory = emptyList(),
                lastTest = null,
                lastTestRevision = null,
                pendingApproval = null,
            )
        }
    }

    override suspend fun recordRun(recipeId: String, result: ActionResult) {
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id == recipeId) {
                    val summary = AutomationRunSummary(
                        status = result.status,
                        message = result.message,
                        logs = result.logs,
                        timestampMillis = result.timestampMillis,
                    )
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
    }

    override suspend fun recordTest(recipeId: String, result: ActionResult, revision: String) {
        mutate { recipes ->
            recipes.map { recipe ->
                if (recipe.id == recipeId) {
                    recipe.copy(
                        lastTest = AutomationRunSummary(
                            status = result.status,
                            message = result.message,
                            logs = result.logs,
                            timestampMillis = result.timestampMillis,
                        ),
                        lastTestRevision = revision,
                    )
                } else {
                    recipe
                }
            }
        }
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
        return (focused + AutomationCatalog.defaultRecipes(actionsById)).distinctBy(AutomationRecipe::id)
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
                    recipe.lastRun?.let { run -> put("lastRun", run.toJson()) }
                    recipe.lastTest?.let { test -> put("lastTest", test.toJson()) }
                    recipe.lastTestRevision?.let { revision -> put("lastTestRevision", revision) }
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
        val array = if (trimmed.startsWith("{")) {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", 1) > RECIPES_SCHEMA_VERSION) return@runCatching null
            root.optJSONArray("items") ?: JSONArray()
        } else {
            JSONArray(raw)
        }
        buildList<AutomationRecipe> {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val steps = item.getJSONArray("steps")
                add(
                    AutomationRecipe(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        description = item.optString("description"),
                        enabled = item.optBoolean("enabled", true),
                        trigger = item.trigger(),
                        steps = buildList<ActionSpec> {
                            repeat(steps.length()) { stepIndex ->
                                steps.getJSONObject(stepIndex).toActionSpec()?.let(::add)
                            }
                        },
                        safety = AutomationSafety(item.optBoolean("requiresConfirmation", false)),
                        lastRun = item.optJSONObject("lastRun")?.let { run ->
                            run.toAutomationRunSummary()
                        },
                        lastTest = item.optJSONObject("lastTest")?.toAutomationRunSummary(),
                        lastTestRevision = item.optString("lastTestRevision").takeIf(String::isNotBlank),
                        pendingApproval = item.optJSONObject("pendingApproval")?.toAutomationRunSummary(),
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
                )
            }
        }
    }.getOrNull()

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

    private companion object {
        val RECIPES = stringPreferencesKey("recipes")
        val RECIPES_QUARANTINE = stringPreferencesKey("recipes_quarantine")
        const val MAX_RUN_HISTORY = 10
    }

    private fun reportRecipeDecodeFailure(raw: String) {
        Log.w(TAG, "Automation recipe decode failed; preserving raw value for recovery (${raw.length} chars)")
    }
}

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
