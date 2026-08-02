package io.codecks.data.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationQuarantinePrivacyTest {
    @Test
    fun quarantineStoresOnlyBoundedHashMetadata() {
        val secret = "Authorization: Bearer should-never-persist"
        val quarantine = JSONObject(quarantinePayload(secret, "recipes"))

        assertEquals(2, quarantine.getInt("schemaVersion"))
        assertEquals("recipes", quarantine.getString("store"))
        assertEquals(secret.length, quarantine.getInt("payloadLength"))
        assertEquals(64, quarantine.getString("payloadSha256").length)
        assertFalse(quarantine.has("raw"))
        assertFalse(quarantine.toString().contains("should-never-persist"))
        assertTrue(quarantine.getLong("quarantinedAtMillis") > 0)
    }
}
