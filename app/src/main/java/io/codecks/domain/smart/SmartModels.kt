package io.codecks.domain.smart

enum class SmartCapability {
    LocalNavigation,
    MacCommand,
    MacInput,
    ConnectionRepair,
    Keyboard,
    Clipboard,
    RuleDraft,
}

enum class SmartSurface {
    Deck,
    Trackpad,
    Keyboard,
    Clipboard,
    Rules,
    AiBuilder,
    Settings,
}

enum class SmartActionKind {
    LocalNavigation,
    MacCommand,
    MacInput,
}

enum class SmartPhoneContext {
    Phone,
    Desktop,
}

@JvmInline
value class SmartAppKey(val value: String) {
    init {
        require(value.isNotBlank()) { "SmartAppKey cannot be blank" }
        require(value.length <= 80) { "SmartAppKey is too long" }
    }
}

@JvmInline
value class SmartMacId(val value: String) {
    init {
        require(value.isNotBlank()) { "SmartMacId cannot be blank" }
        require(value.length <= 80) { "SmartMacId is too long" }
    }
}

enum class SmartRisk {
    Normal,
    RequiresReview,
    Dangerous,
    PrivacySensitive,
    Unsupported,
}

enum class SmartConfidenceLabel {
    VeryLikely,
    Likely,
    Possible,
}

enum class SmartFeedbackType {
    Pin,
    Why,
    SuppressHere,
    NeverGlobal,
    Success,
    Failure,
}

sealed interface SmartUnavailable {
    data object FeatureOff : SmartUnavailable
    data object NoCandidates : SmartUnavailable
    data object Expired : SmartUnavailable
    data class MissingCapability(val capability: SmartCapability) : SmartUnavailable
    data class SuppressedByPolicy(val reason: String) : SmartUnavailable
}

data class SmartContext(
    val currentSurface: SmartSurface,
    val selectedMacId: SmartMacId?,
    val macConnected: Boolean,
    val macInputConnected: Boolean,
    val activeMacApp: SmartAppKey?,
    val recentActionIds: List<String>,
    val supportedCapabilities: Set<SmartCapability>,
    val hourBucket: Int,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val phoneContext: SmartPhoneContext,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis > expiresAtMillis

    fun sanitizedKeys(): Set<String> = buildSet {
        add("surface:${currentSurface.name.lowercase()}")
        selectedMacId?.value?.sanitizeSmartKey()?.takeIf(String::isNotBlank)?.let { add("mac:$it") }
        activeMacApp?.value?.sanitizeSmartKey()?.takeIf(String::isNotBlank)?.let { add("macApp:$it") }
        add("phone:${phoneContext.name.lowercase()}")
        add("hour:$hourBucket")
    }
}

data class SmartActionRef(
    val id: String,
    val title: String,
    val description: String,
    val kind: SmartActionKind,
    val route: String?,
    val requiredCapabilities: Set<SmartCapability>,
    val dangerous: Boolean,
)

data class SmartCandidate(
    val id: String,
    val actionId: String?,
    val title: String,
    val summary: String,
    val reason: String,
    val confidenceLabel: SmartConfidenceLabel,
    val capabilities: Set<SmartCapability>,
    val risks: Set<SmartRisk>,
    val contextKeys: Set<String>,
    val expiresAtMillis: Long,
)

data class SmartFeedback(
    val candidateId: String,
    val actionId: String?,
    val appKey: SmartAppKey?,
    val surface: SmartSurface,
    val macId: SmartMacId?,
    val type: SmartFeedbackType,
    val success: Boolean?,
    val coarseHourBucket: Int,
    val contextKeys: Set<String>,
    val atMillis: Long,
)

data class SmartAppActionMapping(
    val appTokens: Set<String>,
    val actionTokens: Set<String>,
    val reason: String,
    val score: Int,
)

data class SmartFeedbackSummary(
    val suppressedContextActionKeys: Set<String> = emptySet(),
    val globallySuppressedActionIds: Set<String> = emptySet(),
    val actionScores: Map<String, Int> = emptyMap(),
    val transitionScores: Map<String, Int> = emptyMap(),
)

data class SmartDecision(
    val candidates: List<SmartCandidate>,
    val unavailable: SmartUnavailable? = null,
)

interface SmartPolicy {
    fun filter(context: SmartContext, candidates: List<SmartCandidate>, nowMillis: Long): SmartDecision
}

interface SmartEngine {
    fun suggest(
        context: SmartContext,
        actions: List<SmartActionRef>,
        feedback: SmartFeedbackSummary = SmartFeedbackSummary(),
        nowMillis: Long,
    ): SmartDecision
}

fun String.sanitizeSmartKey(): String =
    lowercase()
        .replace(Regex("[^a-z0-9._:-]+"), "_")
        .trim('_')
        .take(80)

fun smartCandidateId(
    surface: SmartSurface,
    appKey: SmartAppKey?,
    actionId: String,
): String = buildString {
    append("smart:")
    append(surface.name.lowercase())
    append(":")
    append((appKey?.value ?: "any").sanitizeSmartKey().ifBlank { "any" })
    append(":")
    append(actionId)
}

fun smartTransitionKey(
    surface: SmartSurface,
    appKey: SmartAppKey?,
    macId: SmartMacId?,
    previousActionId: String,
    nextActionId: String,
): String = buildString {
    append(surface.name.lowercase())
    append(":")
    append((appKey?.value ?: "any").sanitizeSmartKey().ifBlank { "any" })
    append(":")
    append((macId?.value ?: "any").sanitizeSmartKey().ifBlank { "any" })
    append(":")
    append(previousActionId)
    append("->")
    append(nextActionId)
}
