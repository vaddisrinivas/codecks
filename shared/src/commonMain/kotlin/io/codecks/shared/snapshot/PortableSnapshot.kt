package io.codecks.shared.snapshot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val PORTABLE_SNAPSHOT_SCHEMA_V1 = "codecks.portable_snapshot.v1"
const val PORTABLE_SNAPSHOT_MAX_BYTES = 256 * 1024
const val PORTABLE_SNAPSHOT_MAX_DECKS = 32
const val PORTABLE_SNAPSHOT_MAX_SLOTS_PER_DECK = 64
const val PORTABLE_SNAPSHOT_MAX_CATALOG_ITEMS = 256
const val PORTABLE_SNAPSHOT_MAX_ROUTINE_FAVORITES = 256
const val PORTABLE_SNAPSHOT_MAX_AUTOMATIONS = 128
const val PORTABLE_SNAPSHOT_MAX_AUTOMATION_STEPS = 32
const val PORTABLE_SNAPSHOT_MAX_LABEL_CHARS = 96
const val PORTABLE_SNAPSHOT_MAX_ID_CHARS = 96

/**
 * Closed classification for every field considered by the V1 cloud exporter.
 * New persisted fields must add an enum value and therefore choose a policy.
 */
enum class PersistedSnapshotField(val cloudPolicy: SnapshotCloudPolicy) {
    Schema(SnapshotCloudPolicy.CLOUD_ALLOWED),
    SourceAppVersion(SnapshotCloudPolicy.CLOUD_ALLOWED),
    DeckLayout(SnapshotCloudPolicy.CLOUD_ALLOWED),
    DeckSlotLabel(SnapshotCloudPolicy.CLOUD_ALLOWED),
    DeckSlotIcon(SnapshotCloudPolicy.CLOUD_ALLOWED),
    DeckSlotColor(SnapshotCloudPolicy.CLOUD_ALLOWED),
    CatalogActionId(SnapshotCloudPolicy.CLOUD_ALLOWED),
    LocalRouteId(SnapshotCloudPolicy.CLOUD_ALLOWED),
    ThemePreference(SnapshotCloudPolicy.CLOUD_ALLOWED),
    RoutineFavorite(SnapshotCloudPolicy.CLOUD_ALLOWED),
    AutomationDefinition(SnapshotCloudPolicy.CLOUD_ALLOWED),
    DisplayLabel(SnapshotCloudPolicy.CLOUD_ALLOWED),
    RawCommand(SnapshotCloudPolicy.LOCAL_ONLY),
    TestCommand(SnapshotCloudPolicy.LOCAL_ONLY),
    CleanupCommand(SnapshotCloudPolicy.LOCAL_ONLY),
    ExecutionProof(SnapshotCloudPolicy.LOCAL_ONLY),
    ExecutionHistory(SnapshotCloudPolicy.LOCAL_ONLY),
    SshHost(SnapshotCloudPolicy.LOCAL_ONLY),
    SshIpAddress(SnapshotCloudPolicy.LOCAL_ONLY),
    SshUsername(SnapshotCloudPolicy.LOCAL_ONLY),
    SshPrivateKey(SnapshotCloudPolicy.LOCAL_ONLY),
    SshPassword(SnapshotCloudPolicy.LOCAL_ONLY),
    AiCredential(SnapshotCloudPolicy.LOCAL_ONLY),
    ClipboardContent(SnapshotCloudPolicy.LOCAL_ONLY),
    DeviceIdentifier(SnapshotCloudPolicy.LOCAL_ONLY),
    DiagnosticPayload(SnapshotCloudPolicy.LOCAL_ONLY),
}

enum class SnapshotCloudPolicy {
    CLOUD_ALLOWED,
    LOCAL_ONLY,
}

@Serializable
data class PortableSnapshotV1(
    val schema: String = PORTABLE_SNAPSHOT_SCHEMA_V1,
    val sourceAppVersion: String,
    val decks: List<PortableDeck> = emptyList(),
    val catalog: List<PortableCatalogItem> = emptyList(),
    val theme: PortableThemePreferences = PortableThemePreferences(),
    val routineFavorites: List<String> = emptyList(),
    val automations: List<PortableAutomationDefinition> = emptyList(),
    val displayLabels: List<PortableDisplayLabel> = emptyList(),
)

@Serializable
data class PortableDeck(
    val id: String,
    val label: String,
    val rows: Int,
    val columns: Int,
    val slots: List<PortableDeckSlot> = emptyList(),
)

@Serializable
data class PortableDeckSlot(
    val row: Int,
    val column: Int,
    val label: String,
    val icon: PortableIcon,
    val colorHex: String,
    val action: PortableActionReference,
)

@Serializable
data class PortableCatalogItem(
    val id: String,
    val label: String,
    val icon: PortableIcon,
    val colorHex: String,
    val action: PortableActionReference,
)

@Serializable
sealed interface PortableActionReference {
    @Serializable
    @SerialName("catalog")
    data class Catalog(val id: String) : PortableActionReference

    @Serializable
    @SerialName("local_route")
    data class LocalRoute(val id: String) : PortableActionReference
}

@Serializable
enum class PortableIcon {
    @SerialName("pointer") Pointer,
    @SerialName("keyboard") Keyboard,
    @SerialName("clipboard") Clipboard,
    @SerialName("browser") Browser,
    @SerialName("terminal") Terminal,
    @SerialName("finder") Finder,
    @SerialName("media") Media,
    @SerialName("automation") Automation,
    @SerialName("settings") Settings,
    @SerialName("star") Star,
}

@Serializable
data class PortableThemePreferences(
    val mode: PortableThemeMode = PortableThemeMode.System,
    val deckStyle: PortableDeckStyle = PortableDeckStyle.StreamDeckPro,
    val accentColorHex: String? = null,
    val motionEnabled: Boolean = true,
    val highContrast: Boolean = false,
)

@Serializable
enum class PortableThemeMode {
    @SerialName("system") System,
    @SerialName("light") Light,
    @SerialName("dark") Dark,
    @SerialName("oled") Oled,
}

@Serializable
enum class PortableDeckStyle {
    @SerialName("aurora_glass") AuroraGlass,
    @SerialName("candy_pop") CandyPop,
    @SerialName("arcade_neon") ArcadeNeon,
    @SerialName("one_ui_grid") OneUiGrid,
    @SerialName("stream_deck_pro") StreamDeckPro,
    @SerialName("nothing_mono") NothingMono,
    @SerialName("codex_micro_glass") CodexMicroGlass,
}

@Serializable
data class PortableAutomationDefinition(
    val id: String,
    val label: String,
    val trigger: PortableAutomationTrigger,
    val steps: List<PortableAutomationStep>,
    /** Restore is always review-first. V1 rejects encoded enabled automations. */
    val enabled: Boolean = false,
)

@Serializable
enum class PortableAutomationTrigger {
    @SerialName("manual") Manual,
    @SerialName("routine_selected") RoutineSelected,
    @SerialName("app_context") AppContext,
}

@Serializable
data class PortableAutomationStep(
    val order: Int,
    val action: PortableActionReference,
)

@Serializable
data class PortableDisplayLabel(
    val key: PortableDisplayLabelKey,
    val value: String,
)

@Serializable
enum class PortableDisplayLabelKey {
    @SerialName("workspace") Workspace,
    @SerialName("deck_collection") DeckCollection,
    @SerialName("routine_collection") RoutineCollection,
}

class PortableSnapshotAllowlist(
    catalogActionIds: Set<String>,
    localRouteIds: Set<String>,
    routineIds: Set<String>,
) {
    val catalogActionIds: Set<String> = catalogActionIds.toSet()
    val localRouteIds: Set<String> = localRouteIds.toSet()
    val routineIds: Set<String> = routineIds.toSet()

    init {
        require(catalogActionIds.all(::isSafeId)) { "Catalog allowlist contains an invalid ID" }
        require(localRouteIds.all(::isSafeId)) { "Local-route allowlist contains an invalid ID" }
        require(routineIds.all(::isSafeId)) { "Routine allowlist contains an invalid ID" }
    }
}

class PortableRestoreCandidate private constructor(
    val snapshot: PortableSnapshotV1,
) {
    init {
        require(snapshot.automations.none(PortableAutomationDefinition::enabled)) {
            "Restore candidates cannot contain enabled automations"
        }
    }

    companion object {
        internal fun validated(snapshot: PortableSnapshotV1): PortableRestoreCandidate =
            PortableRestoreCandidate(snapshot)
    }
}

sealed interface PortableSnapshotDecodeResult {
    data class Accepted(val candidate: PortableRestoreCandidate) : PortableSnapshotDecodeResult
    data class Rejected(
        val reason: SnapshotRejectionReason,
        val safeMessage: String,
    ) : PortableSnapshotDecodeResult
}

enum class SnapshotRejectionReason {
    TooLarge,
    MalformedOrUnsupportedValue,
    UnsupportedSchema,
    InvalidContent,
}

class PortableSnapshotCodec(
    private val allowlist: PortableSnapshotAllowlist,
) {
    private val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(snapshot: PortableSnapshotV1): String {
        val canonical = snapshot.canonical()
        validateSnapshot(canonical, allowlist)
        val encoded = json.encodeToString(PortableSnapshotV1.serializer(), canonical)
        require(encoded.encodeToByteArray().size <= PORTABLE_SNAPSHOT_MAX_BYTES) {
            "Portable snapshot exceeds byte limit"
        }
        return encoded
    }

    fun decode(encoded: String): PortableSnapshotDecodeResult {
        if (
            encoded.length > PORTABLE_SNAPSHOT_MAX_BYTES ||
            encoded.encodeToByteArray().size > PORTABLE_SNAPSHOT_MAX_BYTES
        ) {
            return PortableSnapshotDecodeResult.Rejected(
                SnapshotRejectionReason.TooLarge,
                "Snapshot is larger than the supported limit.",
            )
        }
        val decoded = try {
            json.decodeFromString(PortableSnapshotV1.serializer(), encoded)
        } catch (_: SerializationException) {
            return PortableSnapshotDecodeResult.Rejected(
                SnapshotRejectionReason.MalformedOrUnsupportedValue,
                "Snapshot contains malformed or unsupported data.",
            )
        } catch (_: IllegalArgumentException) {
            return PortableSnapshotDecodeResult.Rejected(
                SnapshotRejectionReason.MalformedOrUnsupportedValue,
                "Snapshot contains malformed or unsupported data.",
            )
        }
        if (decoded.schema != PORTABLE_SNAPSHOT_SCHEMA_V1) {
            return PortableSnapshotDecodeResult.Rejected(
                SnapshotRejectionReason.UnsupportedSchema,
                "Snapshot version is not supported by this app.",
            )
        }
        return try {
            validateSnapshot(decoded, allowlist)
            PortableSnapshotDecodeResult.Accepted(PortableRestoreCandidate.validated(decoded.canonical()))
        } catch (_: IllegalArgumentException) {
            PortableSnapshotDecodeResult.Rejected(
                SnapshotRejectionReason.InvalidContent,
                "Snapshot failed safety validation.",
            )
        }
    }
}

/** Non-serializable input boundary. Unsafe candidates are reduced to safe omissions. */
sealed interface PersistedActionCandidate {
    data class Catalog(val id: String) : PersistedActionCandidate
    data class LocalRoute(val id: String) : PersistedActionCandidate
    class RawCommand(@Suppress("unused") private val command: String) : PersistedActionCandidate
    class TestCommand(@Suppress("unused") private val command: String) : PersistedActionCandidate
    class CleanupCommand(@Suppress("unused") private val command: String) : PersistedActionCandidate
}

sealed interface SnapshotActionProjection {
    data class Allowed(val action: PortableActionReference) : SnapshotActionProjection
    data class Omitted(val omission: SnapshotOmission) : SnapshotActionProjection
}

data class SnapshotOmission(
    val path: String,
    val field: PersistedSnapshotField,
    val code: SnapshotOmissionCode,
    val safeMessage: String,
)

enum class SnapshotOmissionCode {
    LocalOnly,
    NotAllowlisted,
}

class PortableActionProjector(
    private val allowlist: PortableSnapshotAllowlist,
) {
    fun project(candidate: PersistedActionCandidate, path: String): SnapshotActionProjection = when (candidate) {
        is PersistedActionCandidate.Catalog -> if (candidate.id in allowlist.catalogActionIds) {
            SnapshotActionProjection.Allowed(PortableActionReference.Catalog(candidate.id))
        } else {
            notAllowlisted(path, PersistedSnapshotField.CatalogActionId)
        }
        is PersistedActionCandidate.LocalRoute -> if (candidate.id in allowlist.localRouteIds) {
            SnapshotActionProjection.Allowed(PortableActionReference.LocalRoute(candidate.id))
        } else {
            notAllowlisted(path, PersistedSnapshotField.LocalRouteId)
        }
        is PersistedActionCandidate.RawCommand -> localOnly(path, PersistedSnapshotField.RawCommand)
        is PersistedActionCandidate.TestCommand -> localOnly(path, PersistedSnapshotField.TestCommand)
        is PersistedActionCandidate.CleanupCommand -> localOnly(path, PersistedSnapshotField.CleanupCommand)
    }

    private fun localOnly(path: String, field: PersistedSnapshotField) =
        SnapshotActionProjection.Omitted(
            SnapshotOmission(
                path = safePath(path),
                field = field,
                code = SnapshotOmissionCode.LocalOnly,
                safeMessage = "This local-only action was omitted from cloud backup.",
            ),
        )

    private fun notAllowlisted(path: String, field: PersistedSnapshotField) =
        SnapshotActionProjection.Omitted(
            SnapshotOmission(
                path = safePath(path),
                field = field,
                code = SnapshotOmissionCode.NotAllowlisted,
                safeMessage = "This action is not in the portable allowlist and was omitted.",
            ),
        )
}

private fun validateSnapshot(snapshot: PortableSnapshotV1, allowlist: PortableSnapshotAllowlist) {
    require(snapshot.schema == PORTABLE_SNAPSHOT_SCHEMA_V1) { "Unsupported snapshot schema" }
    requireSafeLabel(snapshot.sourceAppVersion, "source app version")
    require(snapshot.decks.size <= PORTABLE_SNAPSHOT_MAX_DECKS) { "Too many decks" }
    require(snapshot.catalog.size <= PORTABLE_SNAPSHOT_MAX_CATALOG_ITEMS) { "Too many catalog items" }
    require(snapshot.routineFavorites.size <= PORTABLE_SNAPSHOT_MAX_ROUTINE_FAVORITES) { "Too many routine favorites" }
    require(snapshot.automations.size <= PORTABLE_SNAPSHOT_MAX_AUTOMATIONS) { "Too many automations" }
    requireUnique(snapshot.decks.map(PortableDeck::id), "deck IDs")
    requireUnique(snapshot.catalog.map(PortableCatalogItem::id), "catalog IDs")
    requireUnique(snapshot.routineFavorites, "routine favorites")
    requireUnique(snapshot.automations.map(PortableAutomationDefinition::id), "automation IDs")
    requireUnique(snapshot.displayLabels.map { it.key.name }, "display label keys")

    snapshot.decks.forEach { deck ->
        requireSafeId(deck.id, "deck ID")
        requireSafeLabel(deck.label, "deck label")
        require(deck.rows in 1..8 && deck.columns in 1..8) { "Invalid deck dimensions" }
        require(deck.slots.size <= PORTABLE_SNAPSHOT_MAX_SLOTS_PER_DECK) { "Too many deck slots" }
        requireUnique(deck.slots.map { "${it.row}:${it.column}" }, "deck slot positions")
        deck.slots.forEach { slot ->
            require(slot.row in 0 until deck.rows && slot.column in 0 until deck.columns) { "Slot is outside deck bounds" }
            requireSafeLabel(slot.label, "slot label")
            requireColor(slot.colorHex)
            validateAction(slot.action, allowlist)
        }
    }
    snapshot.catalog.forEach { item ->
        requireSafeId(item.id, "catalog item ID")
        requireSafeLabel(item.label, "catalog item label")
        requireColor(item.colorHex)
        validateAction(item.action, allowlist)
    }
    snapshot.theme.accentColorHex?.let(::requireColor)
    snapshot.routineFavorites.forEach { routineId ->
        require(routineId in allowlist.routineIds) { "Routine is not allowlisted" }
    }
    snapshot.automations.forEach { automation ->
        requireSafeId(automation.id, "automation ID")
        requireSafeLabel(automation.label, "automation label")
        require(!automation.enabled) { "Imported automation must be disabled" }
        require(automation.steps.isNotEmpty()) { "Automation must contain a step" }
        require(automation.steps.size <= PORTABLE_SNAPSHOT_MAX_AUTOMATION_STEPS) { "Too many automation steps" }
        requireUnique(automation.steps.map { it.order.toString() }, "automation step order")
        require(automation.steps.map(PortableAutomationStep::order).sorted() == automation.steps.indices.toList()) {
            "Automation steps must use contiguous zero-based order"
        }
        automation.steps.forEach { validateAction(it.action, allowlist) }
    }
    snapshot.displayLabels.forEach { requireSafeLabel(it.value, "display label") }
}

private fun PortableSnapshotV1.canonical(): PortableSnapshotV1 = copy(
    decks = decks.sortedBy(PortableDeck::id).map { deck ->
        deck.copy(slots = deck.slots.sortedWith(compareBy(PortableDeckSlot::row, PortableDeckSlot::column)))
    },
    catalog = catalog.sortedBy(PortableCatalogItem::id),
    routineFavorites = routineFavorites.sorted(),
    automations = automations.sortedBy(PortableAutomationDefinition::id).map { automation ->
        automation.copy(steps = automation.steps.sortedBy(PortableAutomationStep::order), enabled = false)
    },
    displayLabels = displayLabels.sortedBy { it.key.name },
)

private fun validateAction(action: PortableActionReference, allowlist: PortableSnapshotAllowlist) {
    when (action) {
        is PortableActionReference.Catalog -> require(action.id in allowlist.catalogActionIds) {
            "Catalog action is not allowlisted"
        }
        is PortableActionReference.LocalRoute -> require(action.id in allowlist.localRouteIds) {
            "Local route is not allowlisted"
        }
    }
}

private fun requireColor(color: String) {
    require(COLOR_REGEX.matches(color)) { "Color must use canonical #RRGGBB form" }
}

private fun requireSafeId(value: String, label: String) {
    require(isSafeId(value)) { "$label is invalid" }
}

private fun isSafeId(value: String): Boolean =
    value.length in 1..PORTABLE_SNAPSHOT_MAX_ID_CHARS &&
        SAFE_ID_REGEX.matches(value) &&
        !looksLikeCredential(value)

private fun requireSafeLabel(value: String, label: String) {
    require(value.isNotBlank() && value.length <= PORTABLE_SNAPSHOT_MAX_LABEL_CHARS) { "$label is invalid" }
    require(value.none { it.code < 0x20 || it == '\u007f' }) { "$label contains control characters" }
    require(!looksLikeCredential(value)) { "$label resembles credential material" }
}

private fun requireUnique(values: List<String>, label: String) {
    require(values.toSet().size == values.size) { "$label must be unique" }
}

private fun safePath(path: String): String =
    path.takeIf {
        it.length in 1..PORTABLE_SNAPSHOT_MAX_LABEL_CHARS &&
            SAFE_PATH_REGEX.matches(it) &&
            !looksLikeCredential(it)
    }
        ?: "portable-item"

private val SAFE_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")
private val SAFE_PATH_REGEX = Regex("[A-Za-z0-9._:/-]+")
private val COLOR_REGEX = Regex("#[0-9A-F]{6}")
private val CREDENTIAL_ASSIGNMENT_REGEX = Regex(
    "(?i)(api[_-]?key|access[_-]?token|auth[_-]?token|password|private[_-]?key|secret)\\s*[:=]\\s*\\S{6,}",
)
private val PROVIDER_TOKEN_REGEX = Regex("(?i)\\b(sk|rk|pat)-[A-Za-z0-9_-]{16,}\\b")

private fun looksLikeCredential(value: String): Boolean =
    "-----BEGIN " in value ||
        value.startsWith("ssh-rsa ") ||
        value.startsWith("ssh-ed25519 ") ||
        CREDENTIAL_ASSIGNMENT_REGEX.containsMatchIn(value) ||
        PROVIDER_TOKEN_REGEX.containsMatchIn(value)
