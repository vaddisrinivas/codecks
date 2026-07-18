package io.codex.s23deck.domain.commerce

import kotlinx.coroutines.flow.Flow

val DEFAULT_FEATURE_FLAGS = mapOf(
    FeatureFlag.Deck to true,
    FeatureFlag.Trackpad to true,
    FeatureFlag.Automations to true,
    FeatureFlag.Ai to true,
    FeatureFlag.AiBuilder to true,
    FeatureFlag.ContextDeck to false,
    FeatureFlag.DeckEditor to true,
    FeatureFlag.Connection to true,
    FeatureFlag.Keyboard to true,
    FeatureFlag.Clipboard to true,
    FeatureFlag.Activity to false,
    FeatureFlag.Settings to true,
    FeatureFlag.Devices to false,
    FeatureFlag.Premium to false,
    FeatureFlag.Widget to false,
    FeatureFlag.Appearance to false,
    FeatureFlag.Advanced to false,
    FeatureFlag.Paywall to false,
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
