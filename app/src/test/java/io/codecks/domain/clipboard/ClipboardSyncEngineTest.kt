package io.codecks.domain.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSyncEngineTest {
    private val phoneApp = ClipboardSourceId("phone-app")
    private val macAgent = ClipboardSourceId("mac-agent")

    @Test
    fun phoneToMacModeOnlyWritesPhoneChangesToMac() {
        val engine = ClipboardSyncEngine()
        val phone = engine.observe(ClipboardEndpoint.Phone, "from phone", phoneApp, nowMillis = 1_000)

        val action = engine.decide(ClipboardSyncMode.PhoneToMac, nowMillis = 1_100)

        assertEquals(ClipboardSyncAction.WriteToMac(phone.revision.hash), action)
        assertEquals(ClipboardSyncAction.None, engine.decide(ClipboardSyncMode.MacToPhone, nowMillis = 1_100))
    }

    @Test
    fun appliedWriteEchoDoesNotCreateLoop() {
        val engine = ClipboardSyncEngine()
        val phone = engine.observe(ClipboardEndpoint.Phone, "same text", phoneApp, nowMillis = 1_000)
        val write = engine.decide(ClipboardSyncMode.PhoneToMac, nowMillis = 1_010)
        engine.markApplied(write)

        val macEcho = engine.observe(ClipboardEndpoint.Mac, "same text", macAgent, nowMillis = 1_050)

        assertTrue(macEcho.loopEcho)
        assertEquals(phone.revision.hash, macEcho.revision.hash)
        assertEquals(ClipboardSyncAction.None, engine.decide(ClipboardSyncMode.Bidirectional, nowMillis = 1_060))
    }

    @Test
    fun bidirectionalDetectsConflictWhenBothSidesChangeAfterCommonRevision() {
        val engine = ClipboardSyncEngine()
        engine.observe(ClipboardEndpoint.Phone, "base", phoneApp, nowMillis = 1_000)
        val initialWrite = engine.decide(ClipboardSyncMode.PhoneToMac, nowMillis = 1_010)
        engine.markApplied(initialWrite)
        engine.observe(ClipboardEndpoint.Mac, "base", macAgent, nowMillis = 1_020)

        val phoneChange = engine.observe(ClipboardEndpoint.Phone, "phone edit", phoneApp, nowMillis = 2_000)
        val macChange = engine.observe(ClipboardEndpoint.Mac, "mac edit", macAgent, nowMillis = 2_100)
        val action = engine.decide(ClipboardSyncMode.Bidirectional, nowMillis = 2_110)

        assertTrue(action is ClipboardSyncAction.Conflict)
        val conflict = (action as ClipboardSyncAction.Conflict).conflict
        assertEquals(phoneChange.revision.hash, conflict.phone.hash)
        assertEquals(macChange.revision.hash, conflict.mac.hash)
        assertEquals(conflict, engine.snapshot(nowMillis = 2_110).conflict)
    }

    @Test
    fun bidirectionalFastForwardsSingleSidedChange() {
        val engine = ClipboardSyncEngine()
        engine.observe(ClipboardEndpoint.Phone, "base", phoneApp, nowMillis = 1_000)
        val initialWrite = engine.decide(ClipboardSyncMode.PhoneToMac, nowMillis = 1_010)
        engine.markApplied(initialWrite)
        engine.observe(ClipboardEndpoint.Mac, "base", macAgent, nowMillis = 1_020)

        val macChange = engine.observe(ClipboardEndpoint.Mac, "new mac", macAgent, nowMillis = 2_000)
        val action = engine.decide(ClipboardSyncMode.Bidirectional, nowMillis = 2_010)

        assertEquals(ClipboardSyncAction.WriteToPhone(macChange.revision.hash), action)
    }

    @Test
    fun historyIsBoundedAndDoesNotStoreClipboardContents() {
        val engine = ClipboardSyncEngine(maxHistory = 3)

        repeat(5) { index ->
            engine.observe(ClipboardEndpoint.Phone, "secret-$index", phoneApp, nowMillis = index.toLong())
        }

        val history = engine.snapshot(nowMillis = 10).history
        assertEquals(3, history.size)
        assertEquals(listOf(3L, 4L, 5L), history.map { it.revision })
        assertFalse(history.any { it.hash.contains("secret") || it.sourceId.value.contains("secret") })
    }

    @Test
    fun duplicateTextObservationIsNoOpAndPreservesRevision() {
        val engine = ClipboardSyncEngine()
        val first = engine.observe(ClipboardEndpoint.Phone, "same", phoneApp, nowMillis = 1_000)
        val second = engine.observe(ClipboardEndpoint.Phone, "same", phoneApp, nowMillis = 1_100)

        assertFalse(second.changed)
        assertEquals(first.revision.revision, second.revision.revision)
        assertEquals(first.revision.hash, second.revision.hash)
        assertEquals("same", second.revision.sourceId.value)
        assertEquals(phoneApp, second.revision.sourceId)
    }

    @Test
    fun emptyContentIsObservedAndCanPropagate() {
        val engine = ClipboardSyncEngine()
        val phone = engine.observe(ClipboardEndpoint.Phone, "", phoneApp, nowMillis = 1_000)
        engine.observe(ClipboardEndpoint.Mac, "", macAgent, nowMillis = 1_010)

        val action = engine.decide(ClipboardSyncMode.Bidirectional, nowMillis = 1_020)

        assertTrue(phone.changed)
        assertEquals(ClipboardSyncAction.None, action)
    }

    @Test
    fun staleRevisionsRecoverAndResumeSyncAfterReconnect() {
        val engine = ClipboardSyncEngine(staleAfterMillis = 500)

        engine.observe(ClipboardEndpoint.Phone, "old", phoneApp, nowMillis = 1_000)
        engine.observe(ClipboardEndpoint.Mac, "old", macAgent, nowMillis = 1_000)

        val beforeReconnect = engine.snapshot(nowMillis = 1_600)
        assertEquals(setOf(ClipboardEndpoint.Phone, ClipboardEndpoint.Mac), beforeReconnect.staleEndpoints)

        engine.observe(ClipboardEndpoint.Mac, "refreshed on reconnect", macAgent, nowMillis = 2_000)

        val action = engine.decide(ClipboardSyncMode.Bidirectional, nowMillis = 2_010)
        assertEquals(ClipboardSyncAction.WriteToPhone(ClipboardHash.of("refreshed on reconnect")), action)
    }

    @Test
    fun snapshotMarksStaleEndpoints() {
        val engine = ClipboardSyncEngine(staleAfterMillis = 500)

        engine.observe(ClipboardEndpoint.Phone, "old", phoneApp, nowMillis = 1_000)
        engine.observe(ClipboardEndpoint.Mac, "fresh", macAgent, nowMillis = 1_450)

        val snapshot = engine.snapshot(nowMillis = 1_600)
        assertTrue(ClipboardEndpoint.Phone in snapshot.staleEndpoints)
        assertFalse(ClipboardEndpoint.Mac in snapshot.staleEndpoints)
    }
}
