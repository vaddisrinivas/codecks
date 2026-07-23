package io.codecks.ui.home.smart

import io.codecks.domain.DeckAction
import io.codecks.domain.smart.SmartActionKind
import io.codecks.domain.smart.SmartActionRef
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.smart.SmartConfidenceLabel
import io.codecks.domain.smart.SmartContext
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartSurface
import io.codecks.domain.smartActionKind
import io.codecks.domain.smartRequiredCapabilities

data class SmartDeckSuggestionUi(
    val candidateId: String,
    val action: DeckAction,
    val reason: String,
    val confidence: String,
)

@JvmInline
value class SmartRunId(val value: Long)

data class SmartRunRequest(
    val id: SmartRunId,
    val suggestion: SmartDeckSuggestionUi,
    val context: SmartContext,
    val allowDangerous: Boolean,
)

data class SmartDeckInputs(
    val smartDeckEnabled: Boolean = false,
    val onHomeRoute: Boolean = false,
    val currentSurface: SmartSurface = SmartSurface.Deck,
    val selectedMacId: SmartMacId? = null,
    val connectionReady: Boolean = false,
    val macInputConnected: Boolean = false,
    val activeMacApp: SmartAppKey? = null,
    val recentActionIds: List<String> = emptyList(),
    val allActions: List<DeckAction> = emptyList(),
    val visibleDeckActions: List<DeckAction> = emptyList(),
) {
    val isEnabledAndVisible: Boolean = smartDeckEnabled && onHomeRoute
}

fun DeckAction.toSmartActionRef(): SmartActionRef = SmartActionRef(
    id = id,
    title = label,
    description = description,
    kind = smartActionKind(),
    route = route,
    requiredCapabilities = smartRequiredCapabilities(),
    dangerous = dangerous,
)

fun SmartConfidenceLabel.productLabel(): String = when (this) {
    SmartConfidenceLabel.VeryLikely -> "Very likely"
    SmartConfidenceLabel.Likely -> "Likely"
    SmartConfidenceLabel.Possible -> "Possible"
}
