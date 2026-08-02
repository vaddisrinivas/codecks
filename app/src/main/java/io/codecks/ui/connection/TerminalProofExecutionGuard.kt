package io.codecks.ui.connection

import android.content.Context
import io.codecks.data.ConnectionConfig
import io.codecks.domain.connection.MacCapabilityCheckResult

/** Blocks runtime SSH execution unless the current endpoint owns durable terminal proof. */
class TerminalProofExecutionGuard(
    private val snapshotStore: SetupSnapshotStore,
    private val proofStore: SetupTerminalProofStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        SetupSnapshotStore(context.applicationContext),
        SetupTerminalProofStore(context.applicationContext),
    )

    fun requireVerified(config: ConnectionConfig) {
        val snapshot = snapshotStore.load()
        val proof = proofStore.load()
        val receipt = proof.sshReceipt
        val now = nowEpochMs()
        val passedCapabilities = proof.capabilityReceipts
            .filter { it.result == MacCapabilityCheckResult.Passed }
            .associate { it.capability to it.checkedAtEpochMs }
        val verified = config.isReady &&
            snapshot.isComplete &&
            receipt?.result == SshTerminalResult.Passed &&
            receipt.completedAtEpochMs.isFreshSetupProofAt(now) &&
            receipt.setupRevision == snapshot.revisionToken() &&
            receipt.macTargetId == config.setupTargetId() &&
            receipt.confirmedCapabilities.containsAll(REQUIRED_CORE_MAC_CAPABILITIES) &&
            passedCapabilities == receipt.capabilityCheckedAtEpochMs &&
            proof.capabilityReceipts.size == REQUIRED_CORE_MAC_CAPABILITIES.size &&
            proof.capabilityReceipts.all { it.result == MacCapabilityCheckResult.Passed } &&
            proof.capabilityReceipts.all {
                it.checkedAtEpochMs.isFreshSetupProofAt(now)
            }
        check(verified) {
            "Mac controls need verification. Test this Mac connection before running commands."
        }
    }
}
