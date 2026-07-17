package io.codex.s23deck.data.notifications

data class RawNotificationPreview(
    val id: String,
    val packageName: String,
    val source: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val category: String? = null,
    val importance: Int = 0,
)

object NotificationPrivacyPolicy {
    private val sensitivePackageMarkers = listOf(
        "authenticator",
        "password",
        "passkey",
        "vault",
        "bank",
        "wallet",
        "pay",
        "finance",
        "broker",
        "trading",
        "crypto",
    )

    private val sensitiveLabelMarkers = listOf(
        "authenticator",
        "password",
        "passkey",
        "vault",
        "bank",
        "wallet",
        "pay",
        "finance",
        "broker",
        "trading",
        "crypto",
    )

    private val sensitiveContentMarkers = listOf(
        "verification code",
        "security code",
        "one-time code",
        "otp",
        "passcode",
        "password",
        "reset code",
        "2fa",
        "login code",
    )

    fun apply(
        raw: RawNotificationPreview,
        settings: NotificationPrivacySettings,
        ownPackage: String,
    ): NotificationPreview? {
        if (!settings.showOnTrackpad) return null
        if (raw.packageName == ownPackage) return null
        if (settings.allowedPackages.isNotEmpty() && raw.packageName !in settings.allowedPackages) return null
        if (settings.hideSensitiveApps && raw.looksSensitive()) return null
        if (raw.title.isBlank() && raw.text.isBlank()) return null

        return if (settings.showContent) {
            NotificationPreview(
                id = raw.id,
                packageName = raw.packageName,
                source = raw.source,
                title = raw.title.take(80),
                text = raw.text.take(120),
                postedAtMillis = raw.postedAtMillis,
                category = raw.category,
                importance = raw.importance,
                redacted = false,
            )
        } else {
            NotificationPreview(
                id = raw.id,
                packageName = raw.packageName,
                source = raw.source,
                title = raw.source,
                text = "Content hidden",
                postedAtMillis = raw.postedAtMillis,
                category = raw.category,
                importance = raw.importance,
                redacted = true,
            )
        }
    }

    private fun RawNotificationPreview.looksSensitive(): Boolean {
        val normalizedPackage = packageName.lowercase()
        val normalizedSource = source.lowercase()
        val normalizedContent = "$title $text".lowercase()
        return sensitivePackageMarkers.any(normalizedPackage::contains) ||
            sensitiveLabelMarkers.any(normalizedSource::contains) ||
            sensitiveContentMarkers.any(normalizedContent::contains) ||
            Regex("\\b\\d{6,8}\\b").containsMatchIn(normalizedContent)
    }
}
