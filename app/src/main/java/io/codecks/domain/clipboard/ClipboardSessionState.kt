package io.codecks.domain.clipboard

const val DEFAULT_CLIPBOARD_SESSION_DURATION_MILLIS = 15 * 60 * 1_000L

enum class ClipboardSessionPhase {
    Inactive,
    Hidden,
    Locked,
    Expired,
    ActiveVisible,
}

/**
 * Process-memory-only authority to read the phone clipboard.
 *
 * A new process always starts inactive. Persisted sync preferences do not restore this authority.
 */
data class ClipboardSessionState(
    val requestedUntilMillis: Long? = null,
    val appForeground: Boolean = false,
    val surfaceVisible: Boolean = false,
    val deviceUnlocked: Boolean = false,
    val phase: ClipboardSessionPhase = ClipboardSessionPhase.Inactive,
) {
    val canReadPhoneClipboard: Boolean
        get() = phase == ClipboardSessionPhase.ActiveVisible

    fun start(
        nowMillis: Long,
        durationMillis: Long = DEFAULT_CLIPBOARD_SESSION_DURATION_MILLIS,
    ): ClipboardSessionState {
        require(durationMillis > 0L) { "Clipboard session duration must be positive" }
        return copy(requestedUntilMillis = nowMillis + durationMillis).evaluate(nowMillis)
    }

    fun stop(): ClipboardSessionState =
        copy(requestedUntilMillis = null, phase = ClipboardSessionPhase.Inactive)

    fun withEnvironment(
        appForeground: Boolean = this.appForeground,
        surfaceVisible: Boolean = this.surfaceVisible,
        deviceUnlocked: Boolean = this.deviceUnlocked,
        nowMillis: Long,
    ): ClipboardSessionState = copy(
        appForeground = appForeground,
        surfaceVisible = surfaceVisible,
        deviceUnlocked = deviceUnlocked,
    ).evaluate(nowMillis)

    fun evaluate(nowMillis: Long): ClipboardSessionState {
        val nextPhase = when {
            requestedUntilMillis == null -> ClipboardSessionPhase.Inactive
            nowMillis >= requestedUntilMillis -> ClipboardSessionPhase.Expired
            !appForeground || !surfaceVisible -> ClipboardSessionPhase.Hidden
            !deviceUnlocked -> ClipboardSessionPhase.Locked
            else -> ClipboardSessionPhase.ActiveVisible
        }
        return copy(phase = nextPhase)
    }
}
