package io.codecks.commercial.auth

import io.codecks.domain.commercial.AccountDeletionResult
import io.codecks.domain.commercial.AccountSessionResult
import io.codecks.domain.commercial.AccountSignInResult
import io.codecks.domain.commercial.AccountSignOutResult
import io.codecks.domain.commercial.CommercialAccountService
import io.codecks.domain.commercial.CommercialDecision
import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.CommercialSurface
import io.codecks.domain.commercial.CommercialUnavailableCode
import java.net.URI

/** HTTPS-only public account-deletion destination; never carries account data. */
class AccountDeletionUrl private constructor(val value: String) {
    init {
        val parsed = URI(value)
        require(parsed.scheme == "https")
        require(parsed.host?.lowercase() == "codecks.app")
        require(parsed.port == -1 || parsed.port == 443)
        require(parsed.userInfo == null && parsed.query == null && parsed.fragment == null)
    }

    companion object {
        fun https(value: String): AccountDeletionUrl = AccountDeletionUrl(value)
    }
}

/** Generic boundary for a future Credential Manager adapter. */
interface CredentialManagerBoundary

/** Backend auth transport boundary. No implementation is admitted in production-dark. */
interface CommercialAuthTransport

/** Session persistence must be backed by encrypted storage in an activated build. */
interface EncryptedSessionStore

/**
 * Public Play adapter. Dependencies are factories so construction itself is proven unreachable.
 * The immutable compiled policy is the first and only evaluated gate.
 */
class ProductionPlayAccountAdapter(
    val deletionUrl: AccountDeletionUrl,
    private val credentialManagerFactory: () -> CredentialManagerBoundary,
    private val transportFactory: () -> CommercialAuthTransport,
    private val sessionStoreFactory: () -> EncryptedSessionStore,
) : CommercialAccountService {
    private val policy = CommercialExecutionPolicy.PRODUCTION_DARK

    override suspend fun currentSession(): AccountSessionResult {
        policy.decide(CommercialSurface.ACCOUNT)
        return AccountSessionResult.Unavailable(CommercialUnavailableCode.PRODUCTION_DARK)
    }

    override suspend fun signIn(operationId: CommercialOperationId): AccountSignInResult =
        AccountSignInResult.Denied(denialReason())

    override suspend fun signOut(operationId: CommercialOperationId): AccountSignOutResult =
        AccountSignOutResult.Denied(denialReason())

    override suspend fun deleteAccount(operationId: CommercialOperationId): AccountDeletionResult =
        AccountDeletionResult.Denied(denialReason())

    private fun denialReason() =
        (policy.decide(CommercialSurface.ACCOUNT) as CommercialDecision.Denied).reason

    @Suppress("unused")
    private fun unreachableFactories() = listOf(
        credentialManagerFactory,
        transportFactory,
        sessionStoreFactory,
    )
}
