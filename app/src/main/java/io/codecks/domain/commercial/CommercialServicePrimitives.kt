package io.codecks.domain.commercial

enum class CommercialUnavailableCode {
    PRODUCTION_DARK,
    NOT_CONFIGURED,
    OFFLINE,
    SERVICE_UNAVAILABLE,
}

enum class CommercialFailureCode {
    AUTHENTICATION_REQUIRED,
    ACCOUNT_MISMATCH,
    INVALID_STATE,
    REJECTED_BY_SERVER,
    RATE_LIMITED,
    TIMEOUT,
    UNKNOWN,
}

data class CommercialRetryDirective(
    val retryAfterMillis: Long?,
) {
    init {
        require(retryAfterMillis == null || retryAfterMillis >= 0L)
    }
}

class CommercialOperationId private constructor(private val canonical: String) {
    init {
        require(canonical.matches(OPAQUE_ID_REGEX))
    }

    override fun equals(other: Any?): Boolean =
        other is CommercialOperationId && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()

    override fun toString(): String = "CommercialOperationId(redacted)"

    companion object {
        internal fun trusted(value: String): CommercialOperationId = CommercialOperationId(value)
    }
}

class BackendAccountId private constructor(private val canonical: String) {
    init {
        require(canonical.matches(OPAQUE_ID_REGEX))
    }

    override fun equals(other: Any?): Boolean =
        other is BackendAccountId && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()

    override fun toString(): String = "BackendAccountId(redacted)"

    companion object {
        /** Only a backend-auth adapter may translate a verified subject into this ID. */
        internal fun verified(value: String): BackendAccountId = BackendAccountId(value)
    }
}

data class AccountBoundCacheMetadata(
    val accountId: BackendAccountId,
    val verifiedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val backendRevision: Long,
) {
    init {
        require(verifiedAtEpochMillis >= 0L)
        require(expiresAtEpochMillis >= verifiedAtEpochMillis)
        require(backendRevision >= 0L)
    }
}

internal val OPAQUE_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")
