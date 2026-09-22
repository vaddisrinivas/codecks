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
import io.codecks.data.persistence.BoundedPayload
import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.valueForMutation

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
                events = DiagnosticEventCodec.read(backend.read()).valueForMutation("Diagnostic journal") { emptyList() } + event,
                nowEpochMs = nowEpochMs(),
            )
            backend.write(DiagnosticEventCodec.encode(retained))
        }
    }

    fun events(): List<DiagnosticEvent> =
        synchronized(lock) {
            val read = DiagnosticEventCodec.read(backend.read())
            val decoded = (read as? PersistenceRead.Value)?.value.orEmpty()
            val retained = retain(decoded, nowEpochMs())
            if (read is PersistenceRead.Value && retained != decoded) {
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
            DiagnosticEventCodec.read(backend.read()).valueForMutation("Diagnostic journal") { emptyList() }
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
        check(preferences.edit().putString(EVENTS_KEY, value).commit()) { "Diagnostic journal commit failed" }
    }

    override fun clear() {
        check(preferences.edit().remove(EVENTS_KEY).commit()) { "Diagnostic journal clear failed" }
    }

    private companion object {
        const val PREFERENCES_NAME = "diagnostic_event_journal"
        const val EVENTS_KEY = "events_v1"
    }
}

internal object DiagnosticEventCodec {
    private const val SchemaVersion = 1
    private const val MaxBytes = 256 * 1024

    fun encode(events: List<DiagnosticEvent>): String = BoundedPayload.utf8(
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
            .toString(),
        MaxBytes,
    )

    fun decode(value: String?): List<DiagnosticEvent> = (read(value) as? PersistenceRead.Value)?.value.orEmpty()

    fun read(value: String?): PersistenceRead<List<DiagnosticEvent>> {
        if (value == null) return PersistenceRead.Missing
        if (value.isBlank()) return PersistenceRead.Corrupt("blank")
        if (value.toByteArray(Charsets.UTF_8).size > MaxBytes) return PersistenceRead.Corrupt("oversized")
        return runCatching {
            val raw = value
            val root = JSONObject(raw)
            val rawVersion = root.opt("schemaVersion") as? Number ?: return PersistenceRead.Corrupt("schema")
            val version = rawVersion.toInt().takeIf { it.toDouble() == rawVersion.toDouble() }
                ?: return PersistenceRead.Corrupt("schema")
            if (version > SchemaVersion) return PersistenceRead.FutureVersion(version, SchemaVersion)
            if (version != SchemaVersion) return PersistenceRead.Corrupt("schema")
            val events = root.optJSONArray("events") ?: return PersistenceRead.Corrupt("events")
            if (events.length() > DiagnosticEventStore.MAX_ENTRIES) return PersistenceRead.Corrupt("count")
            PersistenceRead.Value(
                List(events.length()) { index -> requireNotNull(decodeEvent(events.optJSONObject(index))) },
            )
        }.getOrElse { PersistenceRead.Corrupt("decode") }
    }

    private fun decodeEvent(value: JSONObject?): DiagnosticEvent? {
        value ?: return null
        val attempt = value.optStrictInt("attempt") ?: return null
        val durationMs = value.optStrictLong("durationMs") ?: return null
        val timestampEpochMs = value.optStrictLong("timestampEpochMs") ?: return null
        val componentRaw = value.opt("component") as? String ?: return null
        val eventRaw = value.opt("event") as? String ?: return null
        val resultRaw = value.opt("result") as? String ?: return null
        val component = DiagnosticComponent.entries.singleOrNull { it.persistedCode == componentRaw } ?: return null
        val event = DiagnosticEventCode.entries.singleOrNull { it.persistedCode == eventRaw } ?: return null
        val result = DiagnosticResultCode.entries.singleOrNull { it.persistedCode == resultRaw } ?: return null
        return runCatching {
            DiagnosticEvent(
                component = component,
                event = event,
                result = result,
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
