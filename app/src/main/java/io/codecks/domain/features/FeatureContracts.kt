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
    val tier: EntitlementTier,
    val status: EntitlementStatus,
    val offlineGrace: OfflineGrace = OfflineGrace.None,
) {
    fun allows(gate: FeatureGate): Boolean =
        status in setOf(EntitlementStatus.Active, EntitlementStatus.Trialing) &&
            tier == EntitlementTier.Premium &&
            gate == FeatureGate.AiBuilder
}

enum class EntitlementTier {
    Free,
    Premium,
}

enum class EntitlementStatus {
    Free,
    Trialing,
    Active,
    Expired,
    Refunded,
    OfflineGrace,
}

sealed interface OfflineGrace {
    data object None : OfflineGrace
    data class Active(val expiresAtEpochMillis: Long) : OfflineGrace
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
    ContextDeck,
    Labs,
    LabAirMouse,
    LabAirTouch,
    LabBackTap,
    LabVolumeKeys,
}
