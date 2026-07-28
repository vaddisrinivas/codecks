package io.codecks.core.trackpad

import io.codecks.domain.reactive.deskdock.DeskDockDecision
import io.codecks.domain.reactive.deskdock.DeskDockScoringEngine
import io.codecks.domain.reactive.deskdock.DeskDockSession
import io.codecks.domain.reactive.deskdock.DeskDockSignals

enum class DeskDockLaunchTrigger {
    BackgroundSignal,
    ExternalAutomation,
    ExplicitUserAction,
}

enum class DeskDockTrackpadRecommendation {
    None,
    SuggestTrackpad,
    LaunchUnlockedTrackpad,
    LaunchRestrictedTrackpad,
    RequireUnlock,
}

data class DeskDockTrackpadLaunchInput(
    val signals: DeskDockSignals,
    val previousSession: DeskDockSession = DeskDockSession(),
    val lockscreenState: LockscreenControlState,
    val featureEnabled: Boolean,
    val automaticLaunchEnabled: Boolean,
    val hidConnected: Boolean,
    val trigger: DeskDockLaunchTrigger,
)

data class DeskDockTrackpadLaunchDecision(
    val deskDock: DeskDockDecision,
    val lockscreenDecision: LockscreenDecision,
    val recommendation: DeskDockTrackpadRecommendation,
    val reasons: List<String>,
)

class DeskDockTrackpadLaunchPolicy(
    private val scoringEngine: DeskDockScoringEngine = DeskDockScoringEngine(),
) {
    fun evaluate(input: DeskDockTrackpadLaunchInput): DeskDockTrackpadLaunchDecision {
        val deskDock = scoringEngine.evaluate(input.signals, input.previousSession)
        val lockscreenDecision = LockscreenTrackpadPolicy.decision(input.lockscreenState)
        val reasons = mutableListOf<String>()

        fun decision(recommendation: DeskDockTrackpadRecommendation): DeskDockTrackpadLaunchDecision =
            DeskDockTrackpadLaunchDecision(
                deskDock = deskDock,
                lockscreenDecision = lockscreenDecision,
                recommendation = recommendation,
                reasons = (reasons + deskDock.score.reasons).distinct().sorted(),
            )

        if (!input.featureEnabled) {
            reasons += "deskdock_disabled"
            return decision(DeskDockTrackpadRecommendation.None)
        }
        if (!deskDock.eligibleForLaunch) {
            reasons += "desk_not_docked"
            return decision(DeskDockTrackpadRecommendation.None)
        }
        if (!input.hidConnected) {
            reasons += "hid_disconnected"
            return decision(DeskDockTrackpadRecommendation.None)
        }
        if (!input.automaticLaunchEnabled) {
            reasons += "automatic_launch_disabled"
            return decision(DeskDockTrackpadRecommendation.SuggestTrackpad)
        }
        if (input.trigger == DeskDockLaunchTrigger.BackgroundSignal) {
            reasons += "background_launch_blocked"
            return decision(DeskDockTrackpadRecommendation.SuggestTrackpad)
        }

        return when (lockscreenDecision) {
            LockscreenDecision.AllowRestrictedPointer -> decision(
                DeskDockTrackpadRecommendation.LaunchRestrictedTrackpad,
            )
            LockscreenDecision.ForwardToUnlockedTrackpad -> decision(
                DeskDockTrackpadRecommendation.LaunchUnlockedTrackpad,
            )
            LockscreenDecision.RequireUnlock -> {
                reasons += "unlock_required"
                decision(DeskDockTrackpadRecommendation.RequireUnlock)
            }
            LockscreenDecision.IgnoreAutomaticEntry -> {
                reasons += "automatic_entry_ignored"
                decision(DeskDockTrackpadRecommendation.None)
            }
        }
    }
}
