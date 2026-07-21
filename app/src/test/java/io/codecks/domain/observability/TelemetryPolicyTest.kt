package io.codecks.domain.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryPolicyTest {
    @Test
    fun rejectsSensitiveCustomFields() {
        val rejected = TelemetryPolicy.validateCustomFields(
            listOf("command", "clipboard", "prompt", "token", "hostname", "email", "device-id", "purchase_token"),
        )

        assertEquals(
            listOf("command", "clipboard", "prompt", "token", "hostname", "email", "device-id", "purchase_token"),
            rejected,
        )
    }

    @Test
    fun allowsCoarseCustomFields() {
        assertTrue(TelemetryPolicy.acceptsCustomField("latency_bucket"))
        assertTrue(TelemetryPolicy.acceptsCustomField("route"))
        assertTrue(TelemetryPolicy.acceptsCustomField("status_code_bucket"))
        assertFalse(TelemetryPolicy.acceptsCustomField("api-key"))
    }
}
