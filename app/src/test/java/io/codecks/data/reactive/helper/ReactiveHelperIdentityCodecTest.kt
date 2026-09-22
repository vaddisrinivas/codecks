package io.codecks.data.reactive.helper

import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.valueForMutation
import io.codecks.platform.helper.StoredReactiveHelperIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class ReactiveHelperIdentityCodecTest {
    private val identity = StoredReactiveHelperIdentity(
        macId = "mac-1",
        displayName = "My Mac",
        helperId = "helper-1",
        publicKeyFingerprint = "a".repeat(64),
        secretAlias = "reactive_helper_abc",
        host = "mac.local",
        port = 45_821,
    )

    @Test
    fun `current round trip and legacy array migration preserve pairing`() {
        val current = ReactiveHelperIdentityCodec.decode(ReactiveHelperIdentityCodec.encode(listOf(identity)))
        assertEquals(PersistenceRead.Value(listOf(identity)), current)

        val legacy = ReactiveHelperIdentityCodec.encode(listOf(identity))
            .let { org.json.JSONObject(it).getJSONArray("identities").toString() }
        assertEquals(PersistenceRead.Value(listOf(identity), migratedFrom = 1), ReactiveHelperIdentityCodec.decode(legacy))
    }

    @Test
    fun `future corrupt and oversized pairing cannot be overwritten`() {
        val outcomes = listOf(
            ReactiveHelperIdentityCodec.decode("{\"schemaVersion\":999,\"identities\":[]}"),
            ReactiveHelperIdentityCodec.decode("corrupt"),
            ReactiveHelperIdentityCodec.decode("x".repeat(70 * 1024)),
        )
        assertTrue(outcomes[0] is PersistenceRead.FutureVersion)
        assertTrue(outcomes[1] is PersistenceRead.Corrupt)
        assertTrue(outcomes[2] is PersistenceRead.Corrupt)
        outcomes.forEach { outcome ->
            assertTrue(runCatching { outcome.valueForMutation("helper") { emptyList() } }.isFailure)
        }
    }

    @Test
    fun `failed identity commit restores prior secret`() = runTest {
        var secret = "old"
        val failure = runCatching {
            commitPreparedPairing(
                preparedIdentity = "validated-identity",
                existingSecret = secret,
                writeSecret = { secret = "new" },
                commitIdentity = { error("disk full") },
                restoreSecret = { secret = it },
                deleteSecret = { secret = "deleted" },
            )
        }
        assertTrue(failure.isFailure)
        assertEquals("old", secret)
    }

    @Test
    fun `failed first pairing deletes newly written secret`() = runTest {
        var secret: String? = null
        runCatching {
            commitPreparedPairing(
                preparedIdentity = "validated-identity",
                existingSecret = null,
                writeSecret = { secret = "new" },
                commitIdentity = { error("process death") },
                restoreSecret = { secret = it },
                deleteSecret = { secret = null },
            )
        }
        assertEquals(null, secret)
    }
}
