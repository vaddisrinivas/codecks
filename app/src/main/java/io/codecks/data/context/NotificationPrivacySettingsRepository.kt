package io.codecks.data.context

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationPrivacyDataStore by preferencesDataStore(name = "notification_privacy")

data class NotificationPrivacySettings(
    val showOnTrackpad: Boolean = true,
    val showContent: Boolean = false,
    val hideSensitiveApps: Boolean = true,
    val allowedPackages: Set<String> = emptySet(),
)

@Singleton
class NotificationPrivacySettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val settings: Flow<NotificationPrivacySettings> = context.notificationPrivacyDataStore.data.map { preferences ->
        NotificationPrivacySettings(
            showOnTrackpad = preferences[SHOW_ON_TRACKPAD] ?: true,
            showContent = preferences[SHOW_CONTENT] ?: false,
            hideSensitiveApps = preferences[HIDE_SENSITIVE_APPS] ?: true,
            allowedPackages = preferences[ALLOWED_PACKAGES].orEmpty(),
        )
    }

    suspend fun update(transform: (NotificationPrivacySettings) -> NotificationPrivacySettings) {
        context.notificationPrivacyDataStore.edit { preferences ->
            val current = NotificationPrivacySettings(
                showOnTrackpad = preferences[SHOW_ON_TRACKPAD] ?: true,
                showContent = preferences[SHOW_CONTENT] ?: false,
                hideSensitiveApps = preferences[HIDE_SENSITIVE_APPS] ?: true,
                allowedPackages = preferences[ALLOWED_PACKAGES].orEmpty(),
            )
            val next = transform(current)
            preferences[SHOW_ON_TRACKPAD] = next.showOnTrackpad
            preferences[SHOW_CONTENT] = next.showContent
            preferences[HIDE_SENSITIVE_APPS] = next.hideSensitiveApps
            preferences[ALLOWED_PACKAGES] = next.allowedPackages
        }
    }

    private companion object {
        val SHOW_ON_TRACKPAD = booleanPreferencesKey("show_on_trackpad")
        val SHOW_CONTENT = booleanPreferencesKey("show_content")
        val HIDE_SENSITIVE_APPS = booleanPreferencesKey("hide_sensitive_apps")
        val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
    }
}
