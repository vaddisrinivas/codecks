package io.codecks.domain.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationConnectionIdentityTest {
    @Test
    fun identityIsStableOpaqueAndBindsEveryTrustField() {
        val identity = automationConnectionIdentity(
            host = "private-mac.local",
            port = 22,
            user = "private-user",
            hostKey = "private-host-key",
        )

        assertTrue(isOpaqueAutomationConnectionIdentity(identity))
        listOf("private-mac.local", "private-user", "private-host-key").forEach {
            assertFalse(identity.contains(it))
        }
        assertNotEquals(
            identity,
            automationConnectionIdentity("private-mac.local", 2222, "private-user", "private-host-key"),
        )
        assertNotEquals(
            identity,
            automationConnectionIdentity("private-mac.local", 22, "other-user", "private-host-key"),
        )
    }
}
