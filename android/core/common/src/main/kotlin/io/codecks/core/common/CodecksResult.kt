package io.codecks.core.common

sealed interface CodecksResult<out T> {
    data class Ok<T>(val value: T) : CodecksResult<T>
    data class Err(val error: CodecksError) : CodecksResult<Nothing>
}

data class CodecksError(
    val code: String,
    val safeMessage: String,
    val repair: String? = null,
)

inline fun <T> codecksRun(block: () -> T): CodecksResult<T> =
    try {
        CodecksResult.Ok(block())
    } catch (error: IllegalArgumentException) {
        CodecksResult.Err(
            CodecksError(
                code = "invalid-input",
                safeMessage = error.message ?: "Invalid input",
            ),
        )
    }

