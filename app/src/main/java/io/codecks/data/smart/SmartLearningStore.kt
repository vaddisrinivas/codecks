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
import io.codecks.data.persistence.BoundedPayload
import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.valueForMutation

private const val SMART_PREFS = "codecks.smart.learning"
private const val KEY_EVENTS = "events"

class SmartLearningStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(SMART_PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun record(feedback: SmartFeedback) {
        synchronized(lock) {
            val events = (readEventsForMutation() + feedback)
                .sortedBy { it.atMillis }
                .takeLast(SmartLearningCodec.MAX_EVENTS)
            check(preferences.edit().putString(KEY_EVENTS, SmartLearningCodec.encode(events)).commit()) {
                "Smart learning commit failed"
            }
        }
    }

    fun summary(nowMillis: Long = System.currentTimeMillis()): SmartFeedbackSummary =
        synchronized(lock) {
            SmartLearningCodec.summary(readEvents(), nowMillis)
        }

    fun clear() {
        synchronized(lock) {
            SmartLearningCodec.read(preferences.getString(KEY_EVENTS, null))
                .valueForMutation("Smart learning") { emptyList() }
            check(preferences.edit().remove(KEY_EVENTS).commit()) { "Smart learning clear failed" }
        }
    }

    private fun readEvents(): List<SmartFeedback> =
        (SmartLearningCodec.read(preferences.getString(KEY_EVENTS, null)) as? PersistenceRead.Value)?.value.orEmpty()

    private fun readEventsForMutation(): List<SmartFeedback> {
        val raw = preferences.getString(KEY_EVENTS, null)
        val result = SmartLearningCodec.read(raw)
        val events = result.valueForMutation("Smart learning") { emptyList() }
        if (result is PersistenceRead.Value && result.migratedFrom != null) {
            check(preferences.edit().putString(KEY_EVENTS, SmartLearningCodec.encode(events)).commit()) {
                "Smart migration commit failed"
            }
        }
        return events
    }
}

object SmartLearningCodec {
    const val SCHEMA_VERSION = 2
    private const val LEGACY_SCHEMA_VERSION = 1
    const val MAX_EVENTS = 200
    const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    const val MAX_TRANSITION_GAP_MS = 5L * 60L * 1000L
    private const val MAX_BYTES = 512 * 1024

    fun encode(events: List<SmartFeedback>): String = BoundedPayload.utf8(
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
            .toString(),
        MAX_BYTES,
    )

    fun migrateToCurrent(raw: String?): String? =
        (read(raw) as? PersistenceRead.Value)
            ?.takeIf { it.migratedFrom == LEGACY_SCHEMA_VERSION }
            ?.let { encode(it.value) }

    fun decode(raw: String?): List<SmartFeedback> = (read(raw) as? PersistenceRead.Value)?.value.orEmpty()

    fun read(raw: String?): PersistenceRead<List<SmartFeedback>> {
        if (raw == null) return PersistenceRead.Missing
        if (raw.isBlank()) return PersistenceRead.Corrupt("blank")
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return PersistenceRead.Corrupt("oversized")
        return runCatching {
            val value = raw
            val root = JSONObject(value)
            val rawSchemaVersion = root.opt("schemaVersion")
            val schemaVersion = when {
                rawSchemaVersion == null || rawSchemaVersion == JSONObject.NULL -> LEGACY_SCHEMA_VERSION
                rawSchemaVersion is Number &&
                    rawSchemaVersion.toDouble() == rawSchemaVersion.toInt().toDouble() -> rawSchemaVersion.toInt()
                else -> return PersistenceRead.Corrupt("schema")
            }
            if (schemaVersion > SCHEMA_VERSION) return PersistenceRead.FutureVersion(schemaVersion, SCHEMA_VERSION)
            if (schemaVersion !in setOf(LEGACY_SCHEMA_VERSION, SCHEMA_VERSION)) return PersistenceRead.Corrupt("schema")
            val array = root.optJSONArray("events") ?: return PersistenceRead.Corrupt("events")
            if (array.length() > MAX_EVENTS) return PersistenceRead.Corrupt("count")
            val events = List(array.length()) { index -> requireNotNull(array.optJSONObject(index)) }
                .mapNotNull { json ->
                    when (schemaVersion) {
                        LEGACY_SCHEMA_VERSION -> {
                            if (json.optString("type") in setOf("Run", "Hide")) null
                            else requireNotNull(decodeLegacyV1Event(json))
                        }
                        SCHEMA_VERSION -> requireNotNull(decodeV2Event(json))
                        else -> error("unsupported")
                    }
                }
            PersistenceRead.Value(events, schemaVersion.takeIf { it != SCHEMA_VERSION })
        }.getOrElse { PersistenceRead.Corrupt("decode") }
    }

    private fun decodeV2Event(json: JSONObject): SmartFeedback? {
        val candidateId = json.optString("candidateId")
        val actionId = json.optString("actionId").takeIf(String::isNotBlank)
        val type = runCatching { SmartFeedbackType.valueOf(json.optString("type")) }.getOrNull()
        val surface = runCatching { SmartSurface.valueOf(json.optString("surface")) }.getOrNull()
        if (candidateId.isBlank() || type == null || surface == null) return null
        val rawSuccess = json.opt("success")
        val success = if (rawSuccess == null || rawSuccess === JSONObject.NULL) null
        else rawSuccess as? Boolean ?: return null
        val coarseHourBucket = json.strictInt("coarseHourBucket") ?: return null
        val atMillis = json.strictLong("atMillis") ?: return null
        val contextKeys = json.strictContextKeys() ?: return null
        val appKey = json.strictOptionalSmartAppKey() ?: return null
        val macId = json.strictOptionalSmartMacId() ?: return null
        return SmartFeedback(
            candidateId = candidateId,
            actionId = actionId,
            appKey = appKey.value,
            surface = surface,
            macId = macId.value,
            type = type,
            success = success,
            coarseHourBucket = coarseHourBucket,
            contextKeys = contextKeys,
            atMillis = atMillis,
        )
    }

    private fun decodeLegacyV1Event(json: JSONObject): SmartFeedback? {
        val legacyCandidateId = json.optString("candidateId")
        val actionId = json.optString("actionId").takeIf(String::isNotBlank)
        val appKey = (json.strictOptionalSmartAppKey() ?: return null).value
        if (legacyCandidateId.isBlank() || actionId == null) return null

        // v1 only shipped Smart Deck and did not persist surface or Mac ID.
        // Prefer the candidate/context surface when recoverable; otherwise Deck is the
        // intentional compatibility default. A missing Mac remains null so old events
        // cannot create newly context-scoped transitions.
        val legacyContextKeys = json.strictContextKeys(includeMac = false) ?: return null
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
            success = json.opt("success").let { raw ->
                if (raw == null || raw === JSONObject.NULL) null else raw as? Boolean ?: return null
            },
            coarseHourBucket = json.strictInt("coarseHourBucket") ?: return null,
            contextKeys = legacyContextKeys,
            atMillis = json.strictLong("atMillis") ?: return null,
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

    private data class OptionalValue<T>(val value: T?)

    private fun JSONObject.strictOptionalSmartAppKey(): OptionalValue<SmartAppKey>? {
        val raw = opt("appKey")
        if (raw == null || raw == JSONObject.NULL || raw == "") return OptionalValue(null)
        val value = raw as? String ?: return null
        return runCatching { OptionalValue(SmartAppKey(value)) }.getOrNull()
    }

    private fun JSONObject.strictOptionalSmartMacId(): OptionalValue<SmartMacId>? {
        val raw = opt("macId")
        if (raw == null || raw == JSONObject.NULL || raw == "") return OptionalValue(null)
        val value = raw as? String ?: return null
        return runCatching { OptionalValue(SmartMacId(value)) }.getOrNull()
    }

    private fun JSONObject.strictContextKeys(includeMac: Boolean = true): Set<String>? {
        val keys = optJSONArray("contextKeys") ?: return null
        val values = List(keys.length()) { index -> keys.opt(index) as? String ?: return null }
        if (includeMac && values.any { key -> SAFE_CONTEXT_KEY_PREFIXES.none(key::startsWith) }) return null
        return values.filter { key ->
            SAFE_CONTEXT_KEY_PREFIXES.any(key::startsWith) && (includeMac || !key.startsWith("mac:"))
        }.toSet()
    }

    private fun JSONObject.strictInt(name: String): Int? {
        val number = opt(name) as? Number ?: return null
        return number.toInt().takeIf { it.toDouble() == number.toDouble() }
    }

    private fun JSONObject.strictLong(name: String): Long? {
        val number = opt(name) as? Number ?: return null
        return number.toLong().takeIf { it.toDouble() == number.toDouble() }
    }

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
