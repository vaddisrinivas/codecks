package io.codecks.domain.update

class ManualUpdateCheckRequest private constructor(
    val requestedAtMillis: Long,
) {
    companion object {
        fun fromExplicitForegroundAction(
            appForeground: Boolean,
            requestedAtMillis: Long,
        ): Result<ManualUpdateCheckRequest> = runCatching {
            require(appForeground) { "Update checks require a foreground user action" }
            require(requestedAtMillis >= 0L)
            ManualUpdateCheckRequest(requestedAtMillis)
        }
    }
}

sealed interface UpdateAvailability {
    val currentVersion: SemanticVersion

    data class UpToDate(
        override val currentVersion: SemanticVersion,
        val latestVersion: SemanticVersion,
    ) : UpdateAvailability

    data class Available(
        override val currentVersion: SemanticVersion,
        val latestVersion: SemanticVersion,
        val releasePageUrl: String,
    ) : UpdateAvailability
}

fun interface UpdateChecker {
    suspend fun check(
        request: ManualUpdateCheckRequest,
        currentVersionName: String,
    ): Result<UpdateAvailability>
}
