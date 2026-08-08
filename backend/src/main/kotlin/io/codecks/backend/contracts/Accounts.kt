package io.codecks.backend.contracts

enum class AccountLifecycle {
    ACTIVE,
    DELETING,
    DELETED,
}

enum class SessionLifecycle {
    ACTIVE,
    REVOKED,
}

data class Session(
    val id: SessionId,
    val accountId: AccountId,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val lifecycle: SessionLifecycle,
    val revision: Revision,
) {
    init {
        require(issuedAtEpochMillis >= 0L)
        require(expiresAtEpochMillis > issuedAtEpochMillis)
    }
}

enum class DeletionStep {
    MUTATIONS_BLOCKED,
    SESSIONS_REVOKED,
    SNAPSHOTS_DELETED,
    RETENTION_MARKER_RECORDED,
}

data class RetentionMarker(
    val accountId: AccountId,
    val deletedAtEpochMillis: Long,
    val revision: Revision,
) {
    init {
        require(deletedAtEpochMillis >= 0L)
    }
}

enum class PlaySubscriptionDisposition {
    /** Account deletion does not cancel, refund, or mutate Play subscriptions. */
    USER_MANAGED_IN_PLAY,
}

data class AccountDeletionReceipt(
    val operationId: OperationId,
    val accountId: AccountId,
    val steps: List<DeletionStep>,
    val marker: RetentionMarker,
    val playSubscriptionDisposition: PlaySubscriptionDisposition =
        PlaySubscriptionDisposition.USER_MANAGED_IN_PLAY,
) {
    init {
        require(steps == DeletionStep.entries) { "Deletion ordering is contractual" }
    }
}

interface AccountSnapshotDeletionPort {
    fun deleteAll(accountId: AccountId, operationId: OperationId): BackendResult<Unit>
}

class AccountService(
    private val deriveAccountId: (VerifiedGoogleSubject) -> AccountId,
) {
    private data class OperationClaim(val kind: String, val fingerprint: Any)

    private data class AccountRecord(
        val accountId: AccountId,
        var lifecycle: AccountLifecycle,
        var revision: Revision,
    )

    private val accountsBySubject = mutableMapOf<VerifiedGoogleSubject, AccountRecord>()
    private val accountsById = mutableMapOf<AccountId, AccountRecord>()
    private val sessions = mutableMapOf<SessionId, Session>()
    private val operationClaims = mutableMapOf<OperationId, OperationClaim>()
    private val operationResults = mutableMapOf<OperationId, Any>()
    private val retentionMarkers = mutableMapOf<AccountId, RetentionMarker>()

    fun issueSession(
        operationId: OperationId,
        verifiedSubject: VerifiedGoogleSubject,
        sessionId: SessionId,
        nowEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): BackendResult<Session> = replay(
        operationId,
        "issue",
        listOf(verifiedSubject, sessionId, nowEpochMillis, expiresAtEpochMillis),
    ) ?: run {
        if (sessions.containsKey(sessionId)) {
            return BackendResult.Rejected(BackendErrorCode.SESSION_ID_CONFLICT)
        }
        val account = accountsBySubject[verifiedSubject] ?: run {
            val derivedId = deriveAccountId(verifiedSubject)
            if (retentionMarkers.containsKey(derivedId)) {
                return BackendResult.Rejected(BackendErrorCode.ACCOUNT_DELETED)
            }
            if (accountsById.containsKey(derivedId)) {
                return BackendResult.Rejected(BackendErrorCode.ACCOUNT_MISMATCH)
            }
            AccountRecord(
                accountId = derivedId,
                lifecycle = AccountLifecycle.ACTIVE,
                revision = Revision(0L),
            ).also {
                accountsBySubject[verifiedSubject] = it
                accountsById[it.accountId] = it
            }
        }
        mutationRejection(account)?.let { return it }
        val session = Session(
            id = sessionId,
            accountId = account.accountId,
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            lifecycle = SessionLifecycle.ACTIVE,
            revision = Revision(0L),
        )
        sessions[sessionId] = session
        account.revision = account.revision.next()
        remember(operationId, session)
    }

    fun rotateSession(
        operationId: OperationId,
        currentSessionId: SessionId,
        replacementSessionId: SessionId,
        nowEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): BackendResult<Session> = replay(
        operationId,
        "rotate",
        listOf(currentSessionId, replacementSessionId, nowEpochMillis, expiresAtEpochMillis),
    ) ?: run {
        val current = sessions[currentSessionId]
            ?: return BackendResult.Rejected(BackendErrorCode.SESSION_REVOKED)
        val account = accountsById.getValue(current.accountId)
        mutationRejection(account)?.let { return it }
        if (current.lifecycle != SessionLifecycle.ACTIVE) {
            return BackendResult.Rejected(BackendErrorCode.SESSION_REVOKED)
        }
        if (current.expiresAtEpochMillis <= nowEpochMillis) {
            return BackendResult.Rejected(BackendErrorCode.SESSION_EXPIRED)
        }
        if (sessions.containsKey(replacementSessionId)) {
            return BackendResult.Rejected(BackendErrorCode.SESSION_ID_CONFLICT)
        }
        sessions[currentSessionId] = current.copy(
            lifecycle = SessionLifecycle.REVOKED,
            revision = current.revision.next(),
        )
        val replacement = Session(
            id = replacementSessionId,
            accountId = current.accountId,
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            lifecycle = SessionLifecycle.ACTIVE,
            revision = Revision(0L),
        )
        sessions[replacementSessionId] = replacement
        account.revision = account.revision.next()
        remember(operationId, replacement)
    }

    fun revokeSession(operationId: OperationId, sessionId: SessionId): BackendResult<Session> =
        replay(operationId, "revoke", sessionId) ?: run {
            val current = sessions[sessionId]
                ?: return BackendResult.Rejected(BackendErrorCode.SESSION_REVOKED)
            val revoked = if (current.lifecycle == SessionLifecycle.REVOKED) {
                current
            } else {
                current.copy(lifecycle = SessionLifecycle.REVOKED, revision = current.revision.next())
            }
            sessions[sessionId] = revoked
            remember(operationId, revoked)
        }

    fun deleteAccount(
        operationId: OperationId,
        accountId: AccountId,
        nowEpochMillis: Long,
        snapshotDeletion: AccountSnapshotDeletionPort,
    ): BackendResult<AccountDeletionReceipt> = replay(
        operationId,
        "delete",
        accountId,
    ) ?: run {
        retentionMarkers[accountId]?.let { marker ->
            val receipt = deletionReceipt(operationId, accountId, marker)
            return remember(operationId, receipt)
        }
        val account = accountsById[accountId]
            ?: return BackendResult.Rejected(BackendErrorCode.ACCOUNT_DELETED)

        account.lifecycle = AccountLifecycle.DELETING
        account.revision = account.revision.next()
        sessions.replaceAll { _, session ->
            if (session.accountId == accountId && session.lifecycle == SessionLifecycle.ACTIVE) {
                session.copy(lifecycle = SessionLifecycle.REVOKED, revision = session.revision.next())
            } else {
                session
            }
        }
        when (val deleted = snapshotDeletion.deleteAll(accountId, operationId)) {
            is BackendResult.Rejected -> return deleted
            is BackendResult.Success,
            is BackendResult.Replayed,
            -> Unit
        }
        account.lifecycle = AccountLifecycle.DELETED
        account.revision = account.revision.next()
        val marker = RetentionMarker(accountId, nowEpochMillis, account.revision)
        retentionMarkers[accountId] = marker
        accountsBySubject.entries.removeAll { it.value.accountId == accountId }
        accountsById.remove(accountId)
        sessions.entries.removeAll { it.value.accountId == accountId }
        val removedOperationIds = operationResults.entries.mapNotNull { (id, value) ->
            when (value) {
                is Session -> value.accountId == accountId
                is AccountDeletionReceipt -> value.accountId == accountId
                else -> false
            }.takeIf { it }?.let { id }
        }
        removedOperationIds.forEach {
            operationResults.remove(it)
            operationClaims.remove(it)
        }
        remember(operationId, deletionReceipt(operationId, accountId, marker))
    }

    fun mutationAccess(accountId: AccountId): BackendResult<Unit> {
        val account = accountsById[accountId]
            ?: return BackendResult.Rejected(BackendErrorCode.ACCOUNT_DELETED)
        return mutationRejection(account) ?: BackendResult.Success(Unit)
    }

    fun session(sessionId: SessionId): Session? = sessions[sessionId]
    fun lifecycle(accountId: AccountId): AccountLifecycle? = accountsById[accountId]?.lifecycle
    fun retentionMarker(accountId: AccountId): RetentionMarker? = retentionMarkers[accountId]

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> replay(
        operationId: OperationId,
        kind: String,
        fingerprint: Any,
    ): BackendResult<T>? {
        val claim = OperationClaim(kind, fingerprint)
        val previousClaim = operationClaims.putIfAbsent(operationId, claim)
        if (previousClaim != null && previousClaim != claim) {
            return BackendResult.Rejected(BackendErrorCode.IDEMPOTENCY_CONFLICT)
        }
        return operationResults[operationId]?.let { BackendResult.Replayed(it as T) }
    }

    private fun <T : Any> remember(operationId: OperationId, value: T): BackendResult.Success<T> {
        operationResults[operationId] = value
        return BackendResult.Success(value)
    }

    private fun mutationRejection(account: AccountRecord): BackendResult.Rejected? = when (account.lifecycle) {
        AccountLifecycle.ACTIVE -> null
        AccountLifecycle.DELETING -> BackendResult.Rejected(BackendErrorCode.ACCOUNT_DELETING)
        AccountLifecycle.DELETED -> BackendResult.Rejected(BackendErrorCode.ACCOUNT_DELETED)
    }

    private fun deletionReceipt(
        operationId: OperationId,
        accountId: AccountId,
        marker: RetentionMarker,
    ): AccountDeletionReceipt = AccountDeletionReceipt(
        operationId = operationId,
        accountId = accountId,
        steps = DeletionStep.entries,
        marker = marker,
    )
}
