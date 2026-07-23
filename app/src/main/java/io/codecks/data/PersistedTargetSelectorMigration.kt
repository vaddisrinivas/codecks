package io.codecks.data

import org.json.JSONArray
import org.json.JSONObject

internal sealed interface PersistedTargetSelectorMigration {
    data object Unchanged : PersistedTargetSelectorMigration
    data class Migrated(
        val payload: String,
        val count: Int,
    ) : PersistedTargetSelectorMigration
    data object Undecodable : PersistedTargetSelectorMigration
}

internal fun migrateDeckTargetSelectorPayload(
    raw: String,
    legacyIds: Map<String, String>,
): PersistedTargetSelectorMigration {
    if (legacyIds.isEmpty()) return PersistedTargetSelectorMigration.Unchanged
    val trimmed = raw.trimStart()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        return PersistedTargetSelectorMigration.Unchanged
    }
    return runCatching {
        val root = if (trimmed.startsWith("{")) JSONObject(raw) else null
        val items = root?.getJSONArray("items") ?: JSONArray(raw)
        val migratedCount = migrateTargetSelectors(items, legacyIds) { item ->
            listOf(item)
        }
        if (migratedCount == 0) {
            PersistedTargetSelectorMigration.Unchanged
        } else {
            PersistedTargetSelectorMigration.Migrated(
                payload = (root ?: items).toString(),
                count = migratedCount,
            )
        }
    }.getOrDefault(PersistedTargetSelectorMigration.Undecodable)
}

internal fun migrateAutomationTargetSelectorPayload(
    raw: String,
    legacyIds: Map<String, String>,
): PersistedTargetSelectorMigration {
    if (legacyIds.isEmpty()) return PersistedTargetSelectorMigration.Unchanged
    return runCatching {
        val trimmed = raw.trimStart()
        val root = if (trimmed.startsWith("{")) JSONObject(raw) else null
        val items = root?.getJSONArray("items") ?: JSONArray(raw)
        val migratedCount = migrateTargetSelectors(items, legacyIds) { recipe ->
            val steps = recipe.getJSONArray("steps")
            buildList {
                repeat(steps.length()) { index ->
                    add(steps.getJSONObject(index))
                }
            }
        }
        if (migratedCount == 0) {
            PersistedTargetSelectorMigration.Unchanged
        } else {
            PersistedTargetSelectorMigration.Migrated(
                payload = (root ?: items).toString(),
                count = migratedCount,
            )
        }
    }.getOrDefault(PersistedTargetSelectorMigration.Undecodable)
}

private fun migrateTargetSelectors(
    items: JSONArray,
    legacyIds: Map<String, String>,
    targetOwners: (JSONObject) -> List<JSONObject>,
): Int {
    var migratedCount = 0
    repeat(items.length()) { index ->
        val item = items.getJSONObject(index)
        targetOwners(item).forEach { owner ->
            if (!owner.has("target")) return@forEach
            val target = owner.getJSONObject("target")
            if (target.optString("type") != "device") return@forEach
            val oldId = target.getString("id")
            val newId = legacyIds[oldId] ?: return@forEach
            if (newId == oldId) return@forEach
            target.put("id", newId)
            migratedCount += 1
        }
    }
    return migratedCount
}
