package io.codecks.data.privacy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticEvent
import io.codecks.domain.privacy.DiagnosticEventCode
import io.codecks.domain.privacy.DiagnosticResultCode
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class DiagnosticEventStore internal constructor(
    private val backend: DiagnosticEventBackend,
    private val nowEpochMs: () -> Long,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        backend = SharedPreferencesDiagnosticEventBackend(context),
        nowEpochMs = System::currentTimeMillis,
    )

    private val lock = Any()

    fun record(event: DiagnosticEvent) {
        synchronized(lock) {
            val retained = retain(
                events = DiagnosticEventCodec.decode(backend.read()) + event,
                nowEpochMs = nowEpochMs(),
            )
            backend.write(DiagnosticEventCodec.encode(retained))
        }
    }

    fun events(): List<DiagnosticEvent> =
        synchronized(lock) {
            val decoded = DiagnosticEventCodec.decode(backend.read())
            val retained = retain(decoded, nowEpochMs())
            if (retained != decoded) {
                backend.write(DiagnosticEventCodec.encode(retained))
            }
            retained
        }

    fun exportJson(): String =
        synchronized(lock) {
            DiagnosticEventCodec.encode(events())
        }

    fun clear() {
        synchronized(lock) {
            backend.clear()
        }
    }

    private fun retain(events: List<DiagnosticEvent>, nowEpochMs: Long): List<DiagnosticEvent> {
        val oldestAllowedEpochMs = (nowEpochMs - RETENTION_MS).coerceAtLeast(0L)
        return events
            .filter { it.timestampEpochMs >= oldestAllowedEpochMs }
            .sortedBy(DiagnosticEvent::timestampEpochMs)
            .takeLast(MAX_ENTRIES)
    }

    companion object {
        const val MAX_ENTRIES = 200
        const val RETENTION_MS = 7L * 24L * 60L * 60L * 1_000L
    }
}

internal interface DiagnosticEventBackend {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

private class SharedPreferencesDiagnosticEventBackend(context: Context) : DiagnosticEventBackend {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): String? = preferences.getString(EVENTS_KEY, null)

    override fun write(value: String) {
        preferences.edit().putString(EVENTS_KEY, value).apply()
    }

    override fun clear() {
        preferences.edit().remove(EVENTS_KEY).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "diagnostic_event_journal"
        const val EVENTS_KEY = "events_v1"
    }
}

internal object DiagnosticEventCodec {
    private const val SchemaVersion = 1

    fun encode(events: List<DiagnosticEvent>): String =
        JSONObject()
            .put("schemaVersion", SchemaVersion)
            .put(
                "events",
                JSONArray().apply {
                    events.forEach { event ->
                        put(
                            JSONObject()
                                .put("component", event.component.persistedCode)
                                .put("event", event.event.persistedCode)
                                .put("result", event.result.persistedCode)
                                .put("attempt", event.attempt)
                                .put("durationMs", event.durationMs)
                                .put("timestampEpochMs", event.timestampEpochMs),
                        )
                    }
                },
            )
            .toString()

    fun decode(value: String?): List<DiagnosticEvent> =
        runCatching {
            val root = JSONObject(value?.takeIf(String::isNotBlank) ?: return emptyList())
            if (root.optInt("schemaVersion", -1) != SchemaVersion) return emptyList()
            val events = root.optJSONArray("events") ?: return emptyList()
            List(events.length()) { index -> events.optJSONObject(index) }
                .mapNotNull(::decodeEvent)
        }.getOrDefault(emptyList())

    private fun decodeEvent(value: JSONObject?): DiagnosticEvent? {
        value ?: return null
        val attempt = value.optStrictInt("attempt") ?: return null
        val durationMs = value.optStrictLong("durationMs") ?: return null
        val timestampEpochMs = value.optStrictLong("timestampEpochMs") ?: return null
        return runCatching {
            DiagnosticEvent(
                component = DiagnosticComponent.fromPersistedCode(value.optString("component")),
                event = DiagnosticEventCode.fromPersistedCode(value.optString("event")),
                result = DiagnosticResultCode.fromPersistedCode(value.optString("result")),
                attempt = attempt,
                durationMs = durationMs,
                timestampEpochMs = timestampEpochMs,
            )
        }.getOrNull()
    }
}

private fun JSONObject.optStrictInt(name: String): Int? {
    val value = opt(name) as? Number ?: return null
    val doubleValue = value.toDouble()
    return value.toInt().takeIf { it.toDouble() == doubleValue }
}

private fun JSONObject.optStrictLong(name: String): Long? {
    val value = opt(name) as? Number ?: return null
    val doubleValue = value.toDouble()
    return value.toLong().takeIf { it.toDouble() == doubleValue }
}
