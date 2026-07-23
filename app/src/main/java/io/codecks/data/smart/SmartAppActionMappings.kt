package io.codecks.data.smart

import android.content.Context
import io.codecks.domain.smart.SmartAppActionMapping
import org.json.JSONArray
import org.json.JSONObject

object SmartAppActionMappings {
    const val ASSET_PATH = "smart_app_action_mappings.json"

    fun load(context: Context): List<SmartAppActionMapping> = runCatching {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        parse(raw)
    }.getOrElse {
        emptyList()
    }

    internal fun parse(raw: String): List<SmartAppActionMapping> = runCatching {
        val root = JSONObject(raw)
        val schemaVersion = root.opt("schemaVersion") as? Number
        if (schemaVersion == null ||
            schemaVersion.toInt() != SUPPORTED_SCHEMA_VERSION ||
            schemaVersion.toDouble() != SUPPORTED_SCHEMA_VERSION.toDouble()
        ) {
            return@runCatching emptyList()
        }
        val mappings = root.optJSONArray("mappings") ?: JSONArray()
        buildList {
            repeat(mappings.length()) { index ->
                val item = mappings.optJSONObject(index) ?: return@repeat
                val appTokens = parseTokenSet(item.optJSONArray("appTokens"))
                val actionTokens = parseTokenSet(item.optJSONArray("actionTokens"))
                val reason = item.optString("reason").takeIf(String::isNotBlank) ?: "Useful for active app context"
                val score = item.optInt("score")
                if (appTokens.isNotEmpty() && actionTokens.isNotEmpty() && score > 0) {
                    add(
                        SmartAppActionMapping(
                            appTokens = appTokens,
                            actionTokens = actionTokens,
                            reason = reason,
                            score = score,
                        ),
                    )
                }
            }
        }
    }.getOrElse {
        emptyList()
    }

    private fun parseTokenSet(tokens: JSONArray?): Set<String> = tokens
        ?.let { array ->
            List(array.length()) { index ->
                array.optString(index)
            }.mapNotNull { token ->
                token
                    .lowercase()
                    .trim()
                    .takeIf(String::isNotBlank)
            }.toSet()
        }.orEmpty()

    private const val SUPPORTED_SCHEMA_VERSION = 1
}
