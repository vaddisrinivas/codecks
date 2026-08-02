package io.codecks.domain.clipboard

object ClipboardBatteryPolicy {
    const val MAX_RETRY_COUNT = 7
    private const val MAX_RETRY_DELAY_MILLIS = 120_000L

    fun automaticPollingAllowed(
        sessionPhase: ClipboardSessionPhase,
        batterySaverActive: Boolean,
    ): Boolean = sessionPhase == ClipboardSessionPhase.ActiveVisible && !batterySaverActive

    fun retryDelayMillis(failureTier: Int): Long = when (failureTier.coerceAtLeast(1)) {
        1 -> 3_000L
        2 -> 8_000L
        3 -> 15_000L
        4 -> 30_000L
        5 -> 60_000L
        else -> MAX_RETRY_DELAY_MILLIS
    }.coerceAtMost(MAX_RETRY_DELAY_MILLIS)
}
