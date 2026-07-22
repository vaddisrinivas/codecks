package io.codecks.domain.features

import io.codecks.domain.ai.FeatureGate
import kotlinx.coroutines.flow.Flow

interface EntitlementRepository {
    val entitlement: Flow<Entitlement>
    suspend fun currentEntitlement(): Entitlement
    suspend fun refresh(): Result<Entitlement>
}

interface FeatureFlagRepository {
    val flags: Flow<Map<FeatureFlag, Boolean>>
    suspend fun isEnabled(flag: FeatureFlag): Boolean
    suspend fun resetDefaults()
}

data class Entitlement(
    val localOnly: Boolean = true,
) {
    fun allows(gate: FeatureGate): Boolean = localOnly && gate == FeatureGate.AiBuilder
}

enum class FeatureFlag {
    Deck,
    Trackpad,
    Automations,
    Ai,
    Keyboard,
    Clipboard,
    Settings,
    DeckEditor,
    Connection,
    AiBuilder,
    SmartSuggestions,
    SmartDeck,
    SmartKeyboard,
    SmartClipboard,
    SmartRules,
    SmartSettings,
    SmartTrackpadSuggest,
    SmartTrackpadSnap,
    SmartOcr,
    Labs,
    LabAirMouse,
    LabAirTouch,
    LabBackTap,
    LabVolumeKeys,
}
