package io.codecks.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import io.codecks.R
import io.codecks.launcher.LauncherIconManager
import io.codecks.ui.mouse.lockscreen.TrackpadEntryActivity
import io.codecks.ui.theme.ThemeSystemSurfaceStore

class TrackpadWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val theme = ThemeSystemSurfaceStore(context).read()
            val views = RemoteViews(context.packageName, R.layout.trackpad_widget).apply {
                setInt(R.id.trackpad_widget_root, "setBackgroundColor", theme.background)
                setImageViewResource(
                    R.id.trackpad_widget_icon,
                    LauncherIconManager(context.applicationContext).current().widgetDrawableRes,
                )
                setInt(R.id.trackpad_widget_icon, "setColorFilter", theme.primary)
                setOnClickPendingIntent(R.id.trackpad_widget_root, TrackpadEntryActivity.widgetPendingIntent(context))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
