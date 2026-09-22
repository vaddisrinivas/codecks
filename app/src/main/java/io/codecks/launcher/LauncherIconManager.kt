package io.codecks.launcher

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import io.codecks.R

enum class LauncherIcon(
    val persistedValue: String,
    val label: String,
    val description: String,
    val componentClassName: String,
    @param:DrawableRes val widgetDrawableRes: Int,
    @param:DrawableRes val notificationDrawableRes: Int,
) {
    RobotFace(
        persistedValue = "robot_face",
        label = "Robot face",
        description = "The original Codecks robot in its illuminated control grid.",
        componentClassName = "io.codecks.launcher.RobotFaceLauncher",
        widgetDrawableRes = R.drawable.ic_widget_robot_face,
        notificationDrawableRes = R.drawable.ic_notification_robot_face,
    ),
    RobotGrid(
        persistedValue = "robot_grid",
        label = "Robot grid",
        description = "A bolder robot controller with a compact four-key grid.",
        componentClassName = "io.codecks.launcher.RobotGridLauncher",
        widgetDrawableRes = R.drawable.ic_widget_robot_grid,
        notificationDrawableRes = R.drawable.ic_notification_robot_grid,
    ),
    PointerGrid(
        persistedValue = "pointer_grid",
        label = "Pointer grid",
        description = "A precise pointer crossing the Codecks button matrix.",
        componentClassName = "io.codecks.launcher.PointerGridLauncher",
        widgetDrawableRes = R.drawable.ic_widget_pointer_grid,
        notificationDrawableRes = R.drawable.ic_notification_pointer_grid,
    ),
    MinimalGreen(
        persistedValue = "minimal_green",
        label = "Minimal green",
        description = "One bright-green control and a clean white pointer.",
        componentClassName = "io.codecks.launcher.MinimalGreenLauncher",
        widgetDrawableRes = R.drawable.ic_widget_minimal_green,
        notificationDrawableRes = R.drawable.ic_notification_minimal_green,
    ),
}

internal object LauncherIconPolicy {
    fun migratePersisted(raw: String?): LauncherIcon? = when (raw?.trim()) {
        null, "" -> null
        "robot", "robot_face", "RobotFace" -> LauncherIcon.RobotFace
        "robot_grid", "RobotGrid" -> LauncherIcon.RobotGrid
        "pointer", "pointer_grid", "PointerGrid" -> LauncherIcon.PointerGrid
        "green", "minimal_green", "MinimalGreen" -> LauncherIcon.MinimalGreen
        else -> null
    }

    fun recoveryTarget(raw: String?, enabled: Set<LauncherIcon>): LauncherIcon =
        migratePersisted(raw)
            ?: enabled.singleOrNull()
            ?: LauncherIcon.RobotFace

    fun mayDisableAlternatives(replacementConfirmedEnabled: Boolean): Boolean =
        replacementConfirmedEnabled
}

internal interface LauncherComponentBackend {
    fun isEnabled(icon: LauncherIcon): Boolean
    fun setEnabled(icon: LauncherIcon, enabled: Boolean)
}

internal interface LauncherSelectionStore {
    fun read(): String?
    fun write(value: String): Boolean
}

class LauncherIconManager internal constructor(
    private val backend: LauncherComponentBackend,
    private val store: LauncherSelectionStore,
    private val onSelectionApplied: () -> Unit = {},
) {
    constructor(context: Context) : this(
        backend = AndroidLauncherComponentBackend(context),
        store = AndroidLauncherSelectionStore(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
        onSelectionApplied = { refreshWidgets(context) },
    )

    fun current(): LauncherIcon = runCatching {
        currentFrom(enabledIcons())
    }.getOrDefault(LauncherIcon.RobotFace)

    @Synchronized
    fun reconcile(): LauncherIcon = runCatching {
        val snapshot = enabledIcons()
        val target = currentFrom(snapshot)
        if (!snapshot.reliable) target else applySelection(target).getOrElse { recoverDefault() }
    }.getOrElse { LauncherIcon.RobotFace }

    @Synchronized
    fun select(icon: LauncherIcon): Result<LauncherIcon> =
        applySelection(icon).recoverCatching { error ->
            recoverDefault()
            throw error
        }

    private fun applySelection(icon: LauncherIcon): Result<LauncherIcon> = runCatching {
        // Enable first. A crash can temporarily leave two launchers, never zero.
        backend.setEnabled(icon, true)
        check(backend.isEnabled(icon)) { "Could not enable ${icon.persistedValue} launcher" }
        check(store.write(icon.persistedValue)) { "Could not persist launcher selection" }
        LauncherIcon.entries.filterNot { it == icon }.forEach { backend.setEnabled(it, false) }
        val converged = enabledIcons()
        check(converged.reliable && converged.icons == setOf(icon)) {
            "Launcher selection did not converge"
        }
        runCatching(onSelectionApplied)
        icon
    }

    private fun recoverDefault(): LauncherIcon {
        val fallback = LauncherIcon.RobotFace
        val previouslyEnabled = enabledIcons()
        if (!previouslyEnabled.reliable) return currentFrom(previouslyEnabled)
        val fallbackEnabled = runCatching {
            backend.setEnabled(fallback, true)
            backend.isEnabled(fallback)
        }.getOrDefault(false)

        // Never disable a surviving launcher unless its replacement is confirmed enabled.
        if (!LauncherIconPolicy.mayDisableAlternatives(fallbackEnabled)) {
            return previouslyEnabled.icons.singleOrNull()
                ?: previouslyEnabled.icons.firstOrNull()
                ?: fallback
        }

        runCatching { store.write(fallback.persistedValue) }
        LauncherIcon.entries.filterNot { it == fallback }.forEach { icon ->
            runCatching { backend.setEnabled(icon, false) }
        }
        runCatching(onSelectionApplied)
        return fallback
    }

    private fun currentFrom(snapshot: EnabledSnapshot): LauncherIcon {
        val persisted = runCatching { store.read() }.getOrNull()
        if (!snapshot.reliable) {
            return LauncherIconPolicy.migratePersisted(persisted)
                ?: snapshot.icons.firstOrNull()
                ?: LauncherIcon.RobotFace
        }
        return LauncherIconPolicy.recoveryTarget(persisted, snapshot.icons)
    }

    private fun enabledIcons(): EnabledSnapshot {
        val enabled = linkedSetOf<LauncherIcon>()
        LauncherIcon.entries.forEach { icon ->
            val state = runCatching { backend.isEnabled(icon) }.getOrElse {
                return EnabledSnapshot(enabled, reliable = false)
            }
            if (state) enabled += icon
        }
        return EnabledSnapshot(enabled, reliable = true)
    }

    private data class EnabledSnapshot(val icons: Set<LauncherIcon>, val reliable: Boolean)

    private companion object {
        const val PREFERENCES_NAME = "launcher_icon_state"
    }
}

private class AndroidLauncherComponentBackend(context: Context) : LauncherComponentBackend {
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    override fun isEnabled(icon: LauncherIcon): Boolean =
        when (packageManager.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            -> false
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == LauncherIcon.RobotFace
            else -> false
        }

    override fun setEnabled(icon: LauncherIcon, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            component(icon),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun component(icon: LauncherIcon): ComponentName =
        ComponentName(packageName, icon.componentClassName)
}

private class AndroidLauncherSelectionStore(
    private val preferences: SharedPreferences,
) : LauncherSelectionStore {
    override fun read(): String? = preferences.getString(KEY_SELECTION, null)

    override fun write(value: String): Boolean =
        preferences.edit().putString(KEY_SELECTION, value).commit()

    private companion object {
        const val KEY_SELECTION = "selection_v2"
    }
}

private fun refreshWidgets(context: Context) {
    val widgetProvider = ComponentName(context.packageName, WIDGET_PROVIDER_CLASS)
    val widgetManager = AppWidgetManager.getInstance(context)
    val widgetIds = widgetManager.getAppWidgetIds(widgetProvider)
    if (widgetIds.isEmpty()) return
    context.sendBroadcast(
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .setComponent(widgetProvider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds),
    )
}

private const val WIDGET_PROVIDER_CLASS = "io.codecks.widget.TrackpadWidgetProvider"
