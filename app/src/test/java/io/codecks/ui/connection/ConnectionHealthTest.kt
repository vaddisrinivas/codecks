package io.codecks.ui.connection

import io.codecks.data.ConnectionConfig
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.domain.connection.RemediationAction
import io.codecks.domain.connection.MacCapabilityCheckReceipt
import io.codecks.domain.connection.MacCapabilityCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionHealthTest {
    @Test
    fun proofClockTransitionsReadyStateAtEarliestDurableExpiry() {
        val checkedAt = 2_000_000_000_000L
        val config = ConnectionConfig(
            host = "mac.local",
            user = "me",
            hostKey = "mac.local ssh-ed25519 key",
            hasKey = true,
        )
        val snapshot = SetupSnapshot(
            receipts = SetupStep.entries.associateWith {
                SetupReceipt(step = it, result = SetupReceiptResult.Passed, completedAtMillis = checkedAt)
            },
        )
        val capabilities = REQUIRED_CORE_MAC_CAPABILITIES.map {
            MacCapabilityCheckReceipt(it, MacCapabilityCheckResult.Passed, null, checkedAt)
        }
        val state = ConnectionUiState(
            config = config,
            setupSnapshot = snapshot,
            sshTerminalReceipt = SshTerminalReceipt(
                setupRevision = snapshot.revisionToken(),
                macTargetId = config.setupTargetId(),
                result = SshTerminalResult.Passed,
                attempt = 1,
                durationMs = 1,
                completedAtEpochMs = checkedAt,
                confirmedCapabilities = REQUIRED_CORE_MAC_CAPABILITIES,
                capabilityCheckedAtEpochMs = capabilities.associate {
                    it.capability to it.checkedAtEpochMs
                },
            ),
            macCapabilityReceipts = capabilities,
        )
        val expiry = checkedAt + SETUP_TERMINAL_RECEIPT_MAX_AGE_MS + 1L

        assertEquals(expiry, state.nextSetupProofExpiryAtEpochMs())
        assertEquals(ConnectionHealthKind.Ready, state.connectionHealth(checkedAt).kind)
        assertEquals(ConnectionHealthKind.Offline, state.connectionHealth(expiry).kind)
    }

    @Test
    fun mapsSetupProgressIntoDistinctHealthStates() {
        assertEquals(
            ConnectionHealthKind.NotConfigured,
            connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, error = null).kind,
        )
        assertEquals(
            ConnectionHealthKind.NeedsFingerprint,
            connectionHealth(ConnectionConfig(host = "mac.local", user = "me"), ConnectionOperation.Idle, error = null).kind,
        )
        assertEquals(
            ConnectionHealthKind.NeedsKey,
            connectionHealth(
                ConnectionConfig(host = "mac.local", user = "me", hostKey = "mac.local ssh-ed25519 key"),
                ConnectionOperation.Idle,
                error = null,
            ).kind,
        )
        assertEquals(
            ConnectionHealthKind.Ready,
            connectionHealth(
                ConnectionConfig(host = "mac.local", user = "me", hostKey = "mac.local ssh-ed25519 key", hasKey = true),
                ConnectionOperation.Idle,
                error = null,
            ).kind,
        )
    }

    @Test
    fun operationTakesPriorityOverSavedConfig() {
        val ready = ConnectionConfig(host = "mac.local", user = "me", hostKey = "key", hasKey = true)

        assertEquals(ConnectionHealthKind.Scanning, connectionHealth(ready, ConnectionOperation.Scanning, null).kind)
        assertEquals(ConnectionHealthKind.Verifying, connectionHealth(ready, ConnectionOperation.Verifying, null).kind)
        assertEquals(ConnectionHealthKind.Connecting, connectionHealth(ready, ConnectionOperation.Connecting, null).kind)
        assertEquals(ConnectionHealthKind.Testing, connectionHealth(ready, ConnectionOperation.Testing, null).kind)
    }

    @Test
    fun mapsFailureMessagesIntoActionableHealthStates() {
        assertEquals(
            ConnectionHealthKind.FingerprintMismatch,
            connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "Host key verification failed").kind,
        )
        assertEquals(
            ConnectionHealthKind.AuthFailed,
            connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "Permission denied, please try again").kind,
        )
        assertEquals(
            ConnectionHealthKind.Offline,
            connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "No route to host").kind,
        )
    }

    @Test
    fun exposesStableLabelsForSharedSurfaces() {
        assertEquals(
            "Ready",
            connectionHealth(
                ConnectionConfig(host = "mac.local", user = "me", hostKey = "mac.local ssh-ed25519 key", hasKey = true),
                ConnectionOperation.Idle,
                error = null,
            ).statusLabel(),
        )
        assertEquals("Setup needed", connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, error = null).statusLabel())
        assertEquals("Offline", connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "No route to host").statusLabel())
    }

    @Test
    fun exposesTypedIssuesWithoutReplacingExistingHealth() {
        val authFailed = connectionHealth(
            ConnectionConfig(),
            ConnectionOperation.Idle,
            "Permission denied",
        )
        val offline = connectionHealth(
            ConnectionConfig(),
            ConnectionOperation.Idle,
            "No route to host",
        )

        assertEquals(ConnectionIssueCode.SSH_AUTH_FAILED, authFailed.issueCode)
        assertEquals(
            listOf(RemediationAction.ReenterSshCredentials),
            authFailed.remediations,
        )
        assertEquals(ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP, offline.issueCode)
    }

    @Test
    fun classifiesOfflineAsTransientAndSecurityOrToolsAsRepairRequired() {
        val offline = connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "Mac is asleep")
        val auth = connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "Authentication failed")
        val hostKey = connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "Host key verification failed")
        val tool = connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "Required tool is missing")

        assertEquals(ConnectionHealthRetryClass.Transient, offline.retryClass)
        listOf(auth, hostKey, tool).forEach {
            assertEquals(ConnectionHealthRetryClass.RepairRequired, it.retryClass)
        }
        assertEquals(ConnectionIssueCode.MAC_TOOL_MISSING, tool.issueCode)
        assertFalse(tool.isReady)
    }

    @Test
    fun unknownExceptionsUseTypedFallbackWithoutRawDetail() {
        val raw = "JSchException: internal packet parse failure at secret-host.local"
        val health = connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, raw)

        assertEquals(ConnectionIssueCode.UNKNOWN, health.issueCode)
        assertEquals(ConnectionHealthRetryClass.RepairRequired, health.retryClass)
        assertFalse(health.detail.contains("secret-host.local"))
        assertEquals("Connection needs attention", health.title)
    }
}
