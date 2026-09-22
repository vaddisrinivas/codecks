package io.codecks.data.clipboard

import io.codecks.domain.clipboard.ClipboardDirection
import io.codecks.domain.clipboard.ClipboardFailureCode
import io.codecks.domain.clipboard.ClipboardReceipt
import io.codecks.domain.clipboard.ClipboardTerminalResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.valueForMutation

class ClipboardLastSyncPrivacyTest {
    @Test
    fun persistedSummaryContainsOnlyAllowlistedMetadata() {
        val encoded = ClipboardLastSyncCodec.encode(
            ClipboardReceipt(
                direction = ClipboardDirection.PhoneToMac,
                terminalResult = ClipboardTerminalResult.Failure,
                failureCode = ClipboardFailureCode.Timeout,
                startedAtMillis = 100,
                completedAtMillis = 200,
            ),
        )
        val keys = JSONObject(encoded).keys().asSequence().toSet()

        assertEquals(ClipboardLastSyncCodec.ALLOWED_FIELDS, keys)
        listOf("content", "hash", "length", "preview", "output", "message").forEach {
            assertFalse(encoded.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun unexpectedFieldsFailClosed() {
        val unsafe = JSONObject()
            .put("direction", "phone_to_mac")
            .put("terminalResult", "failure")
            .put("failureCode", "unknown")
            .put("startedAtMillis", 100)
            .put("completedAtMillis", 200)
            .put("preview", "CANARY_SECRET")
            .toString()

        assertNull(ClipboardLastSyncCodec.decode(unsafe))
    }

    @Test
    fun legacyReceiptIsTypedAndFutureOrCorruptCannotBeOverwritten() {
        val legacy = """{"direction":"phone_to_mac","terminalResult":"failure","failureCode":"timeout","startedAtMillis":100,"completedAtMillis":200}"""
        assertEquals(1, (ClipboardLastSyncCodec.read(legacy) as PersistenceRead.Value).migratedFrom)

        val blocked = listOf(
            ClipboardLastSyncCodec.read("""{"schemaVersion":99}"""),
            ClipboardLastSyncCodec.read("corrupt"),
        )
        blocked.forEach { result ->
            assertTrue(runCatching { result.valueForMutation("clipboard") { error("unused") } }.isFailure)
        }
    }
}
