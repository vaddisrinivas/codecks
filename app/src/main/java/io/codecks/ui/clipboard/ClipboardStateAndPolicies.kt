package io.codecks.ui.clipboard

import io.codecks.domain.privacy.DiagnosticResultCode
import io.codecks.domain.clipboard.ClipboardDirection
import io.codecks.domain.clipboard.ClipboardBatteryPolicy
import io.codecks.domain.clipboard.ClipboardEndpoint
import io.codecks.domain.clipboard.ClipboardHash
import io.codecks.domain.clipboard.ClipboardRevision
import io.codecks.domain.clipboard.ClipboardReceipt
import io.codecks.domain.clipboard.ClipboardSessionPhase
import io.codecks.domain.clipboard.ClipboardSessionState
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.clipboard.ClipboardTerminalResult
import kotlinx.coroutines.CancellationException

private const val MAX_SYNC_INTERVAL_MINUTES = 240
private const val MIN_SYNC_INTERVAL_MINUTES = 1
private const val MAX_FAILURE_CLASS = "runtime.unknown"

data class ClipboardUiState(
    val phoneText: String = "",
    val macText: String = "",
    val mode: ClipboardSyncMode = ClipboardSyncMode.Off,
    val status: String = "Clipboard idle",
    val isRunning: Boolean = false,
    val connectionReady: Boolean = false,
    val connectionConfigured: Boolean = false,
    val latestRevision: Long = 0L,
    val phoneHash: String = "",
    val macHash: String = "",
    val syncIntervalMinutes: Int = 5,
    val history: List<ClipboardRevision> = emptyList(),
    val hasConflict: Boolean = false,
    val isRemoteOffline: Boolean = false,
    val staleEndpoints: Set<ClipboardEndpoint> = emptySet(),
    val phonePreview: String = "Empty",
    val macPreview: String = "Empty",
    val phoneRisk: String? = null,
    val macRisk: String? = null,
    val lastSafetyWarning: String? = null,
    val liveSyncVisible: Boolean = false,
    val syncFailureCount: Int = 0,
    val nextSyncDelaySeconds: Long = 0L,
    val lastFailureClass: String? = null,
    val lastSyncReceipt: ClipboardReceipt? = null,
    val session: ClipboardSessionState = ClipboardSessionState(),
    val batterySaverActive: Boolean = false,
    val pendingSharedText: Boolean = false,
)

internal data class PendingClipboardVerification(
    val direction: ClipboardDirection,
    val expectedHash: String,
)

internal fun PendingClipboardVerification.matches(phoneText: String?, macText: String?): Boolean =
    when (direction) {
        ClipboardDirection.PhoneToMac -> macText?.let(ClipboardHash::of) == expectedHash
        ClipboardDirection.MacToPhone -> phoneText?.let(ClipboardHash::of) == expectedHash
        ClipboardDirection.Bidirectional -> false
    }

internal fun sharedTextTerminalConsumes(result: ClipboardTerminalResult): Boolean =
    result == ClipboardTerminalResult.VerifiedSuccess ||
        result == ClipboardTerminalResult.AppliedUnverified

internal fun clipboardSessionExpiryDelayMillis(
    expiresAtElapsedRealtimeMillis: Long,
    elapsedRealtimeMillis: Long,
): Long = (expiresAtElapsedRealtimeMillis - elapsedRealtimeMillis).coerceAtLeast(0L)

internal fun clipboardAutomaticPollingEligible(
    mode: ClipboardSyncMode,
    connectionReady: Boolean,
    phase: ClipboardSessionPhase,
    batterySaverActive: Boolean,
): Boolean =
    mode != ClipboardSyncMode.Off &&
        connectionReady &&
        ClipboardBatteryPolicy.automaticPollingAllowed(phase, batterySaverActive)

/**
 * Clipboard text arriving from another device is always private to the user.
 *
 * Android 13+ and Samsung clipboard surfaces may render a clipboard preview after an app writes
 * a clip. Mark every synchronized write sensitive, not only strings matched by the heuristic
 * content guard. The guard still controls whether automatic transfer is allowed.
 */
internal fun clipboardSystemPreviewMustBeHidden(): Boolean = true

internal const val LEGACY_CLIP_DESCRIPTION_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * `ClipDescription.EXTRA_IS_SENSITIVE` was added in API 33. Never resolve that field on older
 * Android releases; they use the documented literal compatibility key.
 */
internal fun clipboardSensitiveExtrasKey(
    sdkInt: Int,
    api33Key: () -> String,
): String = if (sdkInt >= 33) api33Key() else LEGACY_CLIP_DESCRIPTION_IS_SENSITIVE

internal fun clipboardDiagnosticResult(result: ClipboardTerminalResult): DiagnosticResultCode = when (result) {
    ClipboardTerminalResult.VerifiedSuccess -> DiagnosticResultCode.SUCCEEDED
    ClipboardTerminalResult.AppliedUnverified -> DiagnosticResultCode.RETRYABLE
    ClipboardTerminalResult.Blocked -> DiagnosticResultCode.BLOCKED
    ClipboardTerminalResult.Failure -> DiagnosticResultCode.FAILED
    ClipboardTerminalResult.Cancellation -> DiagnosticResultCode.CANCELLED
    ClipboardTerminalResult.Conflict -> DiagnosticResultCode.BLOCKED
}

internal const val VERIFICATION_FAILURE_CLASS = "verification.unconfirmed"

internal fun Throwable.rethrowIfCancellationOrFatal() {
    when (this) {
        is CancellationException,
        is VirtualMachineError,
        is ThreadDeath,
        is LinkageError,
        -> throw this
    }
}
