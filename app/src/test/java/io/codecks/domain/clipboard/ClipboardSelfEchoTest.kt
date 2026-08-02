package io.codecks.domain.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSelfEchoTest {
    @Test
    fun appliedEchoExpiresAndCannotSuppressLaterIndependentChange() {
        val engine = ClipboardSyncEngine(echoTtlMillis = 500)
        val phone = engine.observe(ClipboardEndpoint.Phone, "value", PHONE, 1_000)
        val write = ClipboardSyncAction.WriteToMac(phone.revision.hash)
        engine.markApplied(write, nowMillis = 1_010)

        val lateMac = engine.observe(ClipboardEndpoint.Mac, "value", MAC, nowMillis = 1_600)

        assertFalse(lateMac.loopEcho)
    }

    @Test
    fun repeatedOverlayAndRemoteEchoCallbacksTerminateWithoutPingPong() {
        val engine = ClipboardSyncEngine(echoTtlMillis = 5_000)
        val phone = engine.observe(ClipboardEndpoint.Phone, "same", PHONE, 1_000)
        val write = ClipboardSyncAction.WriteToMac(phone.revision.hash)
        engine.markApplied(write, 1_010)

        val firstEcho = engine.observe(ClipboardEndpoint.Mac, "same", MAC, 1_020)
        val repeatedRemote = engine.observe(ClipboardEndpoint.Mac, "same", MAC, 1_030)
        val overlayCallback = engine.observe(ClipboardEndpoint.Phone, "same", PHONE, 1_040)

        assertTrue(firstEcho.loopEcho)
        assertFalse(repeatedRemote.changed)
        assertFalse(overlayCallback.changed)
        repeat(10) {
            assertEquals(ClipboardSyncAction.None, engine.decide(ClipboardSyncMode.Bidirectional, 1_050L + it))
        }
    }

    @Test
    fun pendingEchoCapacityIsBounded() {
        val engine = ClipboardSyncEngine(maxPendingEchoes = 1)
        engine.markApplied(ClipboardSyncAction.WriteToMac(ClipboardHash.of("mac")), 1_000)
        engine.markApplied(ClipboardSyncAction.WriteToPhone(ClipboardHash.of("phone")), 1_001)

        val evicted = engine.observe(ClipboardEndpoint.Mac, "mac", MAC, 1_002)
        val retained = engine.observe(ClipboardEndpoint.Phone, "phone", PHONE, 1_003)

        assertFalse(evicted.loopEcho)
        assertTrue(retained.loopEcho)
    }

    private companion object {
        val PHONE = ClipboardSourceId("phone")
        val MAC = ClipboardSourceId("mac")
    }
}
