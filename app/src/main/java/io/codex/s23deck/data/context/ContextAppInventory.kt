package io.codex.s23deck.data.context

import android.content.Context
import android.content.Intent
import io.codex.s23deck.domain.context.ContextApp

class ContextAppInventory(
    private val context: Context,
) {
    fun launchableApps(): List<ContextApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager
            .queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                if (label.isBlank()) return@mapNotNull null
                ContextApp(packageName = packageName, label = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
