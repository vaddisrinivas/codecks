package io.codecks.data.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPrivacyPolicyTest {
    @Test
    fun defaultSettings_redactTitleAndBody() {
        val preview = NotificationPrivacyPolicy.apply(
            raw = raw(title = "Message", text = "Lunch moved"),
            settings = NotificationPrivacySettings(),
            ownPackage = "io.codecks",
        )

        requireNotNull(preview)
        assertEquals("Messages", preview.title)
        assertEquals("Content hidden", preview.text)
        assertTrue(preview.redacted)
    }

    @Test
    fun showContent_keepsBoundedTitleAndBody() {
        val preview = NotificationPrivacyPolicy.apply(
            raw = raw(title = "A".repeat(100), text = "B".repeat(140)),
            settings = NotificationPrivacySettings(showContent = true),
            ownPackage = "io.codecks",
        )

        requireNotNull(preview)
        assertEquals(80, preview.title.length)
        assertEquals(120, preview.text.length)
        assertFalse(preview.redacted)
    }

    @Test
    fun disabledTrackpadLane_filtersEverything() {
        val preview = NotificationPrivacyPolicy.apply(
            raw = raw(),
            settings = NotificationPrivacySettings(showOnTrackpad = false),
            ownPackage = "io.codecks",
        )

        assertNull(preview)
    }

    @Test
    fun sensitiveAppsAreHiddenByDefault() {
        val preview = NotificationPrivacyPolicy.apply(
            raw = raw(packageName = "com.example.passwordvault", source = "Password Vault"),
            settings = NotificationPrivacySettings(),
            ownPackage = "io.codecks",
        )

        assertNull(preview)
    }

    @Test
    fun ownPackageIsAlwaysFiltered() {
        val preview = NotificationPrivacyPolicy.apply(
            raw = raw(packageName = "io.codecks"),
            settings = NotificationPrivacySettings(showContent = true, hideSensitiveApps = false),
            ownPackage = "io.codecks",
        )

        assertNull(preview)
    }

    @Test
    fun allowlistFiltersUnapprovedApps() {
        val preview = NotificationPrivacyPolicy.apply(
            raw = raw(packageName = "com.example.messages"),
            settings = NotificationPrivacySettings(allowedPackages = setOf("com.example.calendar")),
            ownPackage = "io.codecks",
        )

        assertNull(preview)
    }

    @Test
    fun allowlistKeepsApprovedAppsRedactedByDefault() {
        val preview = NotificationPrivacyPolicy.apply(
            raw = raw(packageName = "com.example.messages", source = "Messages", title = "Lunch", text = "Moved"),
            settings = NotificationPrivacySettings(allowedPackages = setOf("com.example.messages")),
            ownPackage = "io.codecks",
        )

        requireNotNull(preview)
        assertEquals("Messages", preview.title)
        assertEquals("Content hidden", preview.text)
        assertTrue(preview.redacted)
    }

    @Test
    fun sensitiveContentIsHiddenEvenWhenContentPreviewIsEnabled() {
        val preview = NotificationPrivacyPolicy.apply(
            raw = raw(title = "Verification code", text = "123456"),
            settings = NotificationPrivacySettings(showContent = true),
            ownPackage = "io.codecks",
        )

        assertNull(preview)
    }

    private fun raw(
        packageName: String = "com.example.messages",
        source: String = "Messages",
        title: String = "Hello",
        text: String = "World",
    ): RawNotificationPreview = RawNotificationPreview(
        id = "notification-1",
        packageName = packageName,
        source = source,
        title = title,
        text = text,
        postedAtMillis = 123L,
    )
}
