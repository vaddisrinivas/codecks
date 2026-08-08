package io.codecks.commercial.auth

import io.codecks.domain.commercial.AccountDeletionReceipt
import io.codecks.domain.commercial.AccountDeletionResult
import io.codecks.domain.commercial.AccountSession
import io.codecks.domain.commercial.AccountSessionResult
import io.codecks.domain.commercial.AccountSignInResult
import io.codecks.domain.commercial.AccountSignOutResult
import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.BuildCapability
import io.codecks.domain.commercial.CommercialAccountService
import io.codecks.domain.commercial.CommercialDecision
import io.codecks.domain.commercial.CommercialFailureCode
import io.codecks.domain.commercial.CommercialGateState
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.CommercialRetryDirective
import io.codecks.domain.commercial.CommercialSurface
import io.codecks.domain.commercial.Compatibility
import io.codecks.domain.commercial.ConsentState
import io.codecks.domain.commercial.EmergencyDeny
import io.codecks.domain.commercial.EntitlementProjection
import io.codecks.domain.commercial.OwnerActivation
import io.codecks.domain.commercial.RolloutAssignment
import io.codecks.domain.commercial.UserOptIn
import io.codecks.internalcommercial.CommercialTestOverrideMarker
import java.net.URI

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

class AuthNonce private constructor(private val value: String) {
    init {
        require(value.matches(Regex("[A-Za-z0-9._:-]{16,128}")))
    }

    internal fun consume(block: (String) -> Unit) = block(value)
    internal fun matches(other: AuthNonce): Boolean = value == other.value
    override fun toString(): String = "AuthNonce(redacted)"

    companion object {
        fun trusted(value: String): AuthNonce = AuthNonce(value)
    }
}

class EphemeralIdentityToken private constructor(private val value: String) {
    init {
        require(value.length in 16..4096)
    }

    internal fun consume(block: (String) -> Unit) = block(value)
    override fun toString(): String = "EphemeralIdentityToken(redacted)"

    companion object {
        fun ephemeral(value: String): EphemeralIdentityToken = EphemeralIdentityToken(value)
    }
}

class BackendSessionCredential private constructor(private val value: String) {
    init {
        require(value.length in 16..512)
    }

    internal fun consume(block: (String) -> Unit) = block(value)
    internal fun matches(other: BackendSessionCredential): Boolean = value == other.value
    override fun toString(): String = "BackendSessionCredential(redacted)"

    companion object {
        fun verified(value: String): BackendSessionCredential = BackendSessionCredential(value)
    }
}

data class CredentialRequest(
    val operationId: CommercialOperationId,
    val nonce: AuthNonce,
    val reauthenticateAccount: BackendAccountId?,
)

data class CredentialAssertion(
    val nonce: AuthNonce,
    val ephemeralIdToken: EphemeralIdentityToken,
)

sealed interface CredentialBoundaryResult {
    data class Ready(val assertion: CredentialAssertion) : CredentialBoundaryResult
    data object Cancelled : CredentialBoundaryResult
    data object Unavailable : CredentialBoundaryResult
}

/** Generic Credential Manager seam. It contains no email identity or Android SDK type. */
interface CredentialManagerBoundary {
    suspend fun request(request: CredentialRequest): CredentialBoundaryResult
}

data class StoredBackendSession(
    val session: AccountSession,
    val credential: BackendSessionCredential,
)

interface EncryptedSessionStore {
    suspend fun load(): StoredBackendSession?
    suspend fun save(session: StoredBackendSession)
    suspend fun clear()
}

class InMemoryEncryptedSessionStore : EncryptedSessionStore {
    private var stored: StoredBackendSession? = null

    override suspend fun load(): StoredBackendSession? = stored

    override suspend fun save(session: StoredBackendSession) {
        stored = session
    }

    override suspend fun clear() {
        stored = null
    }
}

data class BackendSessionGrant(
    val session: AccountSession,
    val credential: BackendSessionCredential,
)

sealed interface AuthBackendResult<out T> {
    data class Success<T>(val value: T) : AuthBackendResult<T>
    data class Retryable(
        val code: CommercialFailureCode,
        val retryAfterMillis: Long?,
    ) : AuthBackendResult<Nothing>
    data object Rejected : AuthBackendResult<Nothing>
}

interface CommercialAuthTransport {
    suspend fun exchange(
        operationId: CommercialOperationId,
        expectedNonce: AuthNonce,
        assertion: CredentialAssertion,
    ): AuthBackendResult<BackendSessionGrant>

    suspend fun refresh(credential: BackendSessionCredential): AuthBackendResult<BackendSessionGrant>
    suspend fun revokeSession(operationId: CommercialOperationId, credential: BackendSessionCredential): AuthBackendResult<Unit>
    suspend fun reauthenticate(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        expectedNonce: AuthNonce,
        assertion: CredentialAssertion,
    ): AuthBackendResult<Unit>

    suspend fun revokeAllSessions(operationId: CommercialOperationId, accountId: BackendAccountId): AuthBackendResult<Unit>
    suspend fun deleteCloudSnapshots(operationId: CommercialOperationId, accountId: BackendAccountId): AuthBackendResult<Unit>
    suspend fun deleteAccount(operationId: CommercialOperationId, accountId: BackendAccountId): AuthBackendResult<Unit>
}

fun interface AuthNonceIssuer {
    fun issue(operationId: CommercialOperationId): AuthNonce
}

fun interface EpochMillisClock {
    fun now(): Long
}

enum class AccountDeletionPhase {
    NOT_STARTED,
    REAUTHENTICATED,
    SESSIONS_REVOKED,
    SNAPSHOTS_DELETED,
    ACCOUNT_DELETED,
}

data class AccountDeletionProgress(
    val accountId: BackendAccountId,
    val phase: AccountDeletionPhase,
)

interface AccountDeletionProgressStore {
    suspend fun get(operationId: CommercialOperationId): AccountDeletionProgress?
    suspend fun put(operationId: CommercialOperationId, progress: AccountDeletionProgress)
}

class InMemoryAccountDeletionProgressStore : AccountDeletionProgressStore {
    private val progress = mutableMapOf<CommercialOperationId, AccountDeletionProgress>()
    override suspend fun get(operationId: CommercialOperationId): AccountDeletionProgress? = progress[operationId]
    override suspend fun put(operationId: CommercialOperationId, progress: AccountDeletionProgress) {
        this.progress[operationId] = progress
    }
}

fun interface AccountDeletionProgressSink {
    fun onProgress(progress: AccountDeletionProgress)
}

class PlayInternalAccountAdapter(
    val deletionUrl: AccountDeletionUrl,
    private val credentialManagerFactory: () -> CredentialManagerBoundary,
    private val transportFactory: () -> CommercialAuthTransport,
    private val sessionStore: EncryptedSessionStore,
    private val nonceIssuer: AuthNonceIssuer,
    private val deletionProgressStore: AccountDeletionProgressStore,
    private val progressSink: AccountDeletionProgressSink = AccountDeletionProgressSink {},
    private val clock: EpochMillisClock,
) : CommercialAccountService {
    private val policy = CommercialTestOverrideMarker.policyFor(
        CommercialGateState(
            buildCapability = BuildCapability.INTERNAL_TEST_CAPABLE,
            ownerActivation = OwnerActivation.APPROVED,
            compatibility = Compatibility.SUPPORTED,
            emergencyDeny = EmergencyDeny.CLEAR,
            rolloutAssignment = RolloutAssignment.INCLUDED,
            entitlementProjection = EntitlementProjection.NOT_REQUIRED,
            consentState = ConsentState.NOT_REQUIRED,
            userOptIn = UserOptIn.ACCEPTED,
        ),
    )
    private val credentialManager by lazy(credentialManagerFactory)
    private val transport by lazy(transportFactory)

    override suspend fun currentSession(): AccountSessionResult {
        denyReason()?.let { return AccountSessionResult.Unavailable(io.codecks.domain.commercial.CommercialUnavailableCode.PRODUCTION_DARK) }
        val stored = sessionStore.load() ?: return AccountSessionResult.SignedOut
        return when (val refreshed = safeBackend { transport.refresh(stored.credential) }) {
            is AuthBackendResult.Success -> {
                if (!validGrant(refreshed.value, stored.session.accountId, stored)) {
                    sessionStore.clear()
                    return AccountSessionResult.SignedOut
                }
                sessionStore.save(StoredBackendSession(refreshed.value.session, refreshed.value.credential))
                AccountSessionResult.SignedIn(refreshed.value.session)
            }
            AuthBackendResult.Rejected -> {
                sessionStore.clear()
                AccountSessionResult.SignedOut
            }
            is AuthBackendResult.Retryable ->
                AccountSessionResult.Unavailable(io.codecks.domain.commercial.CommercialUnavailableCode.SERVICE_UNAVAILABLE)
        }
    }

    override suspend fun signIn(operationId: CommercialOperationId): AccountSignInResult {
        denyReason()?.let { return AccountSignInResult.Denied(it) }
        val nonce = nonceIssuer.issue(operationId)
        val credentialResult = try {
            credentialManager.request(CredentialRequest(operationId, nonce, null))
        } catch (_: Exception) {
            return retryableSignIn(CommercialFailureCode.UNKNOWN, null)
        }
        val credential = when (val result = credentialResult) {
            is CredentialBoundaryResult.Ready -> result.assertion
            CredentialBoundaryResult.Cancelled -> return AccountSignInResult.Cancelled
            CredentialBoundaryResult.Unavailable -> return AccountSignInResult.Unavailable(
                io.codecks.domain.commercial.CommercialUnavailableCode.NOT_CONFIGURED,
            )
        }
        if (!nonce.matches(credential.nonce)) {
            return retryableSignIn(CommercialFailureCode.REJECTED_BY_SERVER, null)
        }
        return when (val exchanged = safeBackend { transport.exchange(operationId, nonce, credential) }) {
            is AuthBackendResult.Success -> {
                if (!validGrant(exchanged.value, exchanged.value.session.accountId, previous = null)) {
                    return retryableSignIn(CommercialFailureCode.REJECTED_BY_SERVER, null)
                }
                sessionStore.save(StoredBackendSession(exchanged.value.session, exchanged.value.credential))
                AccountSignInResult.SignedIn(exchanged.value.session)
            }
            AuthBackendResult.Rejected -> retryableSignIn(CommercialFailureCode.REJECTED_BY_SERVER, null)
            is AuthBackendResult.Retryable -> retryableSignIn(exchanged.code, exchanged.retryAfterMillis)
        }
    }

    override suspend fun signOut(operationId: CommercialOperationId): AccountSignOutResult {
        denyReason()?.let { return AccountSignOutResult.Denied(it) }
        val stored = sessionStore.load()
        val revoked = stored == null || runCatching {
            transport.revokeSession(operationId, stored.credential) is AuthBackendResult.Success
        }.getOrDefault(false)
        sessionStore.clear()
        if (!revoked) {
            return AccountSignOutResult.Unavailable(io.codecks.domain.commercial.CommercialUnavailableCode.SERVICE_UNAVAILABLE)
        }
        return AccountSignOutResult.SignedOut(operationId)
    }

    override suspend fun deleteAccount(operationId: CommercialOperationId): AccountDeletionResult {
        denyReason()?.let { return AccountDeletionResult.Denied(it) }
        val existingProgress = deletionProgressStore.get(operationId)
        val stored = sessionStore.load()
        if (existingProgress?.phase == AccountDeletionPhase.ACCOUNT_DELETED) {
            if (stored != null && stored.session.accountId != existingProgress.accountId) {
                return retryableDeletion(CommercialFailureCode.ACCOUNT_MISMATCH, null)
            }
            sessionStore.clear()
            return AccountDeletionResult.AlreadyDeleted(operationId)
        }
        val activeSession = stored
            ?: return AccountDeletionResult.RetryableFailure(
                CommercialFailureCode.AUTHENTICATION_REQUIRED,
                CommercialRetryDirective(null),
            )
        var progress = existingProgress
            ?: AccountDeletionProgress(activeSession.session.accountId, AccountDeletionPhase.NOT_STARTED)
        if (progress.accountId != activeSession.session.accountId) {
            return retryableDeletion(CommercialFailureCode.ACCOUNT_MISMATCH, null)
        }
        if (progress.phase < AccountDeletionPhase.REAUTHENTICATED) {
            val nonce = nonceIssuer.issue(operationId)
            val credentialResult = try {
                credentialManager.request(CredentialRequest(operationId, nonce, activeSession.session.accountId))
            } catch (_: Exception) {
                return retryableDeletion(CommercialFailureCode.UNKNOWN, null)
            }
            val assertion = when (val result = credentialResult) {
                is CredentialBoundaryResult.Ready -> result.assertion
                CredentialBoundaryResult.Cancelled,
                CredentialBoundaryResult.Unavailable,
                -> return retryableDeletion(CommercialFailureCode.AUTHENTICATION_REQUIRED, null)
            }
            if (!nonce.matches(assertion.nonce)) {
                return retryableDeletion(CommercialFailureCode.AUTHENTICATION_REQUIRED, null)
            }
            when (
                val result = safeBackend {
                    transport.reauthenticate(operationId, activeSession.session.accountId, nonce, assertion)
                }
            ) {
                is AuthBackendResult.Success -> progress = advance(operationId, progress, AccountDeletionPhase.REAUTHENTICATED)
                AuthBackendResult.Rejected -> return retryableDeletion(CommercialFailureCode.AUTHENTICATION_REQUIRED, null)
                is AuthBackendResult.Retryable -> return retryableDeletion(result.code, result.retryAfterMillis)
            }
        }
        if (progress.phase < AccountDeletionPhase.SESSIONS_REVOKED) {
            when (val result = safeBackend { transport.revokeAllSessions(operationId, progress.accountId) }) {
                is AuthBackendResult.Success -> progress = advance(operationId, progress, AccountDeletionPhase.SESSIONS_REVOKED)
                AuthBackendResult.Rejected -> return retryableDeletion(CommercialFailureCode.REJECTED_BY_SERVER, null)
                is AuthBackendResult.Retryable -> return retryableDeletion(result.code, result.retryAfterMillis)
            }
        }
        if (progress.phase < AccountDeletionPhase.SNAPSHOTS_DELETED) {
            when (val result = safeBackend { transport.deleteCloudSnapshots(operationId, progress.accountId) }) {
                is AuthBackendResult.Success -> progress = advance(operationId, progress, AccountDeletionPhase.SNAPSHOTS_DELETED)
                AuthBackendResult.Rejected -> return retryableDeletion(CommercialFailureCode.REJECTED_BY_SERVER, null)
                is AuthBackendResult.Retryable -> return retryableDeletion(result.code, result.retryAfterMillis)
            }
        }
        if (progress.phase < AccountDeletionPhase.ACCOUNT_DELETED) {
            when (val result = safeBackend { transport.deleteAccount(operationId, progress.accountId) }) {
                is AuthBackendResult.Success -> progress = advance(operationId, progress, AccountDeletionPhase.ACCOUNT_DELETED)
                AuthBackendResult.Rejected -> return retryableDeletion(CommercialFailureCode.REJECTED_BY_SERVER, null)
                is AuthBackendResult.Retryable -> return retryableDeletion(result.code, result.retryAfterMillis)
            }
        }
        sessionStore.clear()
        return AccountDeletionResult.Deleted(
            AccountDeletionReceipt(operationId, clock.now(), sessionsRevokedFirst = true, cloudSnapshotsDeleted = true),
        )
    }

    private suspend fun advance(
        operationId: CommercialOperationId,
        current: AccountDeletionProgress,
        phase: AccountDeletionPhase,
    ): AccountDeletionProgress = AccountDeletionProgress(current.accountId, phase).also {
        deletionProgressStore.put(operationId, it)
        progressSink.onProgress(it)
    }

    private fun denyReason() = when (val decision = policy.decide(CommercialSurface.ACCOUNT)) {
        is CommercialDecision.Allowed -> null
        is CommercialDecision.Denied -> decision.reason
    }

    private fun validGrant(
        grant: BackendSessionGrant,
        expectedAccountId: BackendAccountId,
        previous: StoredBackendSession?,
    ): Boolean {
        if (grant.session.accountId != expectedAccountId) return false
        if (grant.session.expiresAtEpochMillis <= clock.now()) return false
        if (previous == null) return true
        if (grant.session.establishedAtEpochMillis < previous.session.establishedAtEpochMillis) return false
        if (grant.credential.matches(previous.credential)) return false
        return true
    }

    private fun retryableSignIn(code: CommercialFailureCode, retryAfterMillis: Long?) =
        AccountSignInResult.RetryableFailure(code, CommercialRetryDirective(retryAfterMillis))

    private fun retryableDeletion(code: CommercialFailureCode, retryAfterMillis: Long?) =
        AccountDeletionResult.RetryableFailure(code, CommercialRetryDirective(retryAfterMillis))

    private suspend fun <T> safeBackend(block: suspend () -> AuthBackendResult<T>): AuthBackendResult<T> =
        try {
            block()
        } catch (_: Exception) {
            AuthBackendResult.Retryable(CommercialFailureCode.UNKNOWN, null)
        }
}
