package io.codex.s23deck.domain.commerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommerceStateJsonCodecTest {
    @Test
    fun accountRoundTripIncludesSchemaVersion() {
        val encoded = CommerceStateJsonCodec.encodeAccount(
            AccountState.SignedIn(Account("subject", "user@example.com")),
        )

        assertTrue(encoded!!.contains(""""schemaVersion":1"""))
        assertEquals(
            AccountState.SignedIn(Account("subject", "user@example.com")),
            CommerceStateJsonCodec.decodeAccount(encoded),
        )
    }

    @Test
    fun sessionRoundTripIncludesTokens() {
        val session = BackendAuthSession(
            subject = "subject",
            email = "user@example.com",
            accessToken = "access",
            refreshToken = "refresh",
            accessTokenExpiresAtEpochSeconds = 2,
            refreshTokenExpiresAtEpochSeconds = 3,
        )

        assertEquals(session, CommerceStateJsonCodec.decodeSession(CommerceStateJsonCodec.encodeSession(session)))
    }

    @Test
    fun entitlementRoundTripPreservesOfflineGrace() {
        val entitlement = Entitlement(
            tier = EntitlementTier.Premium,
            status = EntitlementStatus.OfflineGrace,
            offlineGrace = OfflineGrace.Active(1234),
        )

        assertEquals(entitlement, CommerceStateJsonCodec.decodeEntitlement(CommerceStateJsonCodec.encodeEntitlement(entitlement)))
    }

    @Test
    fun corruptOrFutureStateFallsBackToSafeDefaults() {
        assertEquals(AccountState.SignedOut, CommerceStateJsonCodec.decodeAccount("{bad"))
        assertNull(CommerceStateJsonCodec.decodeSession("""{"schemaVersion":99,"subject":"s"}"""))
        assertEquals(
            Entitlement(EntitlementTier.Free, EntitlementStatus.Free),
            CommerceStateJsonCodec.decodeEntitlement("""{"schemaVersion":99,"tier":"Premium","status":"Active"}"""),
        )
        assertTrue(CommerceStateJsonCodec.encodeAccount(AccountState.SignedOut) == null)
    }
}
