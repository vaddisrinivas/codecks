package io.codex.s23deck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.RemoteViews
import io.codex.s23deck.data.context.ContextDeckWidgetState
import io.codex.s23deck.data.context.WidgetContextApp

class DeckWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = ContextDeckWidgetState.load(context)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_deckbridge)
            val suggestions = snapshot.suggestions + listOf("Deck", "Trackpad", "AI", "Automations")
            views.setTextViewText(R.id.widget_title, snapshot.title)
            views.setTextViewText(R.id.widget_subtitle, snapshot.subtitle)
            val apps = snapshot.apps
            views.setViewVisibility(R.id.widget_app_grid, if (apps.isEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, if (apps.isEmpty()) View.VISIBLE else View.GONE)
            views.setTextViewText(
                R.id.widget_empty,
                if (apps.isEmpty()) "No clear app match yet\nOpen Context Deck to inspect context" else "",
            )
            WidgetSlots.forEachIndexed { index, slot ->
                bindAppSlot(
                    context = context,
                    views = views,
                    rootId = slot.rootId,
                    iconId = slot.iconId,
                    labelId = slot.labelId,
                    app = apps.getOrNull(index),
                    fallbackLabel = suggestions.getOrElse(index) { "Context" },
                    requestCode = 6101 + index,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_root, launch(context, "context", 6100))
            manager.updateAppWidget(id, views)
        }
    }

    private fun bindAppSlot(
        context: Context,
        views: RemoteViews,
        rootId: Int,
        iconId: Int,
        labelId: Int,
        app: WidgetContextApp?,
        fallbackLabel: String,
        requestCode: Int,
    ) {
        views.setTextViewText(labelId, app?.label ?: fallbackLabel)
        if (app != null) {
            views.setViewVisibility(rootId, View.VISIBLE)
            loadIconBitmap(context, app.packageName)?.let { bitmap ->
                views.setImageViewBitmap(iconId, bitmap)
            }
            views.setOnClickPendingIntent(rootId, launchPackage(context, app.packageName, app.label, requestCode))
        } else {
            views.setViewVisibility(rootId, View.GONE)
            views.setOnClickPendingIntent(rootId, launch(context, "context", requestCode))
        }
    }

    private fun launch(context: Context, destination: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetLaunchActivity::class.java)
            .putExtra(WidgetLaunchActivity.EXTRA_DESTINATION, destination)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun launchPackage(context: Context, packageName: String, label: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetLaunchActivity::class.java)
            .putExtra(WidgetLaunchActivity.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(WidgetLaunchActivity.EXTRA_LABEL, label)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun loadIconBitmap(context: Context, packageName: String): Bitmap? =
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96)
        }.getOrNull()

    private fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    private data class WidgetSlot(
        val rootId: Int,
        val iconId: Int,
        val labelId: Int,
    )

    private companion object {
        val WidgetSlots = listOf(
            WidgetSlot(R.id.widget_app_1, R.id.widget_app_icon_1, R.id.widget_app_label_1),
            WidgetSlot(R.id.widget_app_2, R.id.widget_app_icon_2, R.id.widget_app_label_2),
            WidgetSlot(R.id.widget_app_3, R.id.widget_app_icon_3, R.id.widget_app_label_3),
            WidgetSlot(R.id.widget_app_4, R.id.widget_app_icon_4, R.id.widget_app_label_4),
            WidgetSlot(R.id.widget_app_5, R.id.widget_app_icon_5, R.id.widget_app_label_5),
            WidgetSlot(R.id.widget_app_6, R.id.widget_app_icon_6, R.id.widget_app_label_6),
            WidgetSlot(R.id.widget_app_7, R.id.widget_app_icon_7, R.id.widget_app_label_7),
            WidgetSlot(R.id.widget_app_8, R.id.widget_app_icon_8, R.id.widget_app_label_8),
            WidgetSlot(R.id.widget_app_9, R.id.widget_app_icon_9, R.id.widget_app_label_9),
            WidgetSlot(R.id.widget_app_10, R.id.widget_app_icon_10, R.id.widget_app_label_10),
            WidgetSlot(R.id.widget_app_11, R.id.widget_app_icon_11, R.id.widget_app_label_11),
            WidgetSlot(R.id.widget_app_12, R.id.widget_app_icon_12, R.id.widget_app_label_12),
            WidgetSlot(R.id.widget_app_13, R.id.widget_app_icon_13, R.id.widget_app_label_13),
            WidgetSlot(R.id.widget_app_14, R.id.widget_app_icon_14, R.id.widget_app_label_14),
            WidgetSlot(R.id.widget_app_15, R.id.widget_app_icon_15, R.id.widget_app_label_15),
            WidgetSlot(R.id.widget_app_16, R.id.widget_app_icon_16, R.id.widget_app_label_16),
        )
    }
}
