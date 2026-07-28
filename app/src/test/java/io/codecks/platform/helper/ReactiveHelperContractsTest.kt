package io.codecks.platform.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReactiveHelperContractsTest {
    @Test
    fun reconnectPolicyIsBounded() {
        val policy = ReactiveReconnectPolicy(listOf(1L, 2L, 5L))
        assertEquals(1L, policy.delayForAttempt(0))
        assertEquals(5L, policy.delayForAttempt(999))
    }

    @Test
    fun discoveredEndpointRejectsInvalidPort() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveredReactiveHelper("Codecks", "mac.local", 0, "reactive.v1")
        }
    }

    @Test
    fun storedIdentityRequiresPinnedFingerprint() {
        assertThrows(IllegalArgumentException::class.java) {
            StoredReactiveHelperIdentity(
                macId = "mac-1",
                displayName = "Mac",
                helperId = "helper-1",
                publicKeyFingerprint = "short",
                secretAlias = "secret",
            )
        }
    }
}
