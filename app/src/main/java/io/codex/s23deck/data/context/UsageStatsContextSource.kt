package io.codex.s23deck.data.context

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Process
import io.codex.s23deck.domain.context.ContextApp
import io.codex.s23deck.domain.context.RecentContextApp

class UsageStatsContextSource(
    private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @SuppressLint("MissingPermission")
    fun recentApps(
        availableApps: List<ContextApp>,
        lookbackMillis: Long = DEFAULT_LOOKBACK_MS,
        limit: Int = 12,
    ): List<RecentContextApp> {
        if (!hasUsageAccess()) return emptyList()
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val byPackage = availableApps.associateBy { it.packageName }
        return usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - lookbackMillis, now)
            .orEmpty()
            .asSequence()
            .filter { it.totalTimeInForeground > 0L && it.lastTimeUsed > 0L }
            .mapNotNull { stat -> stat.toRecentContextApp(byPackage) }
            .sortedWith(
                compareByDescending<RecentContextApp> { it.lastUsedAtMillis }
                    .thenByDescending { it.foregroundMillis },
            )
            .distinctBy { it.packageName }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    private fun UsageStats.toRecentContextApp(byPackage: Map<String, ContextApp>): RecentContextApp? {
        val app = byPackage[packageName] ?: return null
        return RecentContextApp(
            packageName = packageName,
            label = app.label,
            lastUsedAtMillis = lastTimeUsed,
            foregroundMillis = totalTimeInForeground,
        )
    }

    private companion object {
        const val DEFAULT_LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}
