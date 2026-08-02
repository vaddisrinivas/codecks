package io.codecks.ui.clipboard

import io.codecks.domain.clipboard.ClipboardEndpoint
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.clipboard.ClipboardTerminalResult
import io.codecks.domain.clipboard.ClipboardDirection
import io.codecks.domain.clipboard.ClipboardHash
import io.codecks.domain.privacy.DiagnosticResultCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardStatusPolicyTest {
    @Test
    fun statusUsesApprovedConnectionVocabulary() {
        assertEquals(
            ClipboardConnectionStatus.SetupNeeded,
            clipboardConnectionStatus(ClipboardUiState()),
        )
        assertEquals(
            ClipboardConnectionStatus.Ready,
            clipboardConnectionStatus(ClipboardUiState(connectionReady = true)),
        )
        assertEquals(
            ClipboardConnectionStatus.Checking,
            clipboardConnectionStatus(ClipboardUiState(connectionReady = true, isRunning = true)),
        )
        assertEquals(
            ClipboardConnectionStatus.Offline,
            clipboardConnectionStatus(ClipboardUiState(connectionReady = true, isRemoteOffline = true)),
        )
        assertEquals(
            ClipboardConnectionStatus.Failed,
            clipboardConnectionStatus(
                ClipboardUiState(connectionReady = true, lastFailureClass = "runtime.timeout"),
            ),
        )
    }

    @Test
    fun detailExplainsStateWithoutInventingAnotherStatus() {
        assertEquals(
            "Manual transfer is available. Automatic sync is off.",
            clipboardStatusDetail(
                ClipboardUiState(connectionReady = true, mode = ClipboardSyncMode.Off),
            ),
        )
        assertEquals(
            "Clipboard information needs another check.",
            clipboardStatusDetail(
                ClipboardUiState(
                    connectionReady = true,
                    mode = ClipboardSyncMode.Bidirectional,
                    staleEndpoints = setOf(ClipboardEndpoint.Mac),
                ),
            ),
        )
    }

    @Test
    fun appliedWithoutReadbackIsNotReportedAsSuccess() {
        assertEquals(
            DiagnosticResultCode.RETRYABLE,
            clipboardDiagnosticResult(ClipboardTerminalResult.AppliedUnverified),
        )
        assertEquals(
            DiagnosticResultCode.BLOCKED,
            clipboardDiagnosticResult(ClipboardTerminalResult.Blocked),
        )
    }

    @Test
    fun configuredButDisconnectedMacIsOfflineNotSetupNeeded() {
        assertEquals(
            ClipboardConnectionStatus.Offline,
            clipboardConnectionStatus(
                ClipboardUiState(
                    connectionConfigured = true,
                    connectionReady = false,
                    isRemoteOffline = true,
                ),
            ),
        )
    }

    @Test
    fun pendingReadbackUsesCapturedHashNotMutableUiText() {
        val pending = PendingClipboardVerification(
            ClipboardDirection.PhoneToMac,
            ClipboardHash.of("original"),
        )

        assertEquals(true, pending.matches(phoneText = null, macText = "original"))
        assertEquals(false, pending.matches(phoneText = null, macText = "new text"))
    }

    @Test
    fun sharedTextIsConsumedOnlyAfterTransferWasApplied() {
        assertEquals(true, sharedTextTerminalConsumes(ClipboardTerminalResult.VerifiedSuccess))
        assertEquals(true, sharedTextTerminalConsumes(ClipboardTerminalResult.AppliedUnverified))
        assertEquals(false, sharedTextTerminalConsumes(ClipboardTerminalResult.Failure))
        assertEquals(false, sharedTextTerminalConsumes(ClipboardTerminalResult.Blocked))
        assertEquals(false, sharedTextTerminalConsumes(ClipboardTerminalResult.Cancellation))
    }

    @Test
    fun sessionExpiryDelayUsesMonotonicElapsedTime() {
        val expiresAtElapsed = 100_000L
        assertEquals(10_000L, clipboardSessionExpiryDelayMillis(expiresAtElapsed, 90_000L))
        assertEquals(0L, clipboardSessionExpiryDelayMillis(expiresAtElapsed, 100_001L))
    }

    @Test
    fun automaticPollingRequiresProofReadyConnection() {
        assertEquals(
            false,
            clipboardAutomaticPollingEligible(
                mode = ClipboardSyncMode.Bidirectional,
                connectionReady = false,
                phase = io.codecks.domain.clipboard.ClipboardSessionPhase.ActiveVisible,
                batterySaverActive = false,
            ),
        )
        assertEquals(
            true,
            clipboardAutomaticPollingEligible(
                mode = ClipboardSyncMode.Bidirectional,
                connectionReady = true,
                phase = io.codecks.domain.clipboard.ClipboardSessionPhase.ActiveVisible,
                batterySaverActive = false,
            ),
        )
    }
}
