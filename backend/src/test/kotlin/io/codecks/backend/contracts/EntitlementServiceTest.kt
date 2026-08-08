package io.codecks.backend.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntitlementServiceTest {
    @Test
    fun `forged client assertion and pending Play state never grant entitlement`() {
        val fixture = Fixture()
        val account = fixture.account('1')
        val token = token('a')

        assertEquals(
            BackendErrorCode.SERVER_VERIFICATION_REQUIRED,
            rejectedCode(fixture.service.submitClientAssertion(op("client01"), account, token)),
        )
        assertEquals(0, fixture.play.queryCount)
        assertTrue(fixture.service.cachedFor(account).isEmpty())

        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.PENDING, 1L))
        val pending = assertIs<BackendResult.Success<EntitlementRecord>>(
            fixture.service.reconcile(op("recon001"), account, token, 20L),
        ).value
        assertFalse(pending.grantsAccess)
        assertEquals(AcknowledgementState.NOT_APPLICABLE, pending.acknowledgement)
    }

    @Test
    fun `verified active purchase binds token acknowledges on backend and replays idempotently`() {
        val fixture = Fixture()
        val account = fixture.account('1')
        val token = token('b')
        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.ACTIVE, 1L))
        val binding = IntegrityBinding(account, op("verify01"), hash('1'), 100L)
        val first = assertIs<BackendResult.Success<EntitlementRecord>>(
            fixture.service.verifyPurchaseMutation(
                binding.operationId,
                account,
                token,
                binding.requestHash,
                evidence("evidence1", binding),
                binding.issuedAtEpochMillis,
            ),
        ).value
        assertTrue(first.grantsAccess)
        assertEquals(AcknowledgementState.CONFIRMED, first.acknowledgement)
        assertEquals(1, fixture.acks.calls)

        val replay = assertIs<BackendResult.Replayed<EntitlementRecord>>(
            fixture.service.verifyPurchaseMutation(
                binding.operationId,
                account,
                token,
                binding.requestHash,
                null,
                binding.issuedAtEpochMillis,
            ),
        ).value
        assertEquals(first, replay)
        assertEquals(1, fixture.play.queryCount)
    }

    @Test
    fun `integrity evidence is operation account hash and time bound and cannot replay`() {
        val fixture = Fixture()
        val account = fixture.account('1')
        val token = token('c')
        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.ACTIVE, 1L))
        val original = IntegrityBinding(account, op("verify02"), hash('2'), 100L)
        val proof = evidence("evidence2", original)

        assertEquals(
            BackendErrorCode.INTEGRITY_MISMATCH,
            rejectedCode(
                fixture.service.verifyPurchaseMutation(
                    op("verify03"), account, token, original.requestHash, proof, 100L,
                ),
            ),
        )
        assertEquals(0, fixture.play.queryCount)
        assertIs<BackendResult.Success<EntitlementRecord>>(
            fixture.service.reconcile(op("recover01"), account, token, 101L),
        )

        val directGate = TransactionIntegrityGate()
        assertIs<BackendResult.Success<Unit>>(directGate.authorizeMutation(original, proof, 100L))
        assertEquals(
            BackendErrorCode.INTEGRITY_MISMATCH,
            rejectedCode(directGate.authorizeMutation(original, proof, 100L)),
        )
        assertEquals(
            BackendErrorCode.INTEGRITY_MISMATCH,
            rejectedCode(
                directGate.authorizeMutation(
                    original.copy(operationId = op("verify99")),
                    proof,
                    100L,
                ),
            ),
        )
        assertEquals(
            BackendErrorCode.INTEGRITY_EXPIRED,
            rejectedCode(
                TransactionIntegrityGate().authorizeMutation(original, proof, 1_000L),
            ),
        )
        assertEquals(
            BackendErrorCode.INTEGRITY_MISMATCH,
            rejectedCode(
                TransactionIntegrityGate().authorizeMutation(
                    original.copy(accountId = fixture.account('2')),
                    proof,
                    100L,
                ),
            ),
        )
        assertEquals(
            BackendErrorCode.INTEGRITY_MISMATCH,
            rejectedCode(
                TransactionIntegrityGate().authorizeMutation(
                    original.copy(requestHash = hash('3')),
                    proof,
                    100L,
                ),
            ),
        )
        assertEquals(
            BackendErrorCode.INTEGRITY_MISMATCH,
            rejectedCode(
                TransactionIntegrityGate().authorizeMutation(
                    original.copy(issuedAtEpochMillis = 101L),
                    evidence("evidence3", original.copy(issuedAtEpochMillis = 101L)),
                    100L,
                ),
            ),
        )
    }

    @Test
    fun `operation id is bound to request body and mutation kind even after rejection`() {
        val fixture = Fixture()
        val account = fixture.account('1')
        val token = token('8')
        val operationId = op("verify08")
        val original = IntegrityBinding(account, operationId, hash('8'), 100L)

        assertEquals(
            BackendErrorCode.INTEGRITY_REQUIRED,
            rejectedCode(
                fixture.service.verifyPurchaseMutation(
                    operationId, account, token, original.requestHash, null, 100L,
                ),
            ),
        )
        assertEquals(
            BackendErrorCode.IDEMPOTENCY_CONFLICT,
            rejectedCode(
                fixture.service.verifyPurchaseMutation(
                    operationId,
                    account,
                    token,
                    hash('9'),
                    evidence("evidence8", original.copy(requestHash = hash('9'))),
                    100L,
                ),
            ),
        )
        assertEquals(
            BackendErrorCode.IDEMPOTENCY_CONFLICT,
            rejectedCode(fixture.service.reconcile(operationId, account, token, 100L)),
        )
        assertEquals(0, fixture.play.queryCount)
    }

    @Test
    fun `RTDN requeries Play and duplicate or out of order revisions are idempotent`() {
        val fixture = Fixture()
        val account = fixture.account('1')
        val token = token('d')
        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.ACTIVE, 2L))
        assertIs<BackendResult.Success<EntitlementRecord>>(
            fixture.service.reconcile(op("recon002"), account, token, 20L),
        )

        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.GRACE, 3L))
        val signal = RtdnSignal(op("rtdn0001"), token, 30L)
        val grace = assertIs<BackendResult.Success<EntitlementRecord>>(fixture.service.processRtdn(signal)).value
        assertEquals(EntitlementLifecycle.GRACE, grace.lifecycle)
        val queriesAfterFirstSignal = fixture.play.queryCount
        assertIs<BackendResult.Replayed<EntitlementRecord>>(fixture.service.processRtdn(signal))
        assertEquals(queriesAfterFirstSignal, fixture.play.queryCount)

        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.ACTIVE, 2L))
        assertEquals(
            BackendErrorCode.STALE_EVENT,
            rejectedCode(fixture.service.processRtdn(RtdnSignal(op("rtdn0002"), token, 40L))),
        )
        assertEquals(
            BackendErrorCode.STALE_EVENT,
            rejectedCode(fixture.service.processRtdn(RtdnSignal(op("rtdn0002"), token, 50L))),
        )
    }

    @Test
    fun `refund revoke and expiry remove access after authoritative requery`() {
        EntitlementLifecycle.entries
            .filterNot { it == EntitlementLifecycle.ACTIVE || it == EntitlementLifecycle.GRACE }
            .forEachIndexed { index, lifecycle ->
                val fixture = Fixture()
                val account = fixture.account('1')
                val token = token("abcd"[index])
                fixture.play.response = BackendResult.Success(fixture.purchase(account, token, lifecycle, 1L))
                val result = assertIs<BackendResult.Success<EntitlementRecord>>(
                    fixture.service.reconcile(op("state$index"), account, token, 20L),
                ).value
                assertFalse(result.grantsAccess, lifecycle.name)
            }
    }

    @Test
    fun `purchase token cannot move between accounts`() {
        val fixture = Fixture()
        val first = fixture.account('1')
        val second = fixture.account('2')
        val token = token('f')
        fixture.play.response = BackendResult.Success(fixture.purchase(first, token, EntitlementLifecycle.ACTIVE, 1L))
        assertIs<BackendResult.Success<EntitlementRecord>>(
            fixture.service.reconcile(op("bind0001"), first, token, 20L),
        )

        fixture.play.response = BackendResult.Success(fixture.purchase(second, token, EntitlementLifecycle.ACTIVE, 2L))
        assertEquals(
            BackendErrorCode.PURCHASE_TOKEN_BOUND_TO_OTHER_ACCOUNT,
            rejectedCode(fixture.service.reconcile(op("bind0002"), second, token, 30L)),
        )
    }

    @Test
    fun `failed backend acknowledgement remains explicit without hiding verified lifecycle`() {
        val fixture = Fixture()
        val account = fixture.account('1')
        val token = token('9')
        fixture.acks.result = BackendResult.Rejected(BackendErrorCode.SERVICE_UNAVAILABLE)
        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.ACTIVE, 1L))

        val record = assertIs<BackendResult.Success<EntitlementRecord>>(
            fixture.service.reconcile(op("ackfail1"), account, token, 20L),
        ).value
        assertEquals(AcknowledgementState.REQUIRED_BY_BACKEND, record.acknowledgement)
        assertFalse(record.grantsAccess)

        fixture.acks.result = BackendResult.Success(Unit)
        val recovered = assertIs<BackendResult.Success<EntitlementRecord>>(
            fixture.service.reconcile(op("ackretry"), account, token, 30L),
        ).value
        assertEquals(AcknowledgementState.CONFIRMED, recovered.acknowledgement)
        assertTrue(recovered.grantsAccess)
        assertEquals(record.backendRevision.next(), recovered.backendRevision)
        assertEquals(2, fixture.acks.calls)
    }

    @Test
    fun `same provider revision with conflicting state fails closed`() {
        val fixture = Fixture()
        val account = fixture.account('1')
        val token = token('7')
        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.ACTIVE, 3L))
        assertIs<BackendResult.Success<EntitlementRecord>>(
            fixture.service.reconcile(op("sameRev1"), account, token, 20L),
        )

        fixture.play.response = BackendResult.Success(fixture.purchase(account, token, EntitlementLifecycle.REFUNDED, 3L))
        assertEquals(
            BackendErrorCode.STALE_EVENT,
            rejectedCode(fixture.service.reconcile(op("sameRev2"), account, token, 30L)),
        )
        assertTrue(fixture.service.onAccountSwitch(account).activeAccountId == account)
        assertTrue(fixture.service.cachedFor(account).single().grantsAccess)
    }

    @Test
    fun `account switch invalidates cache and exposes only active account records`() {
        val fixture = Fixture()
        val first = fixture.account('1')
        val second = fixture.account('2')
        val firstToken = token('1')
        val secondToken = token('2')
        fixture.play.response = BackendResult.Success(fixture.purchase(first, firstToken, EntitlementLifecycle.ACTIVE, 1L))
        fixture.service.reconcile(op("cache001"), first, firstToken, 20L)
        fixture.play.response = BackendResult.Success(fixture.purchase(second, secondToken, EntitlementLifecycle.GRACE, 1L))
        fixture.service.reconcile(op("cache002"), second, secondToken, 20L)

        fixture.service.onAccountSwitch(first)
        assertEquals(listOf(first), fixture.service.cachedFor(first).map { it.accountId }.distinct())
        val switched = fixture.service.onAccountSwitch(second)
        assertTrue(switched.invalidated)
        assertTrue(fixture.service.cachedFor(first).isEmpty())
        assertEquals(listOf(second), fixture.service.cachedFor(second).map { it.accountId }.distinct())
        fixture.service.onAccountSwitch(null)
        assertTrue(fixture.service.cachedFor(second).isEmpty())
    }

    private class Fixture {
        val play = FakePlay()
        val acks = FakeAcknowledgements()
        val service = EntitlementService(play, acks, TransactionIntegrityGate())

        fun account(suffix: Char): AccountId =
            AccountId.fromVerifiedGoogleSubject(subject()) { "account-0000000$suffix" }

        fun purchase(
            account: AccountId,
            token: PurchaseTokenHash,
            lifecycle: EntitlementLifecycle,
            revision: Long,
        ): VerifiedPlayPurchase = VerifiedPlayPurchase(
            tokenHash = token,
            accountId = account,
            productId = "codecks_pro",
            lifecycle = lifecycle,
            providerRevision = revision,
            providerEventTimeEpochMillis = revision * 10L,
            acknowledgedByPlay = false,
        )
    }

    private class FakePlay : PlayPurchaseRequeryPort {
        lateinit var response: BackendResult<VerifiedPlayPurchase>
        var queryCount: Int = 0

        override fun requery(tokenHash: PurchaseTokenHash): BackendResult<VerifiedPlayPurchase> {
            queryCount += 1
            return response
        }
    }

    private class FakeAcknowledgements : PlayAcknowledgementPort {
        var calls: Int = 0
        var result: BackendResult<Unit> = BackendResult.Success(Unit)

        override fun acknowledge(tokenHash: PurchaseTokenHash, accountId: AccountId): BackendResult<Unit> {
            calls += 1
            return result
        }
    }
}
