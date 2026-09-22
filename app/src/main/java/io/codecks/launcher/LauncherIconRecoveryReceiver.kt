package io.codecks.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LauncherIconRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in SUPPORTED_ACTIONS) return
        runCatching { LauncherIconManager(context.applicationContext).reconcile() }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
