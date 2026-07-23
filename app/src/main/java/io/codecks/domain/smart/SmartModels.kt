package io.codecks.domain.smart

enum class SmartCapability {
    LocalNavigation,
    MacCommand,
    ConnectionRepair,
    Keyboard,
    Clipboard,
    RuleDraft,
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
    Run,
    Pin,
    Hide,
    Why,
    NeverForApp,
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
    val currentSurface: String,
    val selectedMacId: String?,
    val macConnected: Boolean,
    val activeMacApp: String?,
    val recentActionIds: List<String>,
    val notificationSourceKeys: List<String>,
    val coarsePhoneContext: String?,
    val supportedCapabilities: Set<SmartCapability>,
    val hourBucket: Int,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis > expiresAtMillis

    fun sanitizedKeys(): Set<String> = buildSet {
        add("surface:${currentSurface.sanitizeSmartKey()}")
        selectedMacId?.sanitizeSmartKey()?.takeIf(String::isNotBlank)?.let { add("mac:$it") }
        activeMacApp?.sanitizeSmartKey()?.takeIf(String::isNotBlank)?.let { add("macApp:$it") }
        coarsePhoneContext?.sanitizeSmartKey()?.takeIf(String::isNotBlank)?.let { add("phone:$it") }
        notificationSourceKeys.map(String::sanitizeSmartKey).filter(String::isNotBlank).take(4).forEach { add("notification:$it") }
        add("hour:$hourBucket")
    }
}

data class SmartActionRef(
    val id: String,
    val title: String,
    val description: String,
    val commandType: String,
    val route: String?,
    val requiresMac: Boolean,
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
    val appKey: String?,
    val type: SmartFeedbackType,
    val success: Boolean?,
    val coarseHourBucket: Int,
    val contextKeys: Set<String>,
    val atMillis: Long,
)

data class SmartFeedbackSummary(
    val hiddenCandidateIds: Set<String> = emptySet(),
    val neverAppActionKeys: Set<String> = emptySet(),
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
