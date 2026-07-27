package io.codecks.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import io.codecks.R
import io.codecks.ui.mouse.lockscreen.TrackpadEntryActivity

class TrackpadWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.trackpad_widget).apply {
                setOnClickPendingIntent(R.id.trackpad_widget_root, TrackpadEntryActivity.widgetPendingIntent(context))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
