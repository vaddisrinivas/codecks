package io.codecks.domain.features

import kotlinx.coroutines.flow.Flow

val DEFAULT_FEATURE_FLAGS = mapOf(
    FeatureFlag.Deck to true,
    FeatureFlag.Trackpad to true,
    FeatureFlag.Automations to true,
    FeatureFlag.Ai to true,
    FeatureFlag.AiBuilder to true,
    FeatureFlag.SmartSuggestions to false,
    FeatureFlag.SmartDeck to false,
    FeatureFlag.SmartKeyboard to false,
    FeatureFlag.SmartClipboard to false,
    FeatureFlag.SmartRules to false,
    FeatureFlag.SmartSettings to false,
    FeatureFlag.SmartTrackpadSuggest to false,
    FeatureFlag.SmartTrackpadSnap to false,
    FeatureFlag.SmartOcr to false,
    FeatureFlag.DeckEditor to true,
    FeatureFlag.Connection to true,
    FeatureFlag.Keyboard to true,
    FeatureFlag.Clipboard to true,
    FeatureFlag.Settings to true,
    FeatureFlag.Labs to false,
    FeatureFlag.LabAirMouse to false,
    FeatureFlag.LabAirTouch to false,
    FeatureFlag.LabBackTap to false,
    FeatureFlag.LabVolumeKeys to false,
)

class FeatureFlaggedEntitlementRepository(
    private val delegate: EntitlementRepository,
    @Suppress("UNUSED_PARAMETER") featureFlagRepository: FeatureFlagRepository,
) : EntitlementRepository {
    override val entitlement: Flow<Entitlement> = delegate.entitlement

    override suspend fun currentEntitlement(): Entitlement = delegate.currentEntitlement()

    override suspend fun refresh(): Result<Entitlement> = delegate.refresh()
}
