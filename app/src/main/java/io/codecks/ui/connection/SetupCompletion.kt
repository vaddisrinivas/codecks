package io.codecks.ui.connection

import io.codecks.HidState
import io.codecks.domain.connection.MacCapabilityCheckReceipt
import io.codecks.domain.connection.MacCapabilityCheckResult
import io.codecks.domain.connection.MacSetupCapability
import java.security.MessageDigest

private const val MAX_SSH_TERMINAL_DURATION_MS = 30_000L
private const val MAX_SETUP_ATTEMPT = 1_000
internal const val SETUP_TERMINAL_RECEIPT_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
private const val SETUP_TERMINAL_RECEIPT_MAX_FUTURE_SKEW_MS = 5L * 60L * 1_000L
private val RevisionPattern = Regex("[a-f0-9]{64}")
val REQUIRED_CORE_MAC_CAPABILITIES: Set<MacSetupCapability> = setOf(
    MacSetupCapability.Reachability,
    MacSetupCapability.HostIdentity,
    MacSetupCapability.Authentication,
    MacSetupCapability.RemoteShell,
)

enum class SshTerminalResult {
    Passed,
    Timeout,
    Cancelled,
    ReachabilityFailed,
    AuthenticationFailed,
    KeyMissing,
    HostKeyMismatch,
    ToolMissing,
    Failed,
}

data class SshTerminalReceipt(
    val setupRevision: String,
    val macTargetId: String,
    val result: SshTerminalResult,
    val attempt: Int,
    val durationMs: Long,
    val completedAtEpochMs: Long,
    val confirmedCapabilities: Set<MacSetupCapability>,
    val capabilityCheckedAtEpochMs: Map<MacSetupCapability, Long>,
) {
    init {
        require(setupRevision.matches(RevisionPattern))
        require(macTargetId.isNotBlank())
        require(attempt in 1..MAX_SETUP_ATTEMPT)
        require(durationMs in 0L..MAX_SSH_TERMINAL_DURATION_MS)
        require(completedAtEpochMs >= durationMs)
        require(
            result == SshTerminalResult.Passed || confirmedCapabilities.isEmpty(),
        ) { "Failed SSH terminal receipts cannot claim capabilities." }
        require(capabilityCheckedAtEpochMs.keys == confirmedCapabilities)
        require(capabilityCheckedAtEpochMs.values.all { it in 0L..completedAtEpochMs })
    }
}

enum class HidTerminalResult {
    USER_CONFIRMED,
    CANCELLED,
    FAILED,
}

data class HidTerminalReceipt(
    val setupRevision: String,
    val macTargetId: String,
    val hidHostAddress: String,
    val result: HidTerminalResult,
    val completedAtEpochMs: Long,
) {
    init {
        require(setupRevision.matches(RevisionPattern))
        require(macTargetId.isNotBlank())
        require(hidHostAddress.isNotBlank())
        require(completedAtEpochMs >= 0L)
    }
}

enum class SetupRepairTarget(val label: String) {
    FindMac("Find Mac"),
    TrustMac("Review Mac trust"),
    Authorize("Authorize Mac"),
    VerifyControls("Test Mac controls"),
    HidPermission("Allow Bluetooth"),
    HidRegistration("Start Bluetooth input"),
    HidPairing("Pair a Mac"),
    HidConnection("Connect Trackpad"),
}

sealed interface SetupCompletionEvaluation {
    data object COMPLETE : SetupCompletionEvaluation

    data class REPAIR_REQUIRED(
        val target: SetupRepairTarget,
        val reasonCode: String,
    ) : SetupCompletionEvaluation
}

data class SetupCompletionProof(
    val snapshot: SetupSnapshot,
    val sshReceipt: SshTerminalReceipt?,
    val hidReceipt: HidTerminalReceipt?,
    val requiredMacCapabilities: Set<MacSetupCapability>,
    val currentMacCapabilityReceipts: List<MacCapabilityCheckReceipt>,
    val currentMacTargetId: String,
    val currentHidStatus: BluetoothSetupStatus,
    val currentHidHostAddress: String?,
    val evaluatedAtEpochMs: Long = System.currentTimeMillis(),
)

fun SetupSnapshot.revisionToken(): String {
    val safeSnapshot = SetupSnapshotCodec.encode(this)
    return MessageDigest.getInstance("SHA-256")
        .digest(safeSnapshot.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

fun evaluateSetupCompletion(proof: SetupCompletionProof): SetupCompletionEvaluation {
    proof.snapshot.firstMandatoryNotPassed?.let { step ->
        return SetupCompletionEvaluation.REPAIR_REQUIRED(step.toRepairTarget(), "setup_receipt_missing")
    }
    val revision = proof.snapshot.revisionToken()
    if (!proof.requiredMacCapabilities.containsAll(REQUIRED_CORE_MAC_CAPABILITIES)) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.VerifyControls,
            "required_capability_baseline_missing",
        )
    }
    val ssh = proof.sshReceipt
        ?: return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.VerifyControls,
            "ssh_receipt_missing",
        )
    if (ssh.setupRevision != revision) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.VerifyControls,
            "ssh_receipt_revision_mismatch",
        )
    }
    if (ssh.macTargetId != proof.currentMacTargetId) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.TrustMac,
            "ssh_target_mismatch",
        )
    }
    if (!ssh.completedAtEpochMs.isFreshAt(proof.evaluatedAtEpochMs)) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.VerifyControls,
            "ssh_receipt_expired",
        )
    }
    if (ssh.result != SshTerminalResult.Passed) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            ssh.result.repairTarget(),
            "ssh_${ssh.result.name.lowercase()}",
        )
    }
    if (ssh.confirmedCapabilities != proof.requiredMacCapabilities) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.VerifyControls,
            "ssh_capability_receipt_mismatch",
        )
    }
    val currentPassedCapabilities = proof.currentMacCapabilityReceipts
        .filter { receipt ->
            receipt.result == MacCapabilityCheckResult.Passed
        }
        .associate { receipt -> receipt.capability to receipt.checkedAtEpochMs }
    val expectedCurrentReceipts = currentPassedCapabilities.filterKeys {
        it in proof.requiredMacCapabilities
    }
    if (expectedCurrentReceipts != ssh.capabilityCheckedAtEpochMs) {
        val target = if (MacSetupCapability.HostIdentity !in currentPassedCapabilities.keys) {
            SetupRepairTarget.TrustMac
        } else {
            SetupRepairTarget.VerifyControls
        }
        return SetupCompletionEvaluation.REPAIR_REQUIRED(target, "current_mac_capability_missing")
    }
    if (proof.currentMacCapabilityReceipts.any {
            !it.checkedAtEpochMs.isFreshAt(proof.evaluatedAtEpochMs)
        }
    ) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.VerifyControls,
            "current_mac_capability_expired",
        )
    }
    val hid = proof.hidReceipt
        ?: return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.HidConnection,
            "hid_confirmation_missing",
        )
    if (hid.setupRevision != revision ||
        hid.result != HidTerminalResult.USER_CONFIRMED ||
        hid.macTargetId != proof.currentMacTargetId ||
        hid.hidHostAddress != proof.currentHidHostAddress ||
        !hid.completedAtEpochMs.isFreshAt(proof.evaluatedAtEpochMs)
    ) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.HidConnection,
            "hid_user_confirmation_missing",
        )
    }
    if (!proof.currentHidStatus.success) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            proof.currentHidStatus.remediation.toRepairTarget(),
            "hid_state_not_confirmed",
        )
    }
    return SetupCompletionEvaluation.COMPLETE
}

fun evaluateRuntimeSetupCompletion(
    state: ConnectionUiState?,
    hidState: HidState,
    permissionState: BluetoothPermissionState,
    hidReceipt: HidTerminalReceipt?,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupCompletionEvaluation {
    state ?: return SetupCompletionEvaluation.REPAIR_REQUIRED(
        SetupRepairTarget.FindMac,
        "setup_state_unavailable",
    )
    val targetId = state.config.takeIf { it.isReady }?.setupTargetId().orEmpty()
    if (targetId.isBlank()) {
        return SetupCompletionEvaluation.REPAIR_REQUIRED(
            SetupRepairTarget.FindMac,
            "mac_target_unavailable",
        )
    }
    val hidStatus = hidState.bluetoothSetupStatus(permissionState)
    val hidAddress = hidState.selectedHostAddress
    val ssh = state.sshTerminalReceipt
    return evaluateSetupCompletion(
        SetupCompletionProof(
            snapshot = state.setupSnapshot,
            sshReceipt = ssh,
            hidReceipt = hidReceipt,
            requiredMacCapabilities = REQUIRED_CORE_MAC_CAPABILITIES,
            currentMacCapabilityReceipts = state.macCapabilityReceipts,
            currentMacTargetId = targetId,
            currentHidStatus = hidStatus,
            currentHidHostAddress = hidAddress,
            evaluatedAtEpochMs = nowEpochMs,
        ),
    )
}

private fun Long.isFreshAt(nowEpochMs: Long): Boolean =
    this <= nowEpochMs + SETUP_TERMINAL_RECEIPT_MAX_FUTURE_SKEW_MS &&
        nowEpochMs - this <= SETUP_TERMINAL_RECEIPT_MAX_AGE_MS

private fun SetupStep.toRepairTarget(): SetupRepairTarget = when (this) {
    SetupStep.FindMac -> SetupRepairTarget.FindMac
    SetupStep.TrustMac -> SetupRepairTarget.TrustMac
    SetupStep.Authorize -> SetupRepairTarget.Authorize
    SetupStep.VerifyControls -> SetupRepairTarget.VerifyControls
}

private fun SshTerminalResult.repairTarget(): SetupRepairTarget = when (this) {
    SshTerminalResult.HostKeyMismatch -> SetupRepairTarget.TrustMac
    SshTerminalResult.AuthenticationFailed,
    SshTerminalResult.KeyMissing,
    -> SetupRepairTarget.Authorize
    SshTerminalResult.Passed,
    SshTerminalResult.Timeout,
    SshTerminalResult.Cancelled,
    SshTerminalResult.ReachabilityFailed,
    SshTerminalResult.ToolMissing,
    SshTerminalResult.Failed,
    -> SetupRepairTarget.VerifyControls
}

private fun BluetoothSetupRemediation.toRepairTarget(): SetupRepairTarget = when (this) {
    BluetoothSetupRemediation.RequestPermission,
    BluetoothSetupRemediation.OpenAppSettings,
    -> SetupRepairTarget.HidPermission
    BluetoothSetupRemediation.EnableBluetooth,
    BluetoothSetupRemediation.RetryHidRegistration,
    -> SetupRepairTarget.HidRegistration
    BluetoothSetupRemediation.OpenSystemPairing,
    BluetoothSetupRemediation.ChooseBondedHost,
    -> SetupRepairTarget.HidPairing
    BluetoothSetupRemediation.None,
    BluetoothSetupRemediation.RetryConnection,
    -> SetupRepairTarget.HidConnection
}
