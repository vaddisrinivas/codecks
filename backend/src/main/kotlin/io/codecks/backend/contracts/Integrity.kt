package io.codecks.backend.contracts

const val MAX_INTEGRITY_EVIDENCE_TTL_MILLIS: Long = 5 * 60 * 1_000L

data class IntegrityBinding(
    val accountId: AccountId,
    val operationId: OperationId,
    val requestHash: RequestHash,
    val issuedAtEpochMillis: Long,
) {
    init {
        require(issuedAtEpochMillis >= 0L)
    }
}

class VerifiedIntegrityEvidence private constructor(
    val evidenceId: EvidenceId,
    val binding: IntegrityBinding,
    val expiresAtEpochMillis: Long,
) : RedactedValue {
    init {
        require(expiresAtEpochMillis >= binding.issuedAtEpochMillis)
        require(expiresAtEpochMillis - binding.issuedAtEpochMillis <= MAX_INTEGRITY_EVIDENCE_TTL_MILLIS)
    }

    override fun toString(): String = "VerifiedIntegrityEvidence(redacted)"

    companion object {
        /** Only a server-side Play Integrity verifier may construct verified evidence. */
        internal fun fromServerVerifier(
            evidenceId: EvidenceId,
            binding: IntegrityBinding,
            expiresAtEpochMillis: Long,
        ): VerifiedIntegrityEvidence = VerifiedIntegrityEvidence(evidenceId, binding, expiresAtEpochMillis)
    }
}

interface IntegrityEvidencePort {
    fun verify(binding: IntegrityBinding): BackendResult<VerifiedIntegrityEvidence>
}

class TransactionIntegrityGate {
    private val consumedEvidence = mutableSetOf<EvidenceId>()

    /** A rejection applies only to this mutation. It never changes account/session/read state. */
    fun authorizeMutation(
        expected: IntegrityBinding,
        evidence: VerifiedIntegrityEvidence?,
        nowEpochMillis: Long,
    ): BackendResult<Unit> {
        if (evidence == null) return BackendResult.Rejected(BackendErrorCode.INTEGRITY_REQUIRED)
        if (evidence.binding != expected) return BackendResult.Rejected(BackendErrorCode.INTEGRITY_MISMATCH)
        if (expected.issuedAtEpochMillis > nowEpochMillis) {
            return BackendResult.Rejected(BackendErrorCode.INTEGRITY_MISMATCH)
        }
        if (evidence.expiresAtEpochMillis <= nowEpochMillis) {
            return BackendResult.Rejected(BackendErrorCode.INTEGRITY_EXPIRED)
        }
        if (!consumedEvidence.add(evidence.evidenceId)) {
            return BackendResult.Rejected(BackendErrorCode.INTEGRITY_MISMATCH)
        }
        return BackendResult.Success(Unit)
    }
}
