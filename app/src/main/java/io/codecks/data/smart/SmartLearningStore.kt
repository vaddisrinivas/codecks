package io.codecks.data.smart

import android.content.Context
import io.codecks.domain.smart.SmartFeedback
import io.codecks.domain.smart.SmartFeedbackSummary
import io.codecks.domain.smart.SmartFeedbackType
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartSurface
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.smartCandidateId
import io.codecks.domain.smart.smartTransitionKey
import org.json.JSONArray
import org.json.JSONObject

private const val SMART_PREFS = "codecks.smart.learning"
private const val KEY_EVENTS = "events"

class SmartLearningStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(SMART_PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun record(feedback: SmartFeedback) {
        synchronized(lock) {
            val events = (readEventsAndMigrate() + feedback)
                .sortedBy { it.atMillis }
                .takeLast(SmartLearningCodec.MAX_EVENTS)
            preferences.edit().putString(KEY_EVENTS, SmartLearningCodec.encode(events)).apply()
        }
    }

    fun summary(nowMillis: Long = System.currentTimeMillis()): SmartFeedbackSummary =
        synchronized(lock) {
            SmartLearningCodec.summary(readEventsAndMigrate(), nowMillis)
        }

    fun clear() {
        synchronized(lock) {
            preferences.edit().remove(KEY_EVENTS).apply()
        }
    }

    private fun readEventsAndMigrate(): List<SmartFeedback> {
        val raw = preferences.getString(KEY_EVENTS, null)
        val migrated = SmartLearningCodec.migrateToCurrent(raw)
        if (migrated != null) {
            preferences.edit().putString(KEY_EVENTS, migrated).apply()
        }
        return SmartLearningCodec.decode(migrated ?: raw)
    }
}

object SmartLearningCodec {
    const val SCHEMA_VERSION = 2
    private const val LEGACY_SCHEMA_VERSION = 1
    const val MAX_EVENTS = 200
    const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    const val MAX_TRANSITION_GAP_MS = 5L * 60L * 1000L

    fun encode(events: List<SmartFeedback>): String =
        JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "events",
                JSONArray().apply {
                    events.takeLast(MAX_EVENTS).forEach { event ->
                        put(
                            JSONObject()
                                .put("candidateId", event.candidateId)
                                .put("actionId", event.actionId.orEmpty())
                                .put("appKey", event.appKey?.value.orEmpty())
                                .put("surface", event.surface.name)
                                .put("macId", event.macId?.value.orEmpty())
                                .put("type", event.type.name)
                                .put("success", event.success)
                                .put("coarseHourBucket", event.coarseHourBucket)
                                .put("contextKeys", JSONArray(event.contextKeys.sorted()))
                                .put("atMillis", event.atMillis),
                        )
                    }
                },
            )
            .toString()

    fun migrateToCurrent(raw: String?): String? =
        runCatching {
            val root = JSONObject(raw?.takeIf(String::isNotBlank) ?: return null)
            val rawVersion = root.opt("schemaVersion")
            val version = when {
                rawVersion == null || rawVersion == JSONObject.NULL -> LEGACY_SCHEMA_VERSION
                rawVersion is Number && rawVersion.toDouble() == rawVersion.toInt().toDouble() -> rawVersion.toInt()
                else -> return null
            }
            if (version != LEGACY_SCHEMA_VERSION || root.optJSONArray("events") == null) return null
            encode(decode(raw))
        }.getOrNull()

    fun decode(raw: String?): List<SmartFeedback> =
        runCatching {
            val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: return emptyList())
            val rawSchemaVersion = root.opt("schemaVersion")
            val schemaVersion = when {
                rawSchemaVersion == null || rawSchemaVersion == JSONObject.NULL -> LEGACY_SCHEMA_VERSION
                rawSchemaVersion is Number &&
                    rawSchemaVersion.toDouble() == rawSchemaVersion.toInt().toDouble() -> rawSchemaVersion.toInt()
                else -> return emptyList()
            }
            if (schemaVersion !in setOf(LEGACY_SCHEMA_VERSION, SCHEMA_VERSION)) return emptyList()
            val array = root.optJSONArray("events") ?: JSONArray()
            List(array.length()) { index -> array.optJSONObject(index) }
                .mapNotNull { json ->
                    when (schemaVersion) {
                        LEGACY_SCHEMA_VERSION -> json?.let(::decodeLegacyV1Event)
                        SCHEMA_VERSION -> json?.let(::decodeV2Event)
                        else -> null
                    }
                }
        }.getOrDefault(emptyList())

    private fun decodeV2Event(json: JSONObject): SmartFeedback? {
        val candidateId = json.optString("candidateId")
        val actionId = json.optString("actionId").takeIf(String::isNotBlank)
        val type = runCatching { SmartFeedbackType.valueOf(json.optString("type")) }.getOrNull()
        val surface = runCatching { SmartSurface.valueOf(json.optString("surface")) }.getOrNull()
        if (candidateId.isBlank() || type == null || surface == null) return null
        return SmartFeedback(
            candidateId = candidateId,
            actionId = actionId,
            appKey = json.optionalSmartAppKey(),
            surface = surface,
            macId = json.optionalSmartMacId(),
            type = type,
            success = json.opt("success") as? Boolean,
            coarseHourBucket = json.optInt("coarseHourBucket"),
            contextKeys = json.safeContextKeys(),
            atMillis = json.optLong("atMillis"),
        )
    }

    private fun decodeLegacyV1Event(json: JSONObject): SmartFeedback? {
        val legacyCandidateId = json.optString("candidateId")
        val actionId = json.optString("actionId").takeIf(String::isNotBlank)
        val appKey = json.optionalSmartAppKey()
        if (legacyCandidateId.isBlank() || actionId == null) return null

        // v1 only shipped Smart Deck and did not persist surface or Mac ID.
        // Prefer the candidate/context surface when recoverable; otherwise Deck is the
        // intentional compatibility default. A missing Mac remains null so old events
        // cannot create newly context-scoped transitions.
        val legacyContextKeys = json.safeContextKeys(includeMac = false)
        val surface = legacySurface(legacyCandidateId, legacyContextKeys)
        val type = when (json.optString("type")) {
            "Pin" -> SmartFeedbackType.Pin
            "Why" -> SmartFeedbackType.Why
            "Success" -> SmartFeedbackType.Success
            "Failure" -> SmartFeedbackType.Failure
            "NeverForApp" -> {
                // This old control meant persisted app-scoped suppression. Its candidate
                // encoded the surface, so migrate it to the closest narrower v2 meaning.
                if (appKey == null) return null
                SmartFeedbackType.SuppressHere
            }
            "Run" -> return null // v1 Run was deliberately score-neutral.
            "Hide" -> return null // "Hide for now" is memory-only in v2; do not make it persistent.
            else -> return null
        }
        return SmartFeedback(
            candidateId = smartCandidateId(surface, appKey, actionId),
            actionId = actionId,
            appKey = appKey,
            surface = surface,
            macId = null,
            type = type,
            success = json.opt("success") as? Boolean,
            coarseHourBucket = json.optInt("coarseHourBucket"),
            contextKeys = legacyContextKeys,
            atMillis = json.optLong("atMillis"),
        )
    }

    private fun legacySurface(candidateId: String, contextKeys: Set<String>): SmartSurface {
        val candidateSurface = candidateId
            .takeIf { it.startsWith("smart:") }
            ?.removePrefix("smart:")
            ?.substringBefore(':')
        val contextSurface = contextKeys
            .firstOrNull { it.startsWith("surface:") }
            ?.substringAfter("surface:")
        val token = candidateSurface ?: contextSurface
        if (token.equals("home", ignoreCase = true)) return SmartSurface.Deck
        return SmartSurface.entries.firstOrNull { it.name.equals(token, ignoreCase = true) }
            ?: SmartSurface.Deck
    }

    private fun JSONObject.optionalSmartAppKey(): SmartAppKey? =
        optString("appKey").takeIf(String::isNotBlank)?.let { runCatching { SmartAppKey(it) }.getOrNull() }

    private fun JSONObject.optionalSmartMacId(): SmartMacId? =
        optString("macId").takeIf(String::isNotBlank)?.let { runCatching { SmartMacId(it) }.getOrNull() }

    private fun JSONObject.safeContextKeys(includeMac: Boolean = true): Set<String> =
        optJSONArray("contextKeys")?.let { keys ->
            List(keys.length()) { keys.optString(it) }
                .filter { key ->
                    SAFE_CONTEXT_KEY_PREFIXES.any(key::startsWith) &&
                        (includeMac || !key.startsWith("mac:"))
                }
                .toSet()
        }.orEmpty()

    fun summary(events: List<SmartFeedback>, nowMillis: Long): SmartFeedbackSummary {
        val fresh = events.filter { nowMillis - it.atMillis <= RETENTION_MS }
        val visibleOrder = fresh.sortedBy { it.atMillis }
        val suppressed = visibleOrder
            .filter { it.type == SmartFeedbackType.SuppressHere && it.actionId != null }
            .mapNotNull { event ->
                val actionId = event.actionId ?: return@mapNotNull null
                smartCandidateId(event.surface, event.appKey, actionId)
            }
            .toSet()
        val scores = mutableMapOf<String, Int>()
        val transitions = mutableMapOf<String, Int>()
        val never = visibleOrder
            .filter { it.type == SmartFeedbackType.NeverGlobal && it.actionId != null }
            .mapNotNull { it.actionId }
            .toSet()
        var previousSuccess: SmartFeedback? = null
        visibleOrder.forEach { event ->
            val id = event.actionId ?: return@forEach
            scores[id] = (scores[id] ?: 0) + when (event.type) {
                SmartFeedbackType.Pin -> 8
                SmartFeedbackType.Success -> 6
                SmartFeedbackType.Failure -> -8
                SmartFeedbackType.SuppressHere -> 0
                SmartFeedbackType.NeverGlobal -> 0
                SmartFeedbackType.Why -> 1
            }
            if (event.type == SmartFeedbackType.Success) {
                previousSuccess?.let { previous ->
                    if (
                        previous.actionId != null &&
                        previous.actionId != id &&
                        previous.atMillis > 0 &&
                        event.atMillis - previous.atMillis in 1..MAX_TRANSITION_GAP_MS &&
                        previous.surface == event.surface &&
                        previous.appKey != null &&
                        event.appKey != null &&
                        previous.appKey == event.appKey &&
                        previous.macId != null &&
                        event.macId != null &&
                        previous.macId == event.macId
                    ) {
                        val key = smartTransitionKey(
                            surface = event.surface,
                            appKey = event.appKey,
                            macId = event.macId,
                            previousActionId = previous.actionId,
                            nextActionId = id,
                        )
                        transitions[key] = (transitions[key] ?: 0) + 10
                    }
                }
                previousSuccess = event
            }
        }
        return SmartFeedbackSummary(
            suppressedContextActionKeys = suppressed,
            globallySuppressedActionIds = never,
            actionScores = scores,
            transitionScores = transitions,
        )
    }

    private val SAFE_CONTEXT_KEY_PREFIXES = listOf("surface:", "mac:", "macApp:", "phone:", "hour:")
}
