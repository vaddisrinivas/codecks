package io.codecks.domain.reactive.deskdock

import io.codecks.domain.reactive.MacStateSnapshot

enum class DeskDockSignalState {
    Positive,
    Negative,
    Unknown,
}

enum class DeskDockManualOverride {
    None,
    ForceDocked,
    ForceAway,
    Suspended,
}

enum class DeskDockState {
    Away,
    Candidate,
    Docked,
    Cooldown,
}

data class DeskDockSignals(
    val capturedAtMillis: Long,
    val bluetoothNear: DeskDockSignalState = DeskDockSignalState.Unknown,
    val charging: DeskDockSignalState = DeskDockSignalState.Unknown,
    val faceUp: DeskDockSignalState = DeskDockSignalState.Unknown,
    val stationary: DeskDockSignalState = DeskDockSignalState.Unknown,
    val ambientLightStable: DeskDockSignalState = DeskDockSignalState.Unknown,
    val nfcTagPresent: DeskDockSignalState = DeskDockSignalState.Unknown,
    val manualOverride: DeskDockManualOverride = DeskDockManualOverride.None,
    val macState: MacStateSnapshot? = null,
) {
    init {
        require(capturedAtMillis >= 0) { "DeskDockSignals capturedAtMillis must not be negative." }
    }
}

data class DeskDockConfig(
    val enterThreshold: Int = 75,
    val exitThreshold: Int = 45,
    val minDwellMillis: Long = 30_000L,
    val cooldownMillis: Long = 60_000L,
    val maxMacStateAgeMillis: Long = 5_000L,
) {
    init {
        require(enterThreshold in 1..100) { "DeskDockConfig enterThreshold must be 1..100." }
        require(exitThreshold in 0 until enterThreshold) {
            "DeskDockConfig exitThreshold must be below enterThreshold."
        }
        require(minDwellMillis >= 0) { "DeskDockConfig minDwellMillis must not be negative." }
        require(cooldownMillis >= 0) { "DeskDockConfig cooldownMillis must not be negative." }
        require(maxMacStateAgeMillis > 0) { "DeskDockConfig maxMacStateAgeMillis must be positive." }
    }
}

data class DeskDockSession(
    val state: DeskDockState = DeskDockState.Away,
    val candidateSinceMillis: Long? = null,
    val dockedSinceMillis: Long? = null,
    val cooldownUntilMillis: Long? = null,
    val manualOverride: DeskDockManualOverride = DeskDockManualOverride.None,
)

data class DeskDockScore(
    val confidence: Int,
    val reasons: List<String>,
) {
    init {
        require(confidence in 0..100) { "DeskDockScore confidence must be 0..100." }
        require(reasons.none { it.isBlank() }) { "DeskDockScore reasons must not contain blank values." }
    }
}

data class DeskDockDecision(
    val state: DeskDockState,
    val score: DeskDockScore,
    val session: DeskDockSession,
    val eligibleForLaunch: Boolean,
    val explanation: String,
)
