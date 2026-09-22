package io.codecks.internalquality.m16

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class M16IdentityTest {
    @Test fun identitiesMatchFrozenVector() {
        val first = M16Identities.resolve("m16Soak01Api35", 1)
        assertEquals("avd01-p01", first.profileId)
        assertEquals("app.codecks.internal:m16p01", first.processName)
        assertEquals("7d7f7c47aa8539e3", first.seed)
        assertEquals("825ef9139bc5df641b99a12496094021", first.nonce)
        assertNotEquals(first, M16Identities.resolve("m16Soak04Api35", 5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownAvdRejected() {
        M16Identities.resolve("medium_phone", 1)
    }
}
