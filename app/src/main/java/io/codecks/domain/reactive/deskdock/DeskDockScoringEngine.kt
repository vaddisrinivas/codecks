package io.codecks.domain.reactive.deskdock

class DeskDockScoringEngine(
    private val config: DeskDockConfig = DeskDockConfig(),
) {
    fun evaluate(
        signals: DeskDockSignals,
        previous: DeskDockSession = DeskDockSession(),
    ): DeskDockDecision {
        val manual = signals.manualOverride.takeUnless { it == DeskDockManualOverride.None }
            ?: previous.manualOverride
        when (manual) {
            DeskDockManualOverride.ForceDocked -> return manualDecision(
                state = DeskDockState.Docked,
                confidence = 100,
                reason = "manual_force_docked",
                nowMillis = signals.capturedAtMillis,
                previous = previous,
                manual = manual,
            )
            DeskDockManualOverride.ForceAway -> return manualDecision(
                state = DeskDockState.Away,
                confidence = 0,
                reason = "manual_force_away",
                nowMillis = signals.capturedAtMillis,
                previous = previous,
                manual = manual,
            )
            DeskDockManualOverride.Suspended -> return manualDecision(
                state = DeskDockState.Cooldown,
                confidence = 0,
                reason = "manual_suspended",
                nowMillis = signals.capturedAtMillis,
                previous = previous,
                manual = manual,
            )
            DeskDockManualOverride.None -> Unit
        }

        val score = score(signals)
        val now = signals.capturedAtMillis
        val inCooldown = previous.cooldownUntilMillis?.let { now < it } == true
        val nextSession = when {
            inCooldown && score.confidence >= config.enterThreshold -> previous.copy(
                state = DeskDockState.Cooldown,
                manualOverride = DeskDockManualOverride.None,
            )
            previous.state == DeskDockState.Docked && score.confidence >= config.exitThreshold -> previous.copy(
                state = DeskDockState.Docked,
                manualOverride = DeskDockManualOverride.None,
            )
            previous.state == DeskDockState.Docked -> DeskDockSession(
                state = DeskDockState.Cooldown,
                cooldownUntilMillis = now + config.cooldownMillis,
            )
            score.confidence >= config.enterThreshold -> {
                val candidateSince = previous.candidateSinceMillis ?: now
                if (now - candidateSince >= config.minDwellMillis) {
                    DeskDockSession(
                        state = DeskDockState.Docked,
                        dockedSinceMillis = now,
                    )
                } else {
                    DeskDockSession(
                        state = DeskDockState.Candidate,
                        candidateSinceMillis = candidateSince,
                    )
                }
            }
            else -> DeskDockSession(state = DeskDockState.Away)
        }

        val decisionScore = if (inCooldown && nextSession.state == DeskDockState.Cooldown) {
            score.copy(reasons = score.reasons + "cooldown_active")
        } else {
            score
        }
        return DeskDockDecision(
            state = nextSession.state,
            score = decisionScore,
            session = nextSession,
            eligibleForLaunch = nextSession.state == DeskDockState.Docked,
            explanation = decisionScore.reasons.joinToString(", "),
        )
    }

    fun score(signals: DeskDockSignals): DeskDockScore {
        var confidence = 0
        val reasons = mutableListOf<String>()
        fun add(state: DeskDockSignalState, points: Int, reason: String) {
            when (state) {
                DeskDockSignalState.Positive -> {
                    confidence += points
                    reasons += reason
                }
                DeskDockSignalState.Negative -> reasons += "not_$reason"
                DeskDockSignalState.Unknown -> Unit
            }
        }

        add(signals.bluetoothNear, 25, "bluetooth_near")
        add(signals.charging, 20, "charging")
        add(signals.faceUp, 15, "face_up")
        add(signals.stationary, 20, "stationary")
        add(signals.ambientLightStable, 10, "ambient_light_stable")
        add(signals.nfcTagPresent, 30, "nfc_tag")
        signals.macState?.let { state ->
            if (!state.isBasicStateExpired(signals.capturedAtMillis, config.maxMacStateAgeMillis)) {
                confidence += 10
                reasons += "mac_state_fresh"
            } else {
                reasons += "mac_state_stale"
            }
        }
        val finalReasons = reasons.ifEmpty { listOf("no_positive_signals") }
        return DeskDockScore(
            confidence = confidence.coerceIn(0, 100),
            reasons = finalReasons.sorted(),
        )
    }

    private fun manualDecision(
        state: DeskDockState,
        confidence: Int,
        reason: String,
        nowMillis: Long,
        previous: DeskDockSession,
        manual: DeskDockManualOverride,
    ): DeskDockDecision {
        val session = when (state) {
            DeskDockState.Docked -> previous.copy(
                state = DeskDockState.Docked,
                dockedSinceMillis = previous.dockedSinceMillis ?: nowMillis,
                cooldownUntilMillis = null,
                manualOverride = manual,
            )
            DeskDockState.Cooldown -> DeskDockSession(
                state = DeskDockState.Cooldown,
                cooldownUntilMillis = nowMillis + config.cooldownMillis,
                manualOverride = manual,
            )
            DeskDockState.Away,
            DeskDockState.Candidate,
            -> DeskDockSession(
                state = DeskDockState.Away,
                manualOverride = manual,
            )
        }
        val score = DeskDockScore(confidence = confidence, reasons = listOf(reason))
        return DeskDockDecision(
            state = session.state,
            score = score,
            session = session,
            eligibleForLaunch = session.state == DeskDockState.Docked,
            explanation = reason,
        )
    }
}
