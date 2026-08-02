package io.codecks.data.privacy

import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticEvent
import io.codecks.domain.privacy.DiagnosticEventCode
import io.codecks.domain.privacy.DiagnosticResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventStoreTest {
    @Test
    fun retainsOnlyLatestTwoHundredEvents() {
        val now = 1_000_000L
        val store = store(nowEpochMs = now)

        repeat(250) { index ->
            store.record(event(timestampEpochMs = now - 500L + index))
        }

        assertEquals(DiagnosticEventStore.MAX_ENTRIES, store.events().size)
        assertEquals(now - 450L, store.events().first().timestampEpochMs)
        assertEquals(now - 251L, store.events().last().timestampEpochMs)
    }

    @Test
    fun removesEventsOlderThanSevenDays() {
        val now = DiagnosticEventStore.RETENTION_MS + 10_000L
        val store = store(nowEpochMs = now)
        store.record(event(timestampEpochMs = now - DiagnosticEventStore.RETENTION_MS - 1L))
        store.record(event(timestampEpochMs = now - DiagnosticEventStore.RETENTION_MS))
        store.record(event(timestampEpochMs = now))

        assertEquals(
            listOf(now - DiagnosticEventStore.RETENTION_MS, now),
            store.events().map(DiagnosticEvent::timestampEpochMs),
        )
    }

    @Test
    fun persistsAndRestoresOnlyTypedFields() {
        val backend = FakeDiagnosticEventBackend()
        val store = DiagnosticEventStore(backend) { 10_000L }
        val expected = event(timestampEpochMs = 9_000L)
        store.record(expected)

        assertEquals(expected, DiagnosticEventStore(backend) { 10_000L }.events().single())
        assertEquals(
            setOf(
                "component",
                "event",
                "result",
                "attempt",
                "durationMs",
                "timestampEpochMs",
            ),
            org.json.JSONObject(requireNotNull(backend.value))
                .getJSONArray("events")
                .getJSONObject(0)
                .keys()
                .asSequence()
                .toSet(),
        )
    }

    @Test
    fun malformedAndFutureValuesFailClosed() {
        val backend = FakeDiagnosticEventBackend(
            """
            {
              "schemaVersion": 1,
              "events": [{
                "component": "future_component",
                "event": "future_event",
                "result": "future_result",
                "attempt": 1,
                "durationMs": 2,
                "timestampEpochMs": 100
              }]
            }
            """.trimIndent(),
        )
        val store = DiagnosticEventStore(backend) { 200L }
        val restored = store.events().single()

        assertEquals(DiagnosticComponent.UNKNOWN, restored.component)
        assertEquals(DiagnosticEventCode.UNKNOWN, restored.event)
        assertEquals(DiagnosticResultCode.UNKNOWN, restored.result)

        backend.value = """{"schemaVersion":1,"events":[{"attempt":"secret"}]}"""
        assertTrue(store.events().isEmpty())
    }

    private fun store(nowEpochMs: Long): DiagnosticEventStore =
        DiagnosticEventStore(FakeDiagnosticEventBackend()) { nowEpochMs }

    private fun event(timestampEpochMs: Long): DiagnosticEvent =
        DiagnosticEvent(
            component = DiagnosticComponent.CONNECTION,
            event = DiagnosticEventCode.ATTEMPT_FINISHED,
            result = DiagnosticResultCode.SUCCEEDED,
            attempt = 1,
            durationMs = 25L,
            timestampEpochMs = timestampEpochMs,
        )
}

internal class FakeDiagnosticEventBackend(
    var value: String? = null,
) : DiagnosticEventBackend {
    override fun read(): String? = value

    override fun write(value: String) {
        this.value = value
    }

    override fun clear() {
        value = null
    }
}
