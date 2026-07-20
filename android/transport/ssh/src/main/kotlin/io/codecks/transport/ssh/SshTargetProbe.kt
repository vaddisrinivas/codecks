package io.codecks.transport.ssh

import io.codecks.domain.actions.TimeoutPolicy
import io.codecks.domain.targets.LiveState
import io.codecks.domain.targets.MacTarget
import io.codecks.domain.targets.TrustState

class SshTargetProbe(
    private val credentialProvider: SshCredentialProvider,
    private val transport: SshCommandTransport = SshjCommandTransport(),
) {
    suspend fun probe(target: MacTarget): LiveState {
        if (target.sshIdentity == null) return LiveState.CONFIGURED
        if (target.trustState == TrustState.HOST_KEY_CHANGED) return LiveState.HOST_KEY_CHANGED
        if (target.trustState != TrustState.TRUSTED) return LiveState.CONFIGURED
        val credential = credentialProvider.privateKeyFor(target) ?: return LiveState.AUTH_FAILED
        return when (
            val result = transport.execute(
                SshCommandRequest(
                    target = target,
                    command = "printf codecks-ready",
                    credential = credential,
                    timeoutPolicy = TimeoutPolicy(runTimeoutMillis = 5_000, outputLimitBytes = 1_024),
                ),
            )
        ) {
            is SshCommandResult.Completed -> if (result.exitStatus == 0) LiveState.ONLINE else LiveState.DEGRADED
            is SshCommandResult.Failed -> result.toLiveState()
        }
    }

    private fun SshCommandResult.Failed.toLiveState(): LiveState = when (kind) {
        SshFailureKind.MISSING_HOST_FINGERPRINT,
        SshFailureKind.HOST_KEY_CHANGED,
        -> LiveState.HOST_KEY_CHANGED
        SshFailureKind.MISSING_IDENTITY,
        SshFailureKind.AUTH_FAILED,
        -> LiveState.AUTH_FAILED
        SshFailureKind.TIMEOUT,
        SshFailureKind.CONNECTION_FAILED,
        -> LiveState.OFFLINE
        SshFailureKind.COMMAND_UNSUPPORTED -> LiveState.DEGRADED
    }
}
