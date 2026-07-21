package io.codecks.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
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

private val Context.deckDataStore by preferencesDataStore(name = "deck")

interface ActionRepository {
    fun favorites(): List<DeckAction>
    fun observeFavorites(): Flow<List<DeckAction>>
    fun layout(): DeckLayout = DeckLayout.fromActions(favorites())
    fun observeLayout(): Flow<DeckLayout> = observeFavorites().map { DeckLayout.fromActions(it) }
    fun allActions(): List<DeckAction>
    fun deckTemplates(): List<DeckTemplate> = DeckTemplateCatalog.templates
    fun actionsForTemplate(templateId: String): List<DeckAction> = emptyList()
    fun templateForActiveApp(activeApp: String): DeckTemplate? = DeckTemplateCatalog.matchActiveApp(activeApp)
    suspend fun saveFavorites(actions: List<DeckAction>)
    suspend fun saveLayout(layout: DeckLayout) = saveFavorites(layout.actions)
    suspend fun exportLayout(): Result<String> = Result.failure(UnsupportedOperationException("Deck export is unavailable"))
    suspend fun validateLayout(payload: String): Result<Unit> = Result.success(Unit)
    suspend fun importLayout(payload: String): Result<Unit> = Result.failure(UnsupportedOperationException("Deck import is unavailable"))
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

    override fun favorites(): List<DeckAction> = defaultLayout.actions

    override fun layout(): DeckLayout = defaultLayout

    override fun observeFavorites(): Flow<List<DeckAction>> =
        observeLayout().map { it.actions }

    override fun observeLayout(): Flow<DeckLayout> =
        context.deckDataStore.data.map { preferences ->
            val storedLayout = preferences[FAVORITES]
                ?.let { raw ->
                    decodeLayout(raw) ?: run {
                        reportFavoriteDecodeFailure(raw)
                        null
                    }
                }
                ?.takeIf { it.slots.isNotEmpty() }
                ?: defaultLayout
            withNextWaveUtilitySlots(storedLayout)
        }

    override fun allActions(): List<DeckAction> = actions

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
        context.deckDataStore.edit { preferences ->
            preferences[FAVORITES]?.let { raw ->
                if (decodeLayout(raw) == null) {
                    preferences[FAVORITES_QUARANTINE] = quarantinePayload(raw, "favorites")
                }
            }
            preferences[FAVORITES] = encodeLayout(normalized)
        }
    }

    private fun withNextWaveUtilitySlots(layout: DeckLayout): DeckLayout {
        val ids = layout.actions.map(DeckAction::id)
        if ("keyboard" in ids || "clipboard" in ids) return layout
        if (ids != OLD_DEFAULT_ACTION_IDS) return layout
        val nextSlots = layout.slots +
            listOfNotNull(
                byId["keyboard"]?.let { DeckSlot(id = "slot-keyboard", action = it, columnSpan = 2) },
                byId["clipboard"]?.let { DeckSlot(id = "slot-clipboard", action = it, columnSpan = 2) },
            )
        return layout.copy(slots = nextSlots).normalized()
    }

    override suspend fun exportLayout(): Result<String> = runCatching {
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
            connectionRepository.runCommand(action.command)
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
                connectionRepository.runCommand(testCommand)
            } else if (command != null) {
                connectionRepository.runCommand(
                    "tmp=\"\${TMPDIR:-/tmp}/deckbridge_test_\$\$.zsh\"; " +
                        "printf %s ${shellQuote(command)} > \"\$tmp\"; " +
                        "zsh -n \"\$tmp\"; status=\$?; rm -f \"\$tmp\"; " +
                        "[ \$status -eq 0 ] && printf ${shellQuote("${action.label} script verified")} || exit \$status",
                )
            } else {
                Result.failure(IllegalStateException("${action.label} has no test command"))
            }
        }
    }

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

    private companion object {
        private const val TAG = "DeckStorage"
        const val FAVORITES_SCHEMA_VERSION = 3
        val DEFAULT_SLOT_SPECS = listOf(
            DefaultSlotSpec("finder"), DefaultSlotSpec("terminal"), DefaultSlotSpec("spotlight"), DefaultSlotSpec("screenshot"),
            DefaultSlotSpec("mission"), DefaultSlotSpec("space_left"), DefaultSlotSpec("space_right"), DefaultSlotSpec("full_screen"),
            DefaultSlotSpec("prev_app"), DefaultSlotSpec("next_app"), DefaultSlotSpec("new_tab"), DefaultSlotSpec("play_pause"),
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
        val json = context.assets.open("deckbridge_actions.json").bufferedReader().use { it.readText() }
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
