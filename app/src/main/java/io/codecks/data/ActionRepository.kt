package io.codecks.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.DeckAction
import io.codecks.domain.ExecutionAuthorization
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.deck.DeckSlot
import io.codecks.domain.deck.DeckTemplate
import io.codecks.domain.deck.DeckTemplateCatalog
import io.codecks.domain.device.DeviceGroupId
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private val Context.deckDataStore by preferencesDataStore(name = "deck")

interface ActionRepository {
    fun favorites(): List<DeckAction>
    fun observeFavorites(): Flow<List<DeckAction>>
    fun layout(): DeckLayout = DeckLayout.fromActions(favorites())
    fun observeLayout(): Flow<DeckLayout> = observeFavorites().map { DeckLayout.fromActions(it) }
    fun catalogActions(): List<DeckAction> = allActions()
    suspend fun customActions(): List<DeckAction> = emptyList()
    fun allActions(): List<DeckAction>
    fun observeAllActions(): Flow<List<DeckAction>> = observeFavorites()
    fun deckTemplates(): List<DeckTemplate> = DeckTemplateCatalog.templates
    fun actionsForTemplate(templateId: String): List<DeckAction> = emptyList()
    fun templateForActiveApp(activeApp: String): DeckTemplate? = DeckTemplateCatalog.matchActiveApp(activeApp)
    suspend fun saveFavorites(actions: List<DeckAction>)
    suspend fun saveLayout(layout: DeckLayout) = saveFavorites(layout.actions)
    suspend fun exportLayout(): Result<String>
    suspend fun validateLayout(payload: String): Result<Unit>
    suspend fun importLayout(payload: String): Result<Unit>
    suspend fun run(action: DeckAction): Result<String>
    suspend fun test(action: DeckAction): Result<String>
}

@Singleton
class DefaultActionRepository @Inject constructor(
    actionCatalog: ActionCatalog,
    @param:ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository,
) : ActionRepository {
    private val actions = actionCatalog.load()
    private val byId = actions.associateBy(DeckAction::id)
    private val defaultLayout: DeckLayout by lazy {
        DeckLayout(
            columns = DeckLayout.DEFAULT_COLUMNS,
            slots = DEFAULT_SLOT_SPECS.mapIndexedNotNull { index, spec ->
                byId[spec.actionId]?.let { action ->
                    DeckSlot(id = "slot-${index + 1}", action = action, columnSpan = spec.columnSpan)
                }
            },
        ).normalized()
    }
    @Volatile
    private var cachedLayout: DeckLayout? = null

    override fun favorites(): List<DeckAction> = layout().actions

    override fun layout(): DeckLayout = cachedLayout ?: defaultLayout

    override fun observeFavorites(): Flow<List<DeckAction>> =
        observeLayout().map { it.actions }

    override fun observeLayout(): Flow<DeckLayout> =
        context.deckDataStore.data
            .onStart { migratePersistedTargetSelectors() }
            .map { preferences ->
                val storedLayout = preferences[FAVORITES]
                    ?.let { raw ->
                        decodeLayout(raw) ?: run {
                            reportFavoriteDecodeFailure(raw)
                            null
                        }
                    }
                    ?.takeIf { it.slots.isNotEmpty() }
                    ?: defaultLayout
                upgradeLayout(storedLayout).also { cachedLayout = it }
            }

    override fun catalogActions(): List<DeckAction> = actions

    override suspend fun customActions(): List<DeckAction> {
        migratePersistedTargetSelectors()
        return context.deckDataStore.data.first()[FAVORITES]
            ?.let(::decodeLayout)
            ?.let(::upgradeLayout)
            ?.also { cachedLayout = it }
            ?.actions
            ?.filterNot { action -> byId.containsKey(action.id) }
            .orEmpty()
    }

    override fun allActions(): List<DeckAction> =
        (actions + layout().actions.filterNot { byId.containsKey(it.id) }).distinctBy(DeckAction::id)

    override fun observeAllActions(): Flow<List<DeckAction>> =
        observeLayout().map { layout -> (actions + layout.actions.filterNot { byId.containsKey(it.id) }).distinctBy(DeckAction::id) }

    override fun deckTemplates(): List<DeckTemplate> = DeckTemplateCatalog.templates

    override fun actionsForTemplate(templateId: String): List<DeckAction> {
        val template = deckTemplates().firstOrNull { it.id == templateId } ?: return favorites()
        return template.actionIds.mapNotNull(byId::get)
    }

    override fun templateForActiveApp(activeApp: String): DeckTemplate? =
        DeckTemplateCatalog.matchActiveApp(activeApp)

    override suspend fun saveFavorites(actions: List<DeckAction>) {
        saveLayout(DeckLayout.fromActions(actions))
    }

    override suspend fun saveLayout(layout: DeckLayout) {
        val normalized = layout.normalized()
        val encoded = encodeLayout(normalized)
        val payload = when (
            val migration = migrateDeckTargetSelectorPayload(
                raw = encoded,
                legacyIds = connectionRepository.legacyTargetIdMigrations(),
            )
        ) {
            is PersistedTargetSelectorMigration.Migrated -> migration.payload
            PersistedTargetSelectorMigration.Unchanged -> encoded
            PersistedTargetSelectorMigration.Undecodable -> error("Generated Deck layout could not be decoded")
        }
        context.deckDataStore.edit { preferences ->
            preferences[FAVORITES]?.let { raw ->
                if (decodeLayout(raw) == null) {
                    preferences[FAVORITES_QUARANTINE] = quarantinePayload(raw, "favorites")
                }
            }
            preferences[FAVORITES] = payload
        }
        cachedLayout = decodeLayout(payload) ?: normalized
    }

    private fun withNextWaveUtilitySlots(layout: DeckLayout): DeckLayout {
        val ids = layout.actions.map(DeckAction::id)
        if (ids == PREVIOUS_DEFAULT_ACTION_IDS) return defaultLayout
        if ("keyboard" in ids || "clipboard" in ids) return layout
        if (ids != OLD_DEFAULT_ACTION_IDS) return layout
        val nextSlots = layout.slots +
            listOfNotNull(
                byId["keyboard"]?.let { DeckSlot(id = "slot-keyboard", action = it, columnSpan = 2) },
                byId["clipboard"]?.let { DeckSlot(id = "slot-clipboard", action = it, columnSpan = 2) },
            )
        return layout.copy(slots = nextSlots).normalized()
    }

    private fun upgradeLayout(layout: DeckLayout): DeckLayout {
        val ids = layout.actions.map(DeckAction::id)
        if (ids == PREVIOUS_DEFAULT_ACTION_IDS || ids == OLD_DEFAULT_ACTION_IDS) return defaultLayout
        return withNextWaveUtilitySlots(layout.normalized())
    }

    override suspend fun exportLayout(): Result<String> = runCatching {
        migratePersistedTargetSelectors()
        context.deckDataStore.data.first()[FAVORITES]
            ?.takeIf { decodeLayout(it)?.slots?.isNotEmpty() == true }
            ?: encodeLayout(defaultLayout)
    }

    override suspend fun importLayout(payload: String): Result<Unit> = runCatching {
        validateLayout(payload).getOrThrow()
        val layout = requireNotNull(decodeLayout(payload))
        saveLayout(layout)
    }

    override suspend fun validateLayout(payload: String): Result<Unit> = runCatching {
        requireNotNull(decodeLayout(payload)?.takeIf { it.slots.isNotEmpty() }) {
            "Backup contains no valid Deck layout"
        }
        Unit
    }

    override suspend fun run(action: DeckAction): Result<String> = when (action.kind) {
        ActionKind.Local -> Result.failure(LocalActionException(action.route.orEmpty()))
        ActionKind.Ssh -> if (action.command != null) {
            if (isBundled(action)) connectionRepository.runBundledCommand(action.command)
            else connectionRepository.runCommand(action.command)
        } else {
            connectionRepository.runAction(action.id, action.dangerous)
        }
    }

    override suspend fun test(action: DeckAction): Result<String> = when (action.kind) {
        ActionKind.Local -> Result.success("${action.label} opens ${action.route.orEmpty().ifBlank { "app route" }}")
        ActionKind.Ssh -> {
            val testCommand = action.testCommand
            val command = action.command
            if (testCommand != null) {
                if (isBundled(action)) connectionRepository.runBundledCommand(testCommand)
                else connectionRepository.runCommand(testCommand)
            } else if (command != null) {
                connectionRepository.validateCommandSyntax(command)
            } else {
                Result.failure(IllegalStateException("${action.label} has no test command"))
            }
        }
    }

    private fun isBundled(action: DeckAction): Boolean =
        byId[action.id]?.let { bundled -> bundled.command == action.command } == true

    private fun encodeLayout(layout: DeckLayout): String = JSONObject().apply {
        put("schemaVersion", FAVORITES_SCHEMA_VERSION)
        put("columns", layout.columns)
        put("items", JSONArray().apply {
        layout.slots.forEach { slot ->
            val action = slot.action
            put(JSONObject().apply {
                put("slotId", slot.id)
                put("columnSpan", slot.columnSpan)
                put("id", action.id)
                put("label", action.label)
                put("kind", action.kind.name)
                put("icon", action.icon.name)
                put("description", action.description)
                put("route", action.route)
                put("command", action.command)
                put("testCommand", action.testCommand)
                put("dangerous", action.dangerous)
                put("liveSafe", action.liveSafe)
                put("requiresTest", action.requiresTest)
                put("target", action.targetSelector.toJson())
                put("commandOrigin", action.commandOrigin.name)
                put("commandReview", action.commandReview.toJson())
                put("confirmationTitle", action.confirmationTitle)
                put("confirmationBody", action.confirmationBody)
                put("riskReason", action.riskReason)
                put("executionAuthorization", action.executionAuthorization.toJson())
            })
        }
        })
    }.toString()

    private fun decodeLayout(raw: String): DeckLayout? {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            return DeckLayout.fromActions(raw.split(",").mapNotNull(byId::get))
        }
        return runCatching {
            val root = if (trimmed.startsWith("{")) JSONObject(raw) else null
            if (root != null && root.optInt("schemaVersion", 1) > FAVORITES_SCHEMA_VERSION) {
                error("Unsupported Deck schema")
            }
            val array = if (root != null) {
                root.optJSONArray("items") ?: JSONArray()
            } else {
                JSONArray(raw)
            }
            DeckLayout(
                columns = root?.optInt("columns", DeckLayout.DEFAULT_COLUMNS) ?: DeckLayout.DEFAULT_COLUMNS,
                slots = decodeFavoriteItems(array),
            ).normalized()
        }.getOrNull()
    }

    private fun decodeFavoriteItems(array: JSONArray): List<DeckSlot> = buildList {
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            val action = byId[id] ?: DeckAction(
                    id = id,
                    label = item.getString("label"),
                    kind = ActionKind.valueOf(item.getString("kind")),
                    icon = ActionIcon.valueOf(item.getString("icon")),
                    description = item.optString("description"),
                    route = item.optString("route").takeIf(String::isNotBlank),
                    command = item.optString("command").takeIf(String::isNotBlank),
                    testCommand = item.optString("testCommand").takeIf(String::isNotBlank),
                    dangerous = item.optBoolean("dangerous"),
                    liveSafe = item.optBoolean("liveSafe"),
                    requiresTest = item.optBoolean("requiresTest"),
                    targetSelector = item.optJSONObject("target").toTargetSelector(),
                    commandOrigin = item.optCommandOrigin(
                        fallback = if (item.optString("command").isNotBlank()) CommandOrigin.UserAuthored else CommandOrigin.Bundled,
                    ),
                    commandReview = item.optJSONObject("commandReview").toCommandReview(),
                    confirmationTitle = item.optString("confirmationTitle").takeIf(String::isNotBlank),
                    confirmationBody = item.optString("confirmationBody").takeIf(String::isNotBlank),
                    riskReason = item.optString("riskReason").takeIf(String::isNotBlank),
                    executionAuthorization = item.optJSONObject("executionAuthorization").toExecutionAuthorization(),
                )
            add(
                DeckSlot(
                    id = item.optString("slotId").takeIf(String::isNotBlank) ?: "slot-${index + 1}",
                    action = action,
                    columnSpan = item.optInt("columnSpan", 1),
                ),
            )
        }
    }

    private suspend fun migratePersistedTargetSelectors() {
        val legacyIds = connectionRepository.legacyTargetIdMigrations()
        if (legacyIds.isEmpty()) return
        context.deckDataStore.edit { preferences ->
            val raw = preferences[FAVORITES] ?: return@edit
            when (val migration = migrateDeckTargetSelectorPayload(raw, legacyIds)) {
                is PersistedTargetSelectorMigration.Migrated -> {
                    if (decodeLayout(migration.payload) == null) {
                        preferences[FAVORITES_QUARANTINE] = quarantinePayload(raw, "favorites")
                    } else {
                        preferences[FAVORITES] = migration.payload
                    }
                }
                PersistedTargetSelectorMigration.Undecodable -> {
                    preferences[FAVORITES_QUARANTINE] = quarantinePayload(raw, "favorites")
                }
                PersistedTargetSelectorMigration.Unchanged -> Unit
            }
        }
    }

    private companion object {
        private const val TAG = "DeckStorage"
        const val FAVORITES_SCHEMA_VERSION = 4
        val DEFAULT_SLOT_SPECS = listOf(
            DefaultSlotSpec("finder"), DefaultSlotSpec("terminal"), DefaultSlotSpec("spotlight"), DefaultSlotSpec("screenshot"),
            DefaultSlotSpec("mission"), DefaultSlotSpec("space_left"), DefaultSlotSpec("space_right"), DefaultSlotSpec("full_screen"),
            DefaultSlotSpec("confetti"), DefaultSlotSpec("sparkle"), DefaultSlotSpec("new_tab"), DefaultSlotSpec("screensaver"),
            DefaultSlotSpec("mute"), DefaultSlotSpec("vol_down"), DefaultSlotSpec("vol_up"), DefaultSlotSpec("lock_mac"),
            DefaultSlotSpec("keyboard", columnSpan = 2), DefaultSlotSpec("clipboard", columnSpan = 2),
            DefaultSlotSpec("trackpad", columnSpan = 3), DefaultSlotSpec("automations"),
        )
        val OLD_DEFAULT_ACTION_IDS = listOf(
            "finder", "terminal", "spotlight", "screenshot",
            "mission", "space_left", "space_right", "full_screen",
            "prev_app", "next_app", "new_tab", "play_pause",
            "mute", "vol_down", "vol_up", "lock_mac",
            "trackpad", "automations",
        )
        val PREVIOUS_DEFAULT_ACTION_IDS = listOf(
            "finder", "terminal", "spotlight", "screenshot",
            "mission", "space_left", "space_right", "full_screen",
            "prev_app", "next_app", "new_tab", "play_pause",
            "mute", "vol_down", "vol_up", "lock_mac",
            "keyboard", "clipboard", "trackpad", "automations",
        )
        val FAVORITES = stringPreferencesKey("favorite_action_ids")
        val FAVORITES_QUARANTINE = stringPreferencesKey("favorite_action_ids_quarantine")
    }

    private data class DefaultSlotSpec(val actionId: String, val columnSpan: Int = 1)

    private fun reportFavoriteDecodeFailure(raw: String) {
        Log.w(TAG, "Deck favorites decode failed; preserving raw value for recovery (${raw.length} chars)")
    }
}

private fun quarantinePayload(raw: String, store: String): String = JSONObject().apply {
    put("schemaVersion", 1)
    put("store", store)
    put("quarantinedAtMillis", System.currentTimeMillis())
    put("raw", raw)
}.toString()

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

class LocalActionException(val route: String) : IllegalStateException("Open $route")

@Singleton
class ActionCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun load(): List<DeckAction> {
        val json = context.assets.open("codecks_actions.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val id = item.getString("id")
                add(
                    DeckAction(
                        id = id,
                        label = item.getString("label"),
                        kind = if (item.optString("kind") == "ssh") ActionKind.Ssh else ActionKind.Local,
                        icon = iconFor(id),
                        description = item.optString("description"),
                        route = item.optString("route").takeIf(String::isNotBlank),
                        command = item.optString("command").takeIf(String::isNotBlank),
                        testCommand = item.optString("test_command").takeIf(String::isNotBlank),
                        dangerous = item.optBoolean("dangerous"),
                        liveSafe = item.optBoolean("live_safe"),
                        requiresTest = item.optBoolean("requires_test"),
                    ),
                )
            }
        }
    }

    private fun iconFor(id: String): ActionIcon = when (id) {
        "add_button" -> ActionIcon.Add
        "blank", "blank_spacer", "empty_pad", "quiet_tile" -> ActionIcon.Empty
        "confetti", "party", "celebrate" -> ActionIcon.Party
        "sparkle", "magic_blank", "glow" -> ActionIcon.Sparkle
        "emoji_heart", "emoji_fire", "emoji_focus", "emoji_coffee" -> ActionIcon.Emoji
        "finder", "desktop", "downloads", "documents" -> ActionIcon.Finder
        "terminal", "dev_tools" -> ActionIcon.Terminal
        "spotlight", "detect_mac", "list_apps" -> ActionIcon.Search
        "control_center", "menu_bar_focus", "automations", "mission", "space_left",
        "space_right", "show_desktop", "full_screen", "next_app", "prev_app" -> ActionIcon.Control
        "notification_center" -> ActionIcon.Notifications
        "trackpad" -> ActionIcon.Mouse
        "keyboard", "clipboard", "copy", "paste" -> ActionIcon.Keyboard
        "screenshot" -> ActionIcon.Screenshot
        "lock_mac", "sleep_display" -> ActionIcon.Lock
        "play_pause", "next_track", "prev_track" -> ActionIcon.Play
        "vol_up", "vol_down", "mute" -> ActionIcon.Volume
        "github" -> ActionIcon.Github
        "new_tab", "tab_left", "tab_right", "reload", "browser_back", "browser_forward",
        "chatgpt", "gmail", "calendar", "drive", "claude", "browser_deck" -> ActionIcon.Browser
        else -> ActionIcon.Apps
    }
}

private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
