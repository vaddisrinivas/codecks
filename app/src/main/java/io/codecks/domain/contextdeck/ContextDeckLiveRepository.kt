package io.codecks.domain.contextdeck

data class ContextDeckLiveSnapshot(
    val signals: List<LiveSignal>,
    val analogControls: List<AnalogControl>,
    val observedAtMillis: Long,
) {
    init {
        require(observedAtMillis >= 0) { "Context Deck snapshot time must be non-negative." }
        require(signals.map(LiveSignal::id).distinct().size == signals.size) { "Live signal ids must be unique." }
        require(analogControls.map(AnalogControl::kind).distinct().size == analogControls.size) {
            "Analog control kinds must be unique."
        }
    }
}

interface ContextDeckLiveRepository {
    suspend fun refresh(): Result<ContextDeckLiveSnapshot>
    suspend fun setAnalog(kind: AnalogControlKind, valuePercent: Int): Result<Unit>
}
