package io.codecks.backend.contracts

private val OPAQUE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")
private val SHA_256 = Regex("[0-9a-f]{64}")

interface RedactedValue

@JvmInline
value class AccountId private constructor(private val canonical: String) : RedactedValue {
    override fun toString(): String = "AccountId(redacted)"

    companion object {
        fun fromVerifiedGoogleSubject(subject: VerifiedGoogleSubject, deriveOpaqueId: (String) -> String): AccountId =
            AccountId(deriveOpaqueId(subject.canonical).also { require(it.matches(OPAQUE_ID)) })
    }
}

class VerifiedGoogleSubject private constructor(internal val canonical: String) : RedactedValue {
    override fun equals(other: Any?): Boolean = other is VerifiedGoogleSubject && canonical == other.canonical
    override fun hashCode(): Int = canonical.hashCode()
    override fun toString(): String = "VerifiedGoogleSubject(redacted)"

    companion object {
        internal fun verified(value: String): VerifiedGoogleSubject =
            VerifiedGoogleSubject(value.also { require(it.matches(OPAQUE_ID)) })
    }
}

@JvmInline
value class OperationId private constructor(private val canonical: String) : RedactedValue {
    override fun toString(): String = "OperationId(redacted)"

    companion object {
        fun trusted(value: String): OperationId = OperationId(value.also { require(it.matches(OPAQUE_ID)) })
    }
}

@JvmInline
value class SessionId private constructor(private val canonical: String) : RedactedValue {
    override fun toString(): String = "SessionId(redacted)"

    companion object {
        fun trusted(value: String): SessionId = SessionId(value.also { require(it.matches(OPAQUE_ID)) })
    }
}

@JvmInline
value class SnapshotId private constructor(private val canonical: String) : RedactedValue {
    override fun toString(): String = "SnapshotId(redacted)"

    companion object {
        fun trusted(value: String): SnapshotId = SnapshotId(value.also { require(it.matches(OPAQUE_ID)) })
    }
}

@JvmInline
value class PurchaseTokenHash private constructor(private val sha256: String) : RedactedValue {
    override fun toString(): String = "PurchaseTokenHash(redacted)"

    companion object {
        /** Raw purchase tokens terminate at the hashing adapter and never enter domain APIs. */
        fun fromTrustedHasher(sha256: String): PurchaseTokenHash =
            PurchaseTokenHash(sha256.also { require(it.matches(SHA_256)) })
    }
}

@JvmInline
value class RequestHash private constructor(private val sha256: String) : RedactedValue {
    override fun toString(): String = "RequestHash(redacted)"

    companion object {
        fun canonicalSha256(value: String): RequestHash =
            RequestHash(value.also { require(it.matches(SHA_256)) })
    }
}

@JvmInline
value class EvidenceId private constructor(private val canonical: String) : RedactedValue {
    override fun toString(): String = "EvidenceId(redacted)"

    companion object {
        internal fun trusted(value: String): EvidenceId = EvidenceId(value.also { require(it.matches(OPAQUE_ID)) })
    }
}

@JvmInline
value class Revision(val value: Long) {
    init {
        require(value >= 0L)
    }

    fun next(): Revision = Revision(Math.addExact(value, 1L))
}

enum class BackendErrorCode {
    ACCOUNT_DELETING,
    ACCOUNT_DELETED,
    ACCOUNT_MISMATCH,
    SESSION_REVOKED,
    SESSION_EXPIRED,
    SESSION_ID_CONFLICT,
    INVALID_SNAPSHOT,
    SNAPSHOT_TOO_LARGE,
    SNAPSHOT_VERSION_UNSUPPORTED,
    PURCHASE_TOKEN_BOUND_TO_OTHER_ACCOUNT,
    SERVER_VERIFICATION_REQUIRED,
    INTEGRITY_REQUIRED,
    INTEGRITY_MISMATCH,
    INTEGRITY_EXPIRED,
    STALE_EVENT,
    IDEMPOTENCY_CONFLICT,
    SERVICE_UNAVAILABLE,
}

sealed interface BackendResult<out T> {
    data class Success<T>(val value: T) : BackendResult<T>
    data class Replayed<T>(val value: T) : BackendResult<T>
    data class Rejected(val code: BackendErrorCode) : BackendResult<Nothing>
}
