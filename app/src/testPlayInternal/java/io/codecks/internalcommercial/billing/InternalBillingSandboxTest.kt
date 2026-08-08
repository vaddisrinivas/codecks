package io.codecks.internalcommercial.billing

import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CanonicalRequestDigest
import io.codecks.domain.commercial.CommercialFailureCode
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.EntitlementRefreshResult
import io.codecks.domain.commercial.EntitlementStateResult
import io.codecks.domain.commercial.IntegrityEvidenceHandle
import io.codecks.domain.commercial.PaidCapability
import io.codecks.domain.commercial.ProductCatalogId
import io.codecks.domain.commercial.PurchaseLaunchRequest
import io.codecks.domain.commercial.PurchaseLaunchResult
import io.codecks.domain.commercial.PurchaseServerLifecycleState
import io.codecks.domain.commercial.PurchaseVerificationRequest
import io.codecks.domain.commercial.PurchaseVerificationResult
import io.codecks.domain.commercial.TransactionIntegrityResult
import io.codecks.domain.commercial.TransactionIntegrityEvidence
import io.codecks.internalcommercial.entitlement.InternalBackendEntitlement
import io.codecks.internalcommercial.entitlement.InternalEntitlementSandbox
import io.codecks.internalcommercial.integrity.InternalIntegritySandbox
import io.codecks.internalcommercial.integrity.InternalIntegrityContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalBillingSandboxTest {
    private var now = 1_000L
    private val accountA = BackendAccountId.verified("account-0001")
    private val accountB = BackendAccountId.verified("account-0002")
    private val product = ProductCatalogId("codecks.pro")
    private val digest = CanonicalRequestDigest("a".repeat(64))

    @Test
    fun constructionIsLazyAndCatalogIsTyped() = runBlocking {
        val fixture = fixture()

        assertEquals(0, fixture.backend.fetchCalls)
        assertEquals(0, fixture.backend.verifyCalls)
        assertEquals(0, fixture.play.launchCalls)
        assertTrue(fixture.billing.queryCatalog() is InternalCatalogQueryResult.Available)
        assertEquals(0, fixture.backend.fetchCalls)
        assertEquals(0, fixture.backend.verifyCalls)
        assertEquals(0, fixture.play.launchCalls)
    }

    @Test
    fun launchPendingAndBackendVerificationNeverGrantLocally() = runBlocking {
        val fixture = fixture()
        val operation = operation("operation-0001")
        val attempt = launch(fixture, operation, accountA)

        val verified = fixture.billing.verify(verification(fixture, operation, accountA, attempt))
        assertTrue(verified is PurchaseVerificationResult.ServerStateRecorded)
        assertTrue(fixture.billing.isAcknowledged(attempt))
        assertEquals(listOf("verify", "ack", "recordAcknowledged"), fixture.events)
        assertTrue(fixture.entitlements.cached(accountA) is EntitlementStateResult.Missing)

        val refreshed = fixture.billing.refreshEntitlements(operation, accountA)
        assertTrue(refreshed is EntitlementRefreshResult.Refreshed)
        val cached = fixture.entitlements.cached(accountA) as EntitlementStateResult.Cached
        assertTrue(cached.state.allows(PaidCapability.SYNC))
    }

    @Test
    fun playPendingNeverCallsBackendAcknowledgesOrGrants() = runBlocking {
        val fixture = fixture()
        fixture.play.pending = true
        val operation = operation("operation-play-pending")
        val launch = fixture.billing.launch(PurchaseLaunchRequest(operation, accountA, product))
            as PurchaseLaunchResult.Pending

        assertEquals(
            PurchaseVerificationResult.Pending(operation),
            fixture.billing.verify(verification(fixture, operation, accountA, launch.attempt)),
        )
        assertEquals(0, fixture.backend.verifyCalls)
        assertEquals(0, fixture.backend.recordAcknowledgedCalls)
        assertTrue(fixture.entitlements.cached(accountA) is EntitlementStateResult.Missing)
    }

    @Test
    fun pendingCallbackClearsPreviouslyObservedToken() = runBlocking {
        val fixture = fixture()
        val operation = operation("operation-callback-pending")
        val attempt = launch(fixture, operation, accountA)

        assertEquals(PurchaseLaunchResult.Pending(attempt), fixture.billing.markPending(attempt))
        assertEquals(
            PurchaseVerificationResult.Pending(operation),
            fixture.billing.verify(verification(fixture, operation, accountA, attempt)),
        )
        assertEquals(0, fixture.backend.verifyCalls)
    }

    @Test
    fun acknowledgementFailureDoesNotGrantAndCanRetryWithFreshIntegrity() = runBlocking {
        val fixture = fixture()
        fixture.play.acknowledgement = InternalAcknowledgementResult.Retryable(CommercialFailureCode.TIMEOUT)
        val operation = operation("operation-ack-retry")
        val attempt = launch(fixture, operation, accountA)

        assertTrue(
            fixture.billing.verify(verification(fixture, operation, accountA, attempt))
                is PurchaseVerificationResult.RetryableFailure,
        )
        assertFalse(fixture.billing.isAcknowledged(attempt))
        assertEquals(listOf("verify", "ack"), fixture.events)
        assertEquals(0, fixture.backend.recordAcknowledgedCalls)
        assertTrue(fixture.entitlements.cached(accountA) is EntitlementStateResult.Missing)

        fixture.play.acknowledgement = InternalAcknowledgementResult.Acknowledged
        assertTrue(
            fixture.billing.verify(verification(fixture, operation, accountA, attempt))
                is PurchaseVerificationResult.ServerStateRecorded,
        )
        assertTrue(fixture.billing.isAcknowledged(attempt))
        assertEquals(1, fixture.backend.recordAcknowledgedCalls)
    }

    @Test
    fun backendPendingNeverAcknowledgesOrGrantsLocally() = runBlocking {
        val fixture = fixture(lifecycle = PurchaseServerLifecycleState.PENDING)
        val operation = operation("operation-pending")
        val attempt = launch(fixture, operation, accountA)

        assertEquals(
            PurchaseVerificationResult.Pending(operation),
            fixture.billing.verify(verification(fixture, operation, accountA, attempt)),
        )
        assertFalse(fixture.billing.isAcknowledged(attempt))
        assertTrue(fixture.entitlements.cached(accountA) is EntitlementStateResult.Missing)
    }

    @Test
    fun wrongAccountAndDuplicateIntegrityCallbackFailClosed() = runBlocking {
        val fixture = fixture()
        val operation = operation("operation-0002")
        val attempt = launch(fixture, operation, accountA)
        val evidence = collect(fixture, operation, accountA)
        val wrong = fixture.billing.verify(
            PurchaseVerificationRequest(operation, accountB, attempt, evidence),
        )
        assertEquals(
            PurchaseVerificationResult.Rejected(CommercialFailureCode.ACCOUNT_MISMATCH),
            wrong,
        )

        val secondFixture = fixture()
        val secondAttempt = launch(secondFixture, operation, accountA)
        val secondEvidence = collect(secondFixture, operation, accountA)
        assertTrue(secondFixture.billing.verify(verification(secondFixture, operation, accountA, secondAttempt, secondEvidence))
            is PurchaseVerificationResult.ServerStateRecorded)
        assertEquals(
            PurchaseVerificationResult.Rejected(CommercialFailureCode.REJECTED_BY_SERVER),
            secondFixture.billing.verify(verification(secondFixture, operation, accountA, secondAttempt, secondEvidence)),
        )

        val forgedFixture = fixture()
        val forgedAttempt = launch(forgedFixture, operation, accountA)
        val forgedEvidence = TransactionIntegrityEvidence(
            operationId = operation,
            requestDigest = digest,
            evidence = IntegrityEvidenceHandle.trusted("forged-evidence-0001"),
            expiresAtEpochMillis = now + 60_000L,
        )
        assertEquals(
            PurchaseVerificationResult.Rejected(CommercialFailureCode.REJECTED_BY_SERVER),
            forgedFixture.billing.verify(
                PurchaseVerificationRequest(operation, accountA, forgedAttempt, forgedEvidence),
            ),
        )
        assertEquals(0, forgedFixture.backend.verifyCalls)
    }

    @Test
    fun expiredIntegrityAndOfflineCacheStayAdvisory() = runBlocking {
        val fixture = fixture()
        val operation = operation("operation-0003")
        val attempt = launch(fixture, operation, accountA)
        val evidence = collect(fixture, operation, accountA)
        now += 60_001L
        assertEquals(
            PurchaseVerificationResult.Rejected(CommercialFailureCode.TIMEOUT),
            fixture.billing.verify(verification(fixture, operation, accountA, attempt, evidence)),
        )

        now = 1_000L
        val online = fixture()
        val onlineAttempt = launch(online, operation, accountA)
        assertTrue(online.billing.verify(verification(online, operation, accountA, onlineAttempt))
            is PurchaseVerificationResult.ServerStateRecorded)
        assertTrue(online.billing.refreshEntitlements(operation, accountA) is EntitlementRefreshResult.Refreshed)
        online.billing.setOnline(false)
        assertTrue(online.entitlements.cached(accountA) is EntitlementStateResult.Cached)
        assertTrue(online.billing.refreshEntitlements(operation, accountA) is EntitlementRefreshResult.RetryableFailure)
    }

    @Test
    fun integrityExpiresAtBoundaryAndIsBoundToOperationAccountAndRequest() = runBlocking {
        val operation = operation("operation-integrity")

        val expired = fixture()
        val expiredAttempt = launch(expired, operation, accountA)
        val expiredEvidence = collect(expired, operation, accountA)
        now += 60_000L
        assertEquals(
            PurchaseVerificationResult.Rejected(CommercialFailureCode.TIMEOUT),
            expired.billing.verify(verification(expired, operation, accountA, expiredAttempt, expiredEvidence)),
        )

        now = 1_000L
        val wrongOperation = fixture()
        launch(wrongOperation, operation, accountA)
        val evidence = collect(wrongOperation, operation, accountA)
        assertEquals(
            CommercialFailureCode.REJECTED_BY_SERVER,
            wrongOperation.integrity.consume(operation("operation-other"), accountA, evidence),
        )

        val wrongAccount = fixture()
        launch(wrongAccount, operation, accountA)
        val accountEvidence = collect(wrongAccount, operation, accountA)
        assertEquals(
            CommercialFailureCode.ACCOUNT_MISMATCH,
            wrongAccount.integrity.consume(operation, accountB, accountEvidence),
        )

        val wrongDigest = fixture()
        val wrongDigestAttempt = launch(wrongDigest, operation, accountA)
        val valid = collect(wrongDigest, operation, accountA)
        val replaced = TransactionIntegrityEvidence(
            operationId = valid.operationId,
            requestDigest = CanonicalRequestDigest("b".repeat(64)),
            evidence = valid.evidence,
            expiresAtEpochMillis = valid.expiresAtEpochMillis,
        )
        assertEquals(
            PurchaseVerificationResult.Rejected(CommercialFailureCode.REJECTED_BY_SERVER),
            wrongDigest.billing.verify(verification(wrongDigest, operation, accountA, wrongDigestAttempt, replaced)),
        )

        now = 60_000L
        val stale = fixture()
        assertTrue(
            stale.integrity.collect(
                InternalIntegrityContext(operation, accountA, digest, requestedAtEpochMillis = 0L),
            ) is TransactionIntegrityResult.Failed,
        )
    }

    @Test
    fun cacheRejectsExactExpiryAccountMismatchAndRevisionRollback() = runBlocking {
        val fixture = fixture(lifecycle = PurchaseServerLifecycleState.ACTIVE)
        val operation = operation("operation-cache-guards")

        assertTrue(fixture.billing.refreshEntitlements(operation, accountA) is EntitlementRefreshResult.Refreshed)
        now += 120_000L
        assertTrue(fixture.entitlements.cached(accountA) is EntitlementStateResult.Missing)

        now = 1_000L
        fixture.backend.revision = 2L
        assertTrue(fixture.billing.refreshEntitlements(operation, accountA) is EntitlementRefreshResult.Refreshed)
        fixture.backend.revision = 1L
        assertEquals(
            CommercialFailureCode.INVALID_STATE,
            (fixture.billing.refreshEntitlements(operation, accountA) as EntitlementRefreshResult.RetryableFailure).code,
        )
        assertTrue(fixture.entitlements.cached(accountA) is EntitlementStateResult.Missing)

        fixture.backend.revision = 3L
        assertTrue(fixture.billing.refreshEntitlements(operation, accountA) is EntitlementRefreshResult.Refreshed)
        fixture.backend.responseAccount = accountB
        assertEquals(
            CommercialFailureCode.ACCOUNT_MISMATCH,
            (fixture.billing.refreshEntitlements(operation, accountA) as EntitlementRefreshResult.RetryableFailure).code,
        )
        assertTrue(fixture.entitlements.cached(accountA) is EntitlementStateResult.Missing)
    }

    @Test
    fun purchaseTokenIsAccountBoundAndRedactedFromDiagnostics() = runBlocking {
        val fixture = fixture()
        val operation = operation("operation-token-bound")
        val attempt = launch(fixture, operation, accountA)

        assertTrue(
            fixture.billing.verify(verification(fixture, operation, accountA, attempt))
                is PurchaseVerificationResult.ServerStateRecorded,
        )
        assertEquals(accountA, fixture.play.lastAcknowledgedAccount)
        assertEquals(fixture.play.lastLaunchToken, fixture.play.lastAcknowledgedToken)
        assertEquals("InternalPurchaseToken(redacted)", fixture.play.lastLaunchToken.toString())
        assertFalse(fixture.play.lastLaunchToken.toString().contains("secret-token"))
    }

    @Test
    fun refundRevokeRestoreAndAccountSwitchClearState() = runBlocking {
        val fixture = fixture(lifecycle = PurchaseServerLifecycleState.ACTIVE)
        val operation = operation("operation-0004")
        val attempt = launch(fixture, operation, accountA)
        assertTrue(fixture.billing.verify(verification(fixture, operation, accountA, attempt))
            is PurchaseVerificationResult.ServerStateRecorded)
        assertTrue(fixture.billing.refreshEntitlements(operation, accountA) is EntitlementRefreshResult.Refreshed)

        fixture.backend.lifecycle = PurchaseServerLifecycleState.REFUNDED
        assertTrue(fixture.billing.restore(operation, accountA) is PurchaseVerificationResult.ServerStateRecorded)
        val refunded = fixture.billing.refreshEntitlements(operation, accountA) as EntitlementRefreshResult.Refreshed
        assertFalse(refunded.state.allows(PaidCapability.SYNC))

        fixture.backend.lifecycle = PurchaseServerLifecycleState.REVOKED
        assertTrue(fixture.billing.reconcile(operation, accountA) is PurchaseVerificationResult.ServerStateRecorded)
        fixture.billing.onAccountChanged(accountB)
        assertTrue(fixture.entitlements.cached(accountA) is EntitlementStateResult.Missing)
    }

    @Test
    fun unknownCatalogAndOfflineManageDoNotStartAClient() = runBlocking {
        val fixture = fixture()
        val invalid = fixture.billing.launch(
            PurchaseLaunchRequest(operation("operation-0005"), accountA, ProductCatalogId("other.product")),
        )
        assertEquals(PurchaseLaunchResult.Failed(CommercialFailureCode.INVALID_STATE), invalid)
        fixture.billing.setOnline(false)
        assertEquals(InternalManageResult.Offline, fixture.billing.openManage())
        assertEquals(InternalCatalogQueryResult.Offline, fixture.billing.queryCatalog())
    }

    private suspend fun launch(
        fixture: Fixture,
        operation: CommercialOperationId,
        account: BackendAccountId,
    ): io.codecks.domain.commercial.PurchaseAttemptHandle {
        val result = fixture.billing.launch(PurchaseLaunchRequest(operation, account, product))
        return (result as PurchaseLaunchResult.AwaitingServerVerification).attempt
    }

    private suspend fun collect(
        fixture: Fixture,
        operation: CommercialOperationId,
        account: BackendAccountId,
    ) = (fixture.billing.collectIntegrity(operation, account, digest) as TransactionIntegrityResult.Ready).evidence

    private suspend fun verification(
        fixture: Fixture,
        operation: CommercialOperationId,
        account: BackendAccountId,
        attempt: io.codecks.domain.commercial.PurchaseAttemptHandle,
        evidence: io.codecks.domain.commercial.TransactionIntegrityEvidence,
    ) = PurchaseVerificationRequest(operation, account, attempt, evidence)

    private suspend fun verification(
        fixture: Fixture,
        operation: CommercialOperationId,
        account: BackendAccountId,
        attempt: io.codecks.domain.commercial.PurchaseAttemptHandle,
    ) = verification(fixture, operation, account, attempt, collect(fixture, operation, account))

    private fun fixture(
        lifecycle: PurchaseServerLifecycleState = PurchaseServerLifecycleState.PURCHASED_UNACKNOWLEDGED,
    ): Fixture {
        val events = mutableListOf<String>()
        val play = FakePlayBilling(events)
        val backend = FakeBackend(accountA, lifecycle, events) { now }
        val entitlements = InternalEntitlementSandbox(backend) { now }
        val integrity = InternalIntegritySandbox({ now })
        val billing = InternalBillingSandbox(
            catalog = listOf(InternalCatalogEntry(product, setOf(PaidCapability.SYNC))),
            backend = backend,
            playBilling = play,
            integrity = integrity,
            entitlements = entitlements,
            clockEpochMillis = { now },
        )
        return Fixture(backend, play, integrity, entitlements, billing, events)
    }

    private fun operation(value: String) = CommercialOperationId.trusted(value)

    private data class Fixture(
        val backend: FakeBackend,
        val play: FakePlayBilling,
        val integrity: InternalIntegritySandbox,
        val entitlements: InternalEntitlementSandbox,
        val billing: InternalBillingSandbox,
        val events: MutableList<String>,
    )

    private class FakePlayBilling(
        private val events: MutableList<String>,
    ) : InternalPlayBillingPort {
        var launchCalls = 0
        var pending = false
        var acknowledgement: InternalAcknowledgementResult = InternalAcknowledgementResult.Acknowledged
        val lastLaunchToken = InternalPurchaseToken.fromPlaySandbox("secret-token-00000001")
        var lastAcknowledgedAccount: BackendAccountId? = null
        var lastAcknowledgedToken: InternalPurchaseToken? = null

        override suspend fun launch(
            accountId: BackendAccountId,
            productId: ProductCatalogId,
            attempt: io.codecks.domain.commercial.PurchaseAttemptHandle,
        ): InternalPlayPurchaseResult {
            launchCalls += 1
            return if (pending) InternalPlayPurchaseResult.Pending
            else InternalPlayPurchaseResult.Purchased(lastLaunchToken)
        }

        override suspend fun acknowledge(
            accountId: BackendAccountId,
            token: InternalPurchaseToken,
        ): InternalAcknowledgementResult {
            events += "ack"
            lastAcknowledgedAccount = accountId
            lastAcknowledgedToken = token
            return acknowledgement
        }
    }

    private class FakeBackend(
        private val account: BackendAccountId,
        var lifecycle: PurchaseServerLifecycleState,
        private val events: MutableList<String>,
        private val clock: () -> Long,
    ) : InternalPurchaseBackendPort {
        var fetchCalls = 0
        var verifyCalls = 0
        var recordAcknowledgedCalls = 0
        var revision = 1L
        var responseAccount = account

        override suspend fun fetch(accountId: BackendAccountId): InternalBackendEntitlement? {
            fetchCalls += 1
            return response(accountId)
        }

        override suspend fun verify(
            operationId: CommercialOperationId,
            accountId: BackendAccountId,
            attempt: io.codecks.domain.commercial.PurchaseAttemptHandle,
            token: InternalPurchaseToken,
        ): InternalBackendEntitlement? {
            verifyCalls += 1
            events += "verify"
            return response(accountId)
        }

        override suspend fun recordAcknowledged(
            operationId: CommercialOperationId,
            accountId: BackendAccountId,
            token: InternalPurchaseToken,
        ): InternalBackendEntitlement? {
            recordAcknowledgedCalls += 1
            events += "recordAcknowledged"
            lifecycle = PurchaseServerLifecycleState.ACTIVE
            return response(accountId, PurchaseServerLifecycleState.ACTIVE)
        }

        override suspend fun restore(
            operationId: CommercialOperationId,
            accountId: BackendAccountId,
        ): InternalBackendEntitlement? = response(accountId)

        private fun response(
            accountId: BackendAccountId,
            responseLifecycle: PurchaseServerLifecycleState = lifecycle,
        ): InternalBackendEntitlement? {
            if (accountId != account) return null
            return InternalBackendEntitlement(
                accountId = responseAccount,
                lifecycle = responseLifecycle,
                capabilities = setOf(PaidCapability.SYNC),
                revision = revision,
                verifiedAtEpochMillis = clock(),
                expiresAtEpochMillis = clock() + 120_000L,
            )
        }
    }
}
