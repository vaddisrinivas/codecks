package io.codecks.backend.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccountServiceTest {
    @Test
    fun `session issue rotation revocation and operation replay are deterministic`() {
        val service = accountService()
        val issued = assertIs<BackendResult.Success<Session>>(
            service.issueSession(op("issue001"), subject(), session("session01"), 10L, 100L),
        ).value
        val replay = assertIs<BackendResult.Replayed<Session>>(
            service.issueSession(op("issue001"), subject(), session("session01"), 10L, 100L),
        ).value
        assertEquals(issued, replay)
        assertEquals(
            BackendErrorCode.IDEMPOTENCY_CONFLICT,
            rejectedCode(
                service.issueSession(op("issue001"), subject(), session("different1"), 10L, 100L),
            ),
        )

        val rotated = assertIs<BackendResult.Success<Session>>(
            service.rotateSession(op("rotate01"), issued.id, session("session02"), 20L, 120L),
        ).value
        assertEquals(SessionLifecycle.REVOKED, service.session(issued.id)?.lifecycle)
        assertEquals(SessionLifecycle.ACTIVE, rotated.lifecycle)
        assertIs<BackendResult.Success<Session>>(service.revokeSession(op("revoke01"), rotated.id))
        assertEquals(SessionLifecycle.REVOKED, service.session(rotated.id)?.lifecycle)
    }

    @Test
    fun `deletion blocks mutations revokes sessions first deletes snapshots and is idempotent`() {
        lateinit var service: AccountService
        service = accountService()
        val active = assertIs<BackendResult.Success<Session>>(
            service.issueSession(op("issue002"), subject(), session("session03"), 10L, 100L),
        ).value
        var observedDeleting = false
        var observedRevoked = false
        val snapshots = object : AccountSnapshotDeletionPort {
            override fun deleteAll(accountId: AccountId, operationId: OperationId): BackendResult<Unit> {
                observedDeleting = service.lifecycle(accountId) == AccountLifecycle.DELETING
                observedRevoked = service.session(active.id)?.lifecycle == SessionLifecycle.REVOKED
                return BackendResult.Success(Unit)
            }
        }

        val result = assertIs<BackendResult.Success<AccountDeletionReceipt>>(
            service.deleteAccount(op("delete01"), active.accountId, 200L, snapshots),
        ).value
        assertTrue(observedDeleting)
        assertTrue(observedRevoked)
        assertEquals(DeletionStep.entries, result.steps)
        assertEquals(PlaySubscriptionDisposition.USER_MANAGED_IN_PLAY, result.playSubscriptionDisposition)
        assertEquals(null, service.lifecycle(active.accountId))
        assertEquals(null, service.session(active.id))
        assertEquals(BackendErrorCode.ACCOUNT_DELETED, rejectedCode(service.mutationAccess(active.accountId)))
        assertEquals(result, assertIs<BackendResult.Replayed<AccountDeletionReceipt>>(
            service.deleteAccount(op("delete01"), active.accountId, 300L, snapshots),
        ).value)
        assertIs<BackendResult.Success<AccountDeletionReceipt>>(
            service.deleteAccount(op("delete03"), active.accountId, 300L, snapshots),
        )
        assertEquals(
            BackendErrorCode.ACCOUNT_DELETED,
            rejectedCode(
                service.issueSession(op("issue099"), subject(), session("session99"), 310L, 400L),
            ),
        )
        assertEquals(result.marker, service.retentionMarker(active.accountId))
    }

    @Test
    fun `failed snapshot deletion leaves account fail closed and retry completes`() {
        val service = accountService()
        val active = assertIs<BackendResult.Success<Session>>(
            service.issueSession(op("issue003"), subject(), session("session04"), 10L, 100L),
        ).value
        var attempts = 0
        val snapshots = object : AccountSnapshotDeletionPort {
            override fun deleteAll(accountId: AccountId, operationId: OperationId): BackendResult<Unit> {
                attempts += 1
                return if (attempts == 1) {
                    BackendResult.Rejected(BackendErrorCode.SERVICE_UNAVAILABLE)
                } else {
                    BackendResult.Success(Unit)
                }
            }
        }

        assertEquals(
            BackendErrorCode.SERVICE_UNAVAILABLE,
            rejectedCode(service.deleteAccount(op("delete02"), active.accountId, 200L, snapshots)),
        )
        assertEquals(AccountLifecycle.DELETING, service.lifecycle(active.accountId))
        assertEquals(BackendErrorCode.ACCOUNT_DELETING, rejectedCode(service.mutationAccess(active.accountId)))
        assertIs<BackendResult.Success<AccountDeletionReceipt>>(
            service.deleteAccount(op("delete02"), active.accountId, 210L, snapshots),
        )
    }

    @Test
    fun `operation id cannot be replayed across mutation kinds`() {
        val service = accountService()
        val issued = assertIs<BackendResult.Success<Session>>(
            service.issueSession(op("shared001"), subject(), session("session05"), 10L, 100L),
        ).value
        assertEquals(
            BackendErrorCode.IDEMPOTENCY_CONFLICT,
            rejectedCode(service.revokeSession(op("shared001"), issued.id)),
        )
        assertFalse(service.session(issued.id)?.lifecycle == SessionLifecycle.REVOKED)
    }

    @Test
    fun `session ids cannot be reused or overwrite another active session`() {
        val service = accountService()
        val first = assertIs<BackendResult.Success<Session>>(
            service.issueSession(op("issue010"), subject(), session("session10"), 10L, 100L),
        ).value
        assertEquals(
            BackendErrorCode.SESSION_ID_CONFLICT,
            rejectedCode(service.issueSession(op("issue011"), subject(), first.id, 20L, 120L)),
        )
        assertEquals(SessionLifecycle.ACTIVE, service.session(first.id)?.lifecycle)

        val second = assertIs<BackendResult.Success<Session>>(
            service.issueSession(op("issue012"), subject(), session("session11"), 20L, 120L),
        ).value
        assertEquals(
            BackendErrorCode.SESSION_ID_CONFLICT,
            rejectedCode(service.rotateSession(op("rotate10"), second.id, first.id, 30L, 130L)),
        )
        assertEquals(SessionLifecycle.ACTIVE, service.session(first.id)?.lifecycle)
        assertEquals(SessionLifecycle.ACTIVE, service.session(second.id)?.lifecycle)
    }
}

private fun accountService(): AccountService = AccountService { verified ->
    AccountId.fromVerifiedGoogleSubject(verified) { "account-00000001" }
}

internal fun subject(): VerifiedGoogleSubject = VerifiedGoogleSubject.verified("google-subject-0001")
internal fun op(value: String): OperationId = OperationId.trusted(value.padEnd(8, '0'))
internal fun session(value: String): SessionId = SessionId.trusted(value.padEnd(8, '0'))
internal fun token(char: Char): PurchaseTokenHash = PurchaseTokenHash.fromTrustedHasher(char.toString().repeat(64))
internal fun hash(char: Char): RequestHash = RequestHash.canonicalSha256(char.toString().repeat(64))
internal fun evidence(value: String, binding: IntegrityBinding): VerifiedIntegrityEvidence =
    VerifiedIntegrityEvidence.fromServerVerifier(EvidenceId.trusted(value.padEnd(8, '0')), binding, 1_000L)

internal fun rejectedCode(result: BackendResult<*>): BackendErrorCode =
    assertIs<BackendResult.Rejected>(result).code
