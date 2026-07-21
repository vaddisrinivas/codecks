package io.codecks.core.settings

import androidx.compose.ui.graphics.vector.ImageVector

interface SettingsSectionProvider {
    fun sections(): List<SettingsSection>
}

data class SettingsSection(
    val id: String,
    val title: String,
    val rows: List<SettingsRowSpec>,
)

data class SettingsRowSpec(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector? = null,
    val value: String? = null,
    val enabled: Boolean = true,
)
