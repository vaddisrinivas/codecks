package io.codecks.ui.theme

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.codecks.widget.TrackpadWidgetProvider

data class ThemeSystemSurfaceColors(val primary: Int, val background: Int)

internal fun interface ThemeSystemSurfaceWriter {
    fun write(bundle: ThemeBundle): Boolean
}

class ThemeSystemSurfaceStore(context: Context) : ThemeSystemSurfaceWriter {
    private val preferences = context.getSharedPreferences("theme_system_surfaces", Context.MODE_PRIVATE)

    override fun write(bundle: ThemeBundle): Boolean {
        val primary = bundle.global[ThemeColorRole.Primary].value.toInt()
        val background = bundle.global[ThemeColorRole.Background].value.toInt()
        if (preferences.contains(PRIMARY) && preferences.getInt(PRIMARY, 0) == primary &&
            preferences.contains(BACKGROUND) && preferences.getInt(BACKGROUND, 0) == background
        ) {
            return true
        }
        return preferences.edit()
            .putInt(PRIMARY, primary)
            .putInt(BACKGROUND, background)
            .commit()
    }

    fun read(): ThemeSystemSurfaceColors = ThemeSystemSurfaceColors(
        primary = preferences.getInt(PRIMARY, ThemePresetCatalog.default[ThemeColorRole.Primary].value.toInt()),
        background = preferences.getInt(BACKGROUND, ThemePresetCatalog.default[ThemeColorRole.Background].value.toInt()),
    )

    private companion object {
        const val PRIMARY = "primary"
        const val BACKGROUND = "background"
    }
}

internal fun interface ThemeWidgetInventory {
    fun existingWidgetIds(): IntArray
}

internal fun interface ThemeWidgetUpdateSender {
    fun update(widgetIds: IntArray)
}

internal class ThemeExistingWidgetRefresher(
    private val inventory: ThemeWidgetInventory,
    private val sender: ThemeWidgetUpdateSender,
) {
    fun refresh(): Boolean {
        val ids = runCatching(inventory::existingWidgetIds).getOrDefault(IntArray(0))
        if (ids.isEmpty()) return false
        return runCatching { sender.update(ids.copyOf()) }.isSuccess
    }
}

internal fun interface ThemeActiveNotificationRefresher {
    fun refresh()
}

/** Process-local registry: refreshing a theme must never start the HID service. */
internal object ThemeActiveNotificationRegistry {
    private val ownershipLock = Any()
    private var active: ThemeActiveNotificationRefresher? = null

    /** Registers and refreshes under one ownership boundary, closing the foreground-start race. */
    fun registerAndRefresh(refresher: ThemeActiveNotificationRefresher): Boolean = synchronized(ownershipLock) {
        active = refresher
        runCatching(refresher::refresh).isSuccess
    }

    fun unregister(refresher: ThemeActiveNotificationRefresher) {
        synchronized(ownershipLock) {
            if (active === refresher) active = null
        }
    }

    fun refreshIfActive(): Boolean = synchronized(ownershipLock) {
        val refresher = active ?: return@synchronized false
        runCatching(refresher::refresh).isSuccess
    }
}

internal data class ThemeSystemPropagationReceipt(
    val mirrorUpdated: Boolean,
    val existingWidgetsUpdated: Boolean,
    val activeNotificationUpdated: Boolean,
)

internal class ThemeSystemSurfacePropagator(
    private val writer: ThemeSystemSurfaceWriter,
    private val widgets: ThemeExistingWidgetRefresher,
    private val notificationRefresh: () -> Boolean,
) {
    constructor(context: Context) : this(
        writer = ThemeSystemSurfaceStore(context.applicationContext),
        widgets = androidWidgetRefresher(context.applicationContext),
        notificationRefresh = ThemeActiveNotificationRegistry::refreshIfActive,
    )

    /** Load reconciliation is deliberately inert beyond repairing the local color mirror. */
    fun reconcilePersisted(bundle: ThemeBundle): Boolean = runCatching { writer.write(bundle) }.getOrDefault(false)

    /** Apply propagation is ordered so every consumer reads the newly mirrored colors. */
    fun propagateApplied(bundle: ThemeBundle): ThemeSystemPropagationReceipt {
        val mirrored = reconcilePersisted(bundle)
        if (!mirrored) return ThemeSystemPropagationReceipt(false, false, false)
        return ThemeSystemPropagationReceipt(
            mirrorUpdated = true,
            existingWidgetsUpdated = widgets.refresh(),
            activeNotificationUpdated = runCatching(notificationRefresh).getOrDefault(false),
        )
    }
}

private fun androidWidgetRefresher(context: Context): ThemeExistingWidgetRefresher {
    val component = ComponentName(context, TrackpadWidgetProvider::class.java)
    return ThemeExistingWidgetRefresher(
        inventory = ThemeWidgetInventory {
            AppWidgetManager.getInstance(context).getAppWidgetIds(component)
        },
        sender = ThemeWidgetUpdateSender { ids ->
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(component)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        },
    )
}
