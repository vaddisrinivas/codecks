package io.codecks.ui.clipboard

import io.codecks.domain.clipboard.ClipboardEndpoint
import io.codecks.domain.clipboard.ClipboardSyncMode
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
}
