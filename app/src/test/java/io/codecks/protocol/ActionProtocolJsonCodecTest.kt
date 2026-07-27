package io.codecks.protocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionProtocolJsonCodecTest {
    @Test
    fun definitionFixtureRoundTrips() {
        val raw = fixture("protocol/fixtures/action-definition.json")

        val decoded = ActionProtocolJsonCodec.decodeActionDefinition(raw)
        val redecoded = ActionProtocolJsonCodec.decodeActionDefinition(
            ActionProtocolJsonCodec.encodeActionDefinition(decoded),
        )

        assertEquals("macos.finder.reveal", decoded.id)
        assertEquals(decoded, redecoded)
    }

    @Test
    fun invocationFixtureRoundTrips() {
        val raw = fixture("protocol/fixtures/action-invocation.json")

        val decoded = ActionProtocolJsonCodec.decodeActionInvocation(raw)
        val redecoded = ActionProtocolJsonCodec.decodeActionInvocation(
            ActionProtocolJsonCodec.encodeActionInvocation(decoded),
        )

        assertEquals("macos.finder.reveal", decoded.actionId)
        assertEquals("/Users/example/Desktop/report.pdf", decoded.arguments["path"])
        assertEquals(decoded, redecoded)
    }

    @Test
    fun planFixtureRoundTrips() {
        val raw = fixture("protocol/fixtures/action-plan.json")

        val decoded = ActionProtocolJsonCodec.decodeActionPlan(raw)
        val redecoded = ActionProtocolJsonCodec.decodeActionPlan(
            ActionProtocolJsonCodec.encodeActionPlan(decoded),
        )

        assertEquals("plan_20260715_meeting_setup", decoded.planId)
        assertEquals(3, decoded.steps.size)
        assertEquals(decoded, redecoded)
    }

    @Test
    fun receiptFixtureRoundTrips() {
        val raw = fixture("protocol/fixtures/action-receipt.json")

        val decoded = ActionProtocolJsonCodec.decodeActionReceipt(raw)
        val redecoded = ActionProtocolJsonCodec.decodeActionReceipt(
            ActionProtocolJsonCodec.encodeActionReceipt(decoded),
        )

        assertEquals(ProtocolActionReceiptStatus.Completed, decoded.status)
        assertEquals("Finder", (decoded.outputs["app"]))
        assertEquals(decoded, redecoded)
    }

    @Test(expected = ProtocolValidationException::class)
    fun invalidReceiptStatusIsRejected() {
        ActionProtocolJsonCodec.decodeActionReceipt(
            """
            {
              "schemaVersion": "1.0",
              "receiptId": "rcpt_20260715_badstatus",
              "invocationId": "inv_20260715_badstatus",
              "actionId": "macos.finder.reveal",
              "status": "mystery",
              "startedAt": "2026-07-15T12:00:01Z",
              "finishedAt": "2026-07-15T12:00:02Z"
            }
            """.trimIndent(),
        )
    }

    private fun fixture(path: String): String {
        val file = sequenceOf(
            File(path),
            File("../$path"),
        ).firstOrNull(File::exists)
        assertTrue("Fixture missing: $path", file != null)
        return file!!.readText()
    }
}
