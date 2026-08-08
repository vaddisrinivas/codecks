package io.codecks.commercial.auth

import io.codecks.domain.commercial.AccountDeletionResult
import io.codecks.domain.commercial.AccountSession
import io.codecks.domain.commercial.AccountSessionResult
import io.codecks.domain.commercial.AccountSignInResult
import io.codecks.domain.commercial.AccountSignOutResult
import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CommercialFailureCode
import io.codecks.domain.commercial.CommercialOperationId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayInternalAccountAdapterTest {
    @Test
    fun deletionUrlIsFixedToPublicCodecksHttps() {
        assertEquals(
            "https://codecks.app/account/delete",
            AccountDeletionUrl.https("https://codecks.app/account/delete").value,
        )
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https://localhost/delete") }
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https://example.com/delete") }
        assertThrows(IllegalArgumentException::class.java) { AccountDeletionUrl.https("https://codecks.app:8443/delete") }
    }

    @Test
    fun signInUsesNonceOnceAndNeverPersistsOrPrintsIdentityToken() = runTest {
        val account = BackendAccountId.verified("account-alpha-0001")
        val tokenCanary = "identity-token-SECRET-CANARY"
        val credentials = FakeCredentialManager(tokenCanary)
        val backend = FakeAuthTransport(account)
        val store = InMemoryEncryptedSessionStore()
        val service = service(credentials, backend, store)

        val first = service.signIn(operation("sign-in-first"))
        assertTrue(first is AccountSignInResult.SignedIn)
        val stored = requireNotNull(store.load())
        assertFalse(stored.toString().contains(tokenCanary))
        assertFalse(stored.credential.toString().contains(tokenCanary))
        assertFalse(first.toString().contains(tokenCanary))

        credentials.replayFirstAssertion = true
        val replay = service.signIn(operation("sign-in-replay"))
        assertTrue(replay is AccountSignInResult.RetryableFailure)
        assertEquals(CommercialFailureCode.REJECTED_BY_SERVER, (replay as AccountSignInResult.RetryableFailure).code)
        assertEquals(1, backend.acceptedExchanges)
    }

    @Test
    fun backendExceptionCannotLeakIdentityTokenCanary() = runTest {
        val account = BackendAccountId.verified("account-canary-auth")
        val canary = "identity-token-SECRET-EXCEPTION-CANARY"
        val backend = FakeAuthTransport(account).also { it.exchangeFailureCanary = canary }
        val store = InMemoryEncryptedSessionStore()

        val result = service(FakeCredentialManager(canary), backend, store)
            .signIn(operation("sign-in-canary"))

        assertTrue(result is AccountSignInResult.RetryableFailure)
        assertEquals(CommercialFailureCode.UNKNOWN, (result as AccountSignInResult.RetryableFailure).code)
        assertFalse(result.toString().contains(canary))
        assertNull(store.load())
    }

    @Test
    fun sessionRecoveryRotatesCredentialAndSignOutRevokesThenClears() = runTest {
        val backend = FakeAuthTransport(BackendAccountId.verified("account-recovery-01"))
        val store = InMemoryEncryptedSessionStore()
        val service = service(FakeCredentialManager("identity-token-recovery"), backend, store)
        service.signIn(operation("sign-in-recovery"))
        val beforeCredential = requireNotNull(store.load()).credential

        val recovered = service.currentSession()
        assertTrue(recovered is AccountSessionResult.SignedIn)
        val afterCredential = requireNotNull(store.load()).credential
        var before = ""
        var after = ""
        beforeCredential.consume { before = it }
        afterCredential.consume { after = it }
        assertFalse(before == after)
        assertEquals(1, backend.refreshes)

        assertTrue(service.signOut(operation("sign-out-recovery")) is AccountSignOutResult.SignedOut)
        assertEquals(1, backend.sessionRevocations)
        assertNull(store.load())
    }

    @Test
    fun sessionRecoveryRejectsBackendAccountRotation() = runTest {
        val account = BackendAccountId.verified("account-stable-0001")
        val backend = FakeAuthTransport(account)
        val store = InMemoryEncryptedSessionStore()
        val service = service(FakeCredentialManager("identity-token-stable"), backend, store)
        service.signIn(operation("sign-in-stable"))
        backend.refreshAccountOverride = BackendAccountId.verified("account-injected-01")

        assertTrue(service.currentSession() is AccountSessionResult.SignedOut)
        assertNull(store.load())
    }

    @Test
    fun sessionRecoveryRejectsExpiredRegressedOrUnrotatedGrants() = runTest {
        suspend fun rejected(configure: FakeAuthTransport.() -> Unit) {
            val backend = FakeAuthTransport(BackendAccountId.verified("account-invalid-001"))
            val store = InMemoryEncryptedSessionStore()
            val service = service(FakeCredentialManager("identity-token-invalid"), backend, store)
            service.signIn(operation("sign-in-invalid"))
            backend.configure()

            assertTrue(service.currentSession() is AccountSessionResult.SignedOut)
            assertNull(store.load())
        }

        rejected { expireRefreshGrant = true }
        rejected { regressRefreshGrant = true }
        rejected { reuseCredentialOnRefresh = true }

        val backend = FakeAuthTransport(BackendAccountId.verified("account-expired-signin"))
            .also { it.expireSignInGrant = true }
        val store = InMemoryEncryptedSessionStore()
        val signIn = service(FakeCredentialManager("identity-token-expired"), backend, store)
            .signIn(operation("sign-in-expired"))
        assertTrue(signIn is AccountSignInResult.RetryableFailure)
        assertNull(store.load())
    }

    @Test
    fun failedOrThrowingRemoteSignOutStillClearsLocalSession() = runTest {
        listOf(false, true).forEach { throws ->
            val backend = FakeAuthTransport(BackendAccountId.verified("account-signout-01"))
            val store = InMemoryEncryptedSessionStore()
            val service = service(FakeCredentialManager("identity-token-signout"), backend, store)
            service.signIn(operation("sign-in-signout"))
            backend.failSessionRevocation = !throws
            backend.throwSessionRevocation = throws

            assertTrue(service.signOut(operation("sign-out-failed")) is AccountSignOutResult.Unavailable)
            assertNull(store.load())
        }
    }

    @Test
    fun deletionReauthenticatesRevokesFirstRetriesAndResumesWithoutRepeatingStages() = runTest {
        val backend = FakeAuthTransport(BackendAccountId.verified("account-delete-0001"))
        backend.failSnapshotDeletionOnce = true
        val credentials = FakeCredentialManager("identity-token-delete")
        val store = InMemoryEncryptedSessionStore()
        val progressStore = InMemoryAccountDeletionProgressStore()
        val phases = mutableListOf<AccountDeletionPhase>()
        val service = service(credentials, backend, store, progressStore, phases)
        service.signIn(operation("sign-in-delete"))
        val deletionOperation = operation("delete-account-01")

        val first = service.deleteAccount(deletionOperation)
        assertTrue(first is AccountDeletionResult.RetryableFailure)
        assertEquals(listOf(AccountDeletionPhase.REAUTHENTICATED, AccountDeletionPhase.SESSIONS_REVOKED), phases)
        assertEquals(1, backend.reauthentications)
        assertEquals(1, backend.allSessionRevocations)
        assertEquals(1, backend.snapshotDeletionAttempts)

        val retry = service.deleteAccount(deletionOperation)
        assertTrue(retry is AccountDeletionResult.Deleted)
        assertEquals(1, backend.reauthentications)
        assertEquals(1, backend.allSessionRevocations)
        assertEquals(2, backend.snapshotDeletionAttempts)
        assertEquals(1, backend.accountDeletions)
        assertNull(store.load())
        val receipt = (retry as AccountDeletionResult.Deleted).receipt
        assertTrue(receipt.sessionsRevokedFirst)
        assertTrue(receipt.cloudSnapshotsDeleted)
        assertTrue(service.deleteAccount(deletionOperation) is AccountDeletionResult.AlreadyDeleted)
    }

    @Test
    fun operationCannotResumeDeletionForAnotherAccount() = runTest {
        val progress = InMemoryAccountDeletionProgressStore()
        val operation = operation("delete-account-bound")
        progress.put(
            operation,
            AccountDeletionProgress(BackendAccountId.verified("account-original-01"), AccountDeletionPhase.REAUTHENTICATED),
        )
        val store = InMemoryEncryptedSessionStore().also {
            it.save(
                StoredBackendSession(
                    AccountSession(BackendAccountId.verified("account-other-0001"), 1, 2),
                    BackendSessionCredential.verified("backend-session-other"),
                ),
            )
        }
        val result = service(
            FakeCredentialManager("identity-token-other"),
            FakeAuthTransport(BackendAccountId.verified("account-other-0001")),
            store,
            progress,
        ).deleteAccount(operation)
        assertTrue(result is AccountDeletionResult.RetryableFailure)
        assertEquals(CommercialFailureCode.ACCOUNT_MISMATCH, (result as AccountDeletionResult.RetryableFailure).code)
    }

    @Test
    fun completedDeletionOperationCannotClearAnotherAccountsSession() = runTest {
        val progress = InMemoryAccountDeletionProgressStore()
        val operation = operation("delete-complete-bound")
        progress.put(
            operation,
            AccountDeletionProgress(BackendAccountId.verified("account-deleted-001"), AccountDeletionPhase.ACCOUNT_DELETED),
        )
        val otherAccount = BackendAccountId.verified("account-current-001")
        val store = InMemoryEncryptedSessionStore().also {
            it.save(
                StoredBackendSession(
                    AccountSession(otherAccount, 5_001, 10_001),
                    BackendSessionCredential.verified("backend-session-current"),
                ),
            )
        }

        val result = service(
            FakeCredentialManager("identity-token-current"),
            FakeAuthTransport(otherAccount),
            store,
            progress,
        ).deleteAccount(operation)

        assertTrue(result is AccountDeletionResult.RetryableFailure)
        assertEquals(CommercialFailureCode.ACCOUNT_MISMATCH, (result as AccountDeletionResult.RetryableFailure).code)
        assertTrue(store.load() != null)
    }

    private fun service(
        credentials: FakeCredentialManager,
        backend: FakeAuthTransport,
        store: InMemoryEncryptedSessionStore,
        progress: InMemoryAccountDeletionProgressStore = InMemoryAccountDeletionProgressStore(),
        phases: MutableList<AccountDeletionPhase> = mutableListOf(),
    ): PlayInternalAccountAdapter {
        var nonce = 0
        return PlayInternalAccountAdapter(
            deletionUrl = AccountDeletionUrl.https("https://codecks.app/account/delete"),
            credentialManagerFactory = { credentials },
            transportFactory = { backend },
            sessionStore = store,
            nonceIssuer = AuthNonceIssuer { AuthNonce.trusted("nonce-${++nonce}-1234567890") },
            deletionProgressStore = progress,
            progressSink = AccountDeletionProgressSink { phases += it.phase },
            clock = EpochMillisClock { 5_000L },
        )
    }

    private fun operation(suffix: String) = CommercialOperationId.trusted("operation-$suffix")
}

private class FakeCredentialManager(private val token: String) : CredentialManagerBoundary {
    var replayFirstAssertion = false
    private var first: CredentialAssertion? = null

    override suspend fun request(request: CredentialRequest): CredentialBoundaryResult {
        val assertion = if (replayFirstAssertion && first != null) {
            requireNotNull(first)
        } else {
            CredentialAssertion(request.nonce, EphemeralIdentityToken.ephemeral(token)).also {
                if (first == null) first = it
            }
        }
        return CredentialBoundaryResult.Ready(assertion)
    }
}

private class FakeAuthTransport(private val accountId: BackendAccountId) : CommercialAuthTransport {
    private val consumedNonces = mutableSetOf<String>()
    private var credentialRevision = 0
    var acceptedExchanges = 0
    var refreshes = 0
    var sessionRevocations = 0
    var reauthentications = 0
    var allSessionRevocations = 0
    var snapshotDeletionAttempts = 0
    var accountDeletions = 0
    var failSnapshotDeletionOnce = false
    var refreshAccountOverride: BackendAccountId? = null
    var failSessionRevocation = false
    var throwSessionRevocation = false
    var expireRefreshGrant = false
    var expireSignInGrant = false
    var regressRefreshGrant = false
    var reuseCredentialOnRefresh = false
    var exchangeFailureCanary: String? = null
    private var lastCredential: BackendSessionCredential? = null

    override suspend fun exchange(
        operationId: CommercialOperationId,
        expectedNonce: AuthNonce,
        assertion: CredentialAssertion,
    ): AuthBackendResult<BackendSessionGrant> {
        exchangeFailureCanary?.let { error(it) }
        if (!consumeMatchingNonce(expectedNonce, assertion.nonce)) return AuthBackendResult.Rejected
        acceptedExchanges++
        return AuthBackendResult.Success(grant())
    }

    override suspend fun refresh(credential: BackendSessionCredential): AuthBackendResult<BackendSessionGrant> {
        refreshes++
        return AuthBackendResult.Success(grant(refreshAccountOverride ?: accountId, isRefresh = true))
    }

    override suspend fun revokeSession(
        operationId: CommercialOperationId,
        credential: BackendSessionCredential,
    ): AuthBackendResult<Unit> {
        sessionRevocations++
        if (throwSessionRevocation) error("revocation transport failure")
        if (failSessionRevocation) return AuthBackendResult.Rejected
        return AuthBackendResult.Success(Unit)
    }

    override suspend fun reauthenticate(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        expectedNonce: AuthNonce,
        assertion: CredentialAssertion,
    ): AuthBackendResult<Unit> {
        if (accountId != this.accountId || !consumeMatchingNonce(expectedNonce, assertion.nonce)) {
            return AuthBackendResult.Rejected
        }
        reauthentications++
        return AuthBackendResult.Success(Unit)
    }

    override suspend fun revokeAllSessions(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): AuthBackendResult<Unit> {
        allSessionRevocations++
        return AuthBackendResult.Success(Unit)
    }

    override suspend fun deleteCloudSnapshots(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): AuthBackendResult<Unit> {
        snapshotDeletionAttempts++
        if (failSnapshotDeletionOnce) {
            failSnapshotDeletionOnce = false
            return AuthBackendResult.Retryable(CommercialFailureCode.TIMEOUT, 10)
        }
        return AuthBackendResult.Success(Unit)
    }

    override suspend fun deleteAccount(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): AuthBackendResult<Unit> {
        accountDeletions++
        return AuthBackendResult.Success(Unit)
    }

    private fun grant(grantAccount: BackendAccountId = accountId, isRefresh: Boolean = false): BackendSessionGrant {
        credentialRevision++
        val credential = if (isRefresh && reuseCredentialOnRefresh) {
            requireNotNull(lastCredential)
        } else {
            BackendSessionCredential.verified("backend-session-${credentialRevision.toString().padStart(4, '0')}")
        }
        lastCredential = credential
        val establishedAt = when {
            (isRefresh && expireRefreshGrant) || (!isRefresh && expireSignInGrant) -> 4_000L
            isRefresh && regressRefreshGrant -> 1L
            else -> 5_000L + credentialRevision
        }
        val expiresAt = if (
            (isRefresh && expireRefreshGrant) || (!isRefresh && expireSignInGrant)
        ) {
            5_000L
        } else {
            10_000L + credentialRevision
        }
        return BackendSessionGrant(
            AccountSession(grantAccount, establishedAt, expiresAt),
            credential,
        )
    }

    private fun consumeMatchingNonce(expected: AuthNonce, actual: AuthNonce): Boolean {
        var expectedValue = ""
        var actualValue = ""
        expected.consume { expectedValue = it }
        actual.consume { actualValue = it }
        return expectedValue == actualValue && consumedNonces.add(actualValue)
    }
}
