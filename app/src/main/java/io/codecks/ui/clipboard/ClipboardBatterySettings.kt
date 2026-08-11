package io.codecks.ui.clipboard

import android.content.Context
import android.content.Intent
import android.provider.Settings

internal fun openClipboardBatterySaverSettings(context: Context) {
    val batterySaverIntent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(batterySaverIntent) }
        .recoverCatching {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}
