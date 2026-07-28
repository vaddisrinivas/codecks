package io.codecks.core.reactive

import io.codecks.data.ConnectionRepository
import io.codecks.domain.reactive.SafeSftpTransferRequest

interface ReactiveSftpTransferClient {
    suspend fun transfer(request: SafeSftpTransferRequest): ReactiveSftpTransferExecution
}

data object UnavailableReactiveSftpTransferClient : ReactiveSftpTransferClient {
    override suspend fun transfer(
        request: SafeSftpTransferRequest,
    ): ReactiveSftpTransferExecution = ReactiveSftpTransferExecution.Unsupported("sftp_transfer_unavailable")
}

class ConnectionRepositoryReactiveSftpTransferClient(
    private val connectionRepository: ConnectionRepository,
) : ReactiveSftpTransferClient {
    override suspend fun transfer(
        request: SafeSftpTransferRequest,
    ): ReactiveSftpTransferExecution =
        connectionRepository.runSftpTransferOnTarget(request.provenance.macId.value, request).fold(
            onSuccess = { ReactiveSftpTransferExecution.Succeeded },
            onFailure = { error -> ReactiveSftpTransferExecution.Failed(error.toSftpFailureCode(), retryable = true) },
        )
}

sealed interface ReactiveSftpTransferExecution {
    data object Succeeded : ReactiveSftpTransferExecution
    data class Failed(val errorCode: String, val retryable: Boolean) : ReactiveSftpTransferExecution
    data class Unsupported(val reasonCode: String) : ReactiveSftpTransferExecution
}

private fun Throwable.toSftpFailureCode(): String = when {
    message?.contains("fingerprint", ignoreCase = true) == true -> "sftp_host_key_unverified"
    message?.contains("missing", ignoreCase = true) == true -> "sftp_file_missing"
    message?.contains("maxBytes", ignoreCase = true) == true -> "sftp_file_too_large"
    message?.contains("Connect", ignoreCase = true) == true -> "sftp_target_not_ready"
    else -> "sftp_transfer_failed"
}
