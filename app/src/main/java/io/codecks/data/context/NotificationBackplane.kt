package io.codecks.data.context

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationPreview(
    val id: String,
    val packageName: String = "",
    val source: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val category: String? = null,
    val importance: Int = 0,
    val redacted: Boolean = false,
)

data class NotificationContextEvent(
    val id: String,
    val packageName: String,
    val source: String,
    val category: String?,
    val importance: Int,
    val postedAtMillis: Long,
    val observedAtMillis: Long,
    val removed: Boolean,
)

object PhoneNotificationBackplane {
    private val _notifications = MutableStateFlow<List<NotificationPreview>>(emptyList())
    val notifications: StateFlow<List<NotificationPreview>> = _notifications.asStateFlow()
    private val _events = MutableStateFlow<List<NotificationContextEvent>>(emptyList())
    val events: StateFlow<List<NotificationContextEvent>> = _events.asStateFlow()
    private val _privacySettings = MutableStateFlow(NotificationPrivacySettings())

    fun replace(items: List<NotificationPreview>) {
        _notifications.value = items
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.postedAtMillis }
            .take(8)
    }

    fun updatePrivacySettings(settings: NotificationPrivacySettings) {
        _privacySettings.value = settings
        if (!settings.showOnTrackpad) {
            _notifications.value = emptyList()
        }
    }

    fun privacySettings(): NotificationPrivacySettings = _privacySettings.value

    fun record(event: NotificationContextEvent) {
        _events.value = (_events.value + event)
            .distinctBy { "${it.id}:${it.removed}:${it.observedAtMillis}" }
            .sortedByDescending { it.observedAtMillis }
            .take(64)
    }

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val component = ComponentName(context, DeckBridgeNotificationListenerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }
}

class DeckBridgeNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        publish(activeNotifications.orEmpty())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { PhoneNotificationBackplane.record(toContextEvent(it, removed = false)) }
        publish(activeNotifications.orEmpty())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { PhoneNotificationBackplane.record(toContextEvent(it, removed = true)) }
        publish(activeNotifications.orEmpty())
    }

    private fun publish(items: Array<out StatusBarNotification>) {
        val ownPackage = packageName
        val privacySettings = PhoneNotificationBackplane.privacySettings()
        PhoneNotificationBackplane.replace(
            items
                .asSequence()
                .mapNotNull { sbn ->
                    toRawPreview(sbn)?.let { raw ->
                        NotificationPrivacyPolicy.apply(
                            raw = raw,
                            settings = privacySettings,
                            ownPackage = ownPackage,
                        )
                    }
                }
                .toList(),
        )
    }

    private fun toRawPreview(sbn: StatusBarNotification): RawNotificationPreview? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = listOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return RawNotificationPreview(
            id = "${sbn.packageName}:${sbn.id}:${sbn.postTime}",
            packageName = sbn.packageName,
            source = appLabel(sbn.packageName),
            title = title,
            text = text,
            postedAtMillis = sbn.postTime,
            category = sbn.notification.category,
            importance = sbn.notification.priority,
        )
    }

    private fun toContextEvent(sbn: StatusBarNotification, removed: Boolean): NotificationContextEvent =
        NotificationContextEvent(
            id = "${sbn.packageName}:${sbn.id}:${sbn.postTime}",
            packageName = sbn.packageName,
            source = appLabel(sbn.packageName),
            category = sbn.notification.category,
            importance = sbn.notification.priority,
            postedAtMillis = sbn.postTime,
            observedAtMillis = System.currentTimeMillis(),
            removed = removed,
        )

    private fun appLabel(packageName: String): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
}
