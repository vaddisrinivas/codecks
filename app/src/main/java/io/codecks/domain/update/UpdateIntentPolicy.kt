package io.codecks.domain.update

class UpdateIntentPolicy(
    private val sourcePolicy: UpdateSourcePolicy,
) {
    fun allowedExternalUrl(value: String?): String? {
        val candidate = value?.takeIf(String::isNotBlank) ?: return null
        return runCatching { sourcePolicy.requireReleasePageUrl(candidate).toString() }.getOrNull()
    }
}
