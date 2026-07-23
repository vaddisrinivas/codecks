package io.codecks.data.smart

import android.content.Context
import io.codecks.domain.smart.SmartFeedback
import io.codecks.domain.smart.SmartFeedbackSummary
import io.codecks.domain.smart.SmartFeedbackType
import org.json.JSONArray
import org.json.JSONObject

private const val SMART_PREFS = "codecks.smart.learning"
private const val KEY_EVENTS = "events"

class SmartLearningStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(SMART_PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun record(feedback: SmartFeedback) {
        synchronized(lock) {
            val events = (SmartLearningCodec.decode(preferences.getString(KEY_EVENTS, null)) + feedback)
                .sortedBy { it.atMillis }
                .takeLast(SmartLearningCodec.MAX_EVENTS)
            preferences.edit().putString(KEY_EVENTS, SmartLearningCodec.encode(events)).apply()
        }
    }

    fun summary(nowMillis: Long = System.currentTimeMillis()): SmartFeedbackSummary =
        synchronized(lock) {
            SmartLearningCodec.summary(SmartLearningCodec.decode(preferences.getString(KEY_EVENTS, null)), nowMillis)
        }

    fun clear() {
        synchronized(lock) {
            preferences.edit().remove(KEY_EVENTS).apply()
        }
    }
}

object SmartLearningCodec {
    const val MAX_EVENTS = 200
    const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

    fun encode(events: List<SmartFeedback>): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put(
                "events",
                JSONArray().apply {
                    events.takeLast(MAX_EVENTS).forEach { event ->
                        put(
                            JSONObject()
                                .put("candidateId", event.candidateId)
                                .put("actionId", event.actionId.orEmpty())
                                .put("appKey", event.appKey.orEmpty())
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

    fun decode(raw: String?): List<SmartFeedback> =
        runCatching {
            val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: return emptyList())
            val array = root.optJSONArray("events") ?: JSONArray()
            List(array.length()) { index -> array.optJSONObject(index) }
                .mapNotNull { json ->
                    val candidateId = json?.optString("candidateId").orEmpty()
                    val type = runCatching { SmartFeedbackType.valueOf(json?.optString("type").orEmpty()) }.getOrNull()
                    if (candidateId.isBlank() || type == null) return@mapNotNull null
                    SmartFeedback(
                        candidateId = candidateId,
                        actionId = json.optString("actionId").takeIf(String::isNotBlank),
                        appKey = json.optString("appKey").takeIf(String::isNotBlank),
                        type = type,
                        success = json.opt("success") as? Boolean,
                        coarseHourBucket = json.optInt("coarseHourBucket"),
                        contextKeys = json.optJSONArray("contextKeys")?.let { keys ->
                            List(keys.length()) { keys.optString(it) }.filter(String::isNotBlank).toSet()
                        }.orEmpty(),
                        atMillis = json.optLong("atMillis"),
                    )
                }
        }.getOrDefault(emptyList())

    fun summary(events: List<SmartFeedback>, nowMillis: Long): SmartFeedbackSummary {
        val fresh = events.filter { nowMillis - it.atMillis <= RETENTION_MS }
        val hidden = fresh.filter { it.type == SmartFeedbackType.Hide }.map { it.candidateId }.toSet()
        val never = fresh
            .filter { it.type == SmartFeedbackType.NeverForApp && it.appKey != null && it.actionId != null }
            .map { "${it.appKey}:${it.actionId}" }
            .toSet()
        val scores = mutableMapOf<String, Int>()
        val transitions = mutableMapOf<String, Int>()
        var previousSuccessfulActionId: String? = null
        fresh.forEach { event ->
            val id = event.actionId ?: return@forEach
            scores[id] = (scores[id] ?: 0) + when (event.type) {
                SmartFeedbackType.Run -> 0
                SmartFeedbackType.Pin -> 8
                SmartFeedbackType.Success -> 6
                SmartFeedbackType.Failure -> -8
                SmartFeedbackType.Hide -> 0
                SmartFeedbackType.NeverForApp -> 0
                SmartFeedbackType.Why -> 1
            }
            if (event.type == SmartFeedbackType.Success) {
                previousSuccessfulActionId?.takeIf { it != id }?.let { previous ->
                    transitions["$previous->$id"] = (transitions["$previous->$id"] ?: 0) + 10
                }
                previousSuccessfulActionId = id
            }
        }
        return SmartFeedbackSummary(
            hiddenCandidateIds = hidden,
            neverAppActionKeys = never,
            actionScores = scores,
            transitionScores = transitions,
        )
    }
}
