package io.codecks.transport.ssh

import io.codecks.domain.actions.TimeoutPolicy
import io.codecks.domain.targets.MacTarget

data class SshCommandRequest(
    val target: MacTarget,
    val command: String,
    val credential: SshPrivateKeyCredential,
    val timeoutPolicy: TimeoutPolicy,
)

sealed interface SshCommandResult {
    data class Completed(
        val exitStatus: Int?,
        val stdout: String,
        val stderr: String,
        val durationMillis: Long,
        val outputTruncated: Boolean = false,
    ) : SshCommandResult

    data class Failed(
        val kind: SshFailureKind,
        val safeMessage: String,
    ) : SshCommandResult
}

enum class SshFailureKind {
    MISSING_IDENTITY,
    MISSING_HOST_FINGERPRINT,
    HOST_KEY_CHANGED,
    AUTH_FAILED,
    TIMEOUT,
    CONNECTION_FAILED,
    COMMAND_UNSUPPORTED,
}

interface SshCommandTransport {
    suspend fun execute(request: SshCommandRequest): SshCommandResult
}
