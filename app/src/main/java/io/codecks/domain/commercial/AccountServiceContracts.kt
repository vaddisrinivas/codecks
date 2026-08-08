package io.codecks.domain.commercial

data class AccountSession(
    val accountId: BackendAccountId,
    val establishedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(establishedAtEpochMillis >= 0L)
        require(expiresAtEpochMillis >= establishedAtEpochMillis)
    }
}

sealed interface AccountSessionResult {
    data object SignedOut : AccountSessionResult
    data class SignedIn(val session: AccountSession) : AccountSessionResult
    data class Unavailable(val code: CommercialUnavailableCode) : AccountSessionResult
}

sealed interface AccountSignInResult {
    data class SignedIn(val session: AccountSession) : AccountSignInResult
    data object Cancelled : AccountSignInResult
    data class Denied(val reason: CommercialDenyReason) : AccountSignInResult
    data class RetryableFailure(
        val code: CommercialFailureCode,
        val retry: CommercialRetryDirective,
    ) : AccountSignInResult
    data class Unavailable(val code: CommercialUnavailableCode) : AccountSignInResult
}

sealed interface AccountSignOutResult {
    data class SignedOut(val operationId: CommercialOperationId) : AccountSignOutResult
    data class Denied(val reason: CommercialDenyReason) : AccountSignOutResult
    data class Unavailable(val code: CommercialUnavailableCode) : AccountSignOutResult
}

data class AccountDeletionReceipt(
    val operationId: CommercialOperationId,
    val completedAtEpochMillis: Long,
    val sessionsRevokedFirst: Boolean,
    val cloudSnapshotsDeleted: Boolean,
) {
    init {
        require(completedAtEpochMillis >= 0L)
        require(sessionsRevokedFirst) { "Deletion receipt must prove session-first revocation" }
        require(cloudSnapshotsDeleted) { "Deletion receipt must prove cloud snapshot deletion" }
    }
}

sealed interface AccountDeletionResult {
    data class Deleted(val receipt: AccountDeletionReceipt) : AccountDeletionResult
    data class AlreadyDeleted(val operationId: CommercialOperationId) : AccountDeletionResult
    data class Denied(val reason: CommercialDenyReason) : AccountDeletionResult
    data class RetryableFailure(
        val code: CommercialFailureCode,
        val retry: CommercialRetryDirective,
    ) : AccountDeletionResult
    data class Unavailable(val code: CommercialUnavailableCode) : AccountDeletionResult
}

interface CommercialAccountService {
    suspend fun currentSession(): AccountSessionResult
    suspend fun signIn(operationId: CommercialOperationId): AccountSignInResult
    suspend fun signOut(operationId: CommercialOperationId): AccountSignOutResult
    suspend fun deleteAccount(operationId: CommercialOperationId): AccountDeletionResult
}
