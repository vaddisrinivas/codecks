package io.codecks.domain

enum class CommandOrigin {
    Bundled,
    UserAuthored,
    AiGenerated,
}

data class CommandReview(
    val reviewedRevision: String? = null,
    val checkedRevision: String? = null,
)

data class ExecutionAuthorization(
    val dangerousRevisionConfirmed: String? = null,
)
