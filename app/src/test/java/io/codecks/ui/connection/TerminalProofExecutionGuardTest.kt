package io.codecks.ui.connection

import io.codecks.data.ConnectionConfig
import io.codecks.domain.connection.MacCapabilityCheckReceipt
import io.codecks.domain.connection.MacCapabilityCheckResult
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.test.runTest

class TerminalProofExecutionGuardTest {
    @Test
    fun blocksMissingAndCrossTargetProofButAllowsCurrentDurableProof() = runTest {
        val snapshotStore = SetupSnapshotStore.inMemory()
        val proofStore = SetupTerminalProofStore.inMemory()
        val guard = TerminalProofExecutionGuard(snapshotStore, proofStore)
        val config = ConnectionConfig(
            host = "mac.local",
            port = 22,
            user = "user",
            hasKey = true,
            hostKey = "mac.local ssh-ed25519 key",
        )

        assertThrows(IllegalStateException::class.java) { guard.requireVerified(config) }

        SetupStep.entries.forEach { snapshotStore.record(it, SetupReceiptResult.Passed) }
        val snapshot = snapshotStore.load()
        val checkedAt = System.currentTimeMillis()
        val capabilities = REQUIRED_CORE_MAC_CAPABILITIES.map {
            MacCapabilityCheckReceipt(
                capability = it,
                result = MacCapabilityCheckResult.Passed,
                failureCode = null,
                checkedAtEpochMs = checkedAt,
            )
        }
        proofStore.record(
            SshTerminalReceipt(
                setupRevision = snapshot.revisionToken(),
                macTargetId = config.setupTargetId(),
                result = SshTerminalResult.Passed,
                attempt = 1,
                durationMs = 10,
                completedAtEpochMs = checkedAt,
                confirmedCapabilities = REQUIRED_CORE_MAC_CAPABILITIES,
                capabilityCheckedAtEpochMs = capabilities.associate {
                    it.capability to it.checkedAtEpochMs
                },
            ),
            capabilities,
        )

        guard.requireVerified(config)
        assertThrows(IllegalStateException::class.java) {
            guard.requireVerified(config.copy(host = "other.local"))
        }
    }

    @Test
    fun rejectsStaleFutureAndStaleCapabilityProof() = runTest {
        val now = 2_000_000_000_000L
        val snapshotStore = SetupSnapshotStore.inMemory()
        val proofStore = SetupTerminalProofStore.inMemory()
        val guard = TerminalProofExecutionGuard(snapshotStore, proofStore) { now }
        val config = ConnectionConfig(
            host = "mac.local",
            port = 22,
            user = "user",
            hasKey = true,
            hostKey = "mac.local ssh-ed25519 key",
        )
        SetupStep.entries.forEach { snapshotStore.record(it, SetupReceiptResult.Passed) }
        val snapshot = snapshotStore.load()

        suspend fun recordAt(receiptAt: Long, capabilityAt: Long) {
            val capabilities = REQUIRED_CORE_MAC_CAPABILITIES.map {
                MacCapabilityCheckReceipt(it, MacCapabilityCheckResult.Passed, null, capabilityAt)
            }
            proofStore.record(
                SshTerminalReceipt(
                    setupRevision = snapshot.revisionToken(),
                    macTargetId = config.setupTargetId(),
                    result = SshTerminalResult.Passed,
                    attempt = 1,
                    durationMs = 10,
                    completedAtEpochMs = receiptAt,
                    confirmedCapabilities = REQUIRED_CORE_MAC_CAPABILITIES,
                    capabilityCheckedAtEpochMs = capabilities.associate {
                        it.capability to it.checkedAtEpochMs
                    },
                ),
                capabilities,
            )
        }

        val stale = now - SETUP_TERMINAL_RECEIPT_MAX_AGE_MS - 1
        recordAt(stale, stale)
        assertThrows(IllegalStateException::class.java) { guard.requireVerified(config) }
        recordAt(now + 5L * 60L * 1_000L + 1, now - 1)
        assertThrows(IllegalStateException::class.java) { guard.requireVerified(config) }
        recordAt(now - 1, now - SETUP_TERMINAL_RECEIPT_MAX_AGE_MS - 1)
        assertThrows(IllegalStateException::class.java) { guard.requireVerified(config) }
    }
}
