package io.codecks.transport.ssh

import io.codecks.core.common.CodecksClock
import io.codecks.core.common.SystemCodecksClock
import io.codecks.domain.actions.ActionPlan
import io.codecks.domain.actions.ActionReceipt
import io.codecks.domain.actions.MacActionExecutor
import io.codecks.domain.actions.ReceiptState
import io.codecks.domain.actions.RepairHint
import io.codecks.domain.targets.MacTarget

class SshMacActionExecutor(
    private val credentialProvider: SshCredentialProvider,
    private val transport: SshCommandTransport = SshjCommandTransport(),
    private val commandCatalog: SshCommandCatalog = CoreSshCommandCatalog,
    private val clock: CodecksClock = SystemCodecksClock,
) : MacActionExecutor {
    override suspend fun execute(
        plan: ActionPlan,
        target: MacTarget,
        startedAtEpochMillis: Long,
    ): ActionReceipt {
        val command = commandCatalog.commandFor(plan)
            ?: return failedReceipt(plan, target, startedAtEpochMillis, SshFailureKind.COMMAND_UNSUPPORTED, "Unsupported Mac action.")
        val credential = credentialProvider.privateKeyFor(target)
            ?: return failedReceipt(plan, target, startedAtEpochMillis, SshFailureKind.MISSING_IDENTITY, "SSH key is unavailable.")
        return when (
            val result = transport.execute(
                SshCommandRequest(
                    target = target,
                    command = command,
                    credential = credential,
                    timeoutPolicy = plan.steps.single().timeoutPolicy,
                ),
            )
        ) {
            is SshCommandResult.Completed -> completedReceipt(plan, target, startedAtEpochMillis, result)
            is SshCommandResult.Failed -> failedReceipt(plan, target, startedAtEpochMillis, result.kind, result.safeMessage)
        }
    }

    private fun completedReceipt(
        plan: ActionPlan,
        target: MacTarget,
        startedAtEpochMillis: Long,
        result: SshCommandResult.Completed,
    ): ActionReceipt {
        val actionTitle = plan.steps.single().safeSummary
        val success = result.exitStatus == 0
        return ActionReceipt(
            invocationId = plan.invocationId,
            state = if (success) ReceiptState.SUCCESS else ReceiptState.FAILURE,
            targetId = target.logicalId,
            startedAtEpochMillis = startedAtEpochMillis,
            endedAtEpochMillis = startedAtEpochMillis + result.durationMillis,
            safeSummary = buildString {
                append(actionTitle)
                append(if (success) " succeeded" else " failed")
                append(" on ")
                append(target.displayName)
                result.exitStatus?.let { append(" (exit $it)") }
                if (result.outputTruncated) append("; output truncated")
            },
            repair = if (success) null else RepairHint(
                title = "Check macOS permissions for this action.",
                actionLabel = "Repair connection",
            ),
        )
    }

    private fun failedReceipt(
        plan: ActionPlan,
        target: MacTarget,
        startedAtEpochMillis: Long,
        kind: SshFailureKind,
        safeMessage: String,
    ): ActionReceipt = ActionReceipt(
        invocationId = plan.invocationId,
        state = when (kind) {
            SshFailureKind.MISSING_IDENTITY,
            SshFailureKind.MISSING_HOST_FINGERPRINT,
            SshFailureKind.HOST_KEY_CHANGED,
            SshFailureKind.AUTH_FAILED,
            SshFailureKind.TIMEOUT,
            SshFailureKind.CONNECTION_FAILED,
            SshFailureKind.COMMAND_UNSUPPORTED,
            -> ReceiptState.UNAVAILABLE
        },
        targetId = target.logicalId,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = clock.nowEpochMillis(),
        safeSummary = "${plan.steps.single().safeSummary} unavailable on ${target.displayName}: $safeMessage",
        repair = RepairHint(
            title = repairTitle(kind),
            actionLabel = "Repair connection",
        ),
    )

    private fun repairTitle(kind: SshFailureKind): String = when (kind) {
        SshFailureKind.MISSING_IDENTITY -> "Create or restore the phone SSH key."
        SshFailureKind.MISSING_HOST_FINGERPRINT -> "Trust the Mac host fingerprint before connecting."
        SshFailureKind.HOST_KEY_CHANGED -> "Re-check the Mac host fingerprint before continuing."
        SshFailureKind.AUTH_FAILED -> "Reinstall the phone public key on the Mac."
        SshFailureKind.TIMEOUT -> "Confirm the Mac is awake and reachable on the local network."
        SshFailureKind.CONNECTION_FAILED -> "Check the Mac address, port, and local network."
        SshFailureKind.COMMAND_UNSUPPORTED -> "Update the Mac action catalog."
    }
}
