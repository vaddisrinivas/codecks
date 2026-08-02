package io.codecks.domain.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardConflictResolverTest {
    @Test
    fun cancelSuppressesSameConflictWithoutOverwritingEitherEndpoint() {
        val engine = conflictedEngine()
        assertTrue(engine.decide(ClipboardSyncMode.Bidirectional, 2_100) is ClipboardSyncAction.Conflict)

        assertTrue(engine.cancelConflict())
        assertEquals(ClipboardSyncAction.None, engine.decide(ClipboardSyncMode.Bidirectional, 2_200))
        assertFalse(engine.snapshot(2_200).conflict != null)
    }

    @Test
    fun endpointChangeAfterCancelCreatesNewConflict() {
        val engine = conflictedEngine()
        engine.decide(ClipboardSyncMode.Bidirectional, 2_100)
        engine.cancelConflict()

        engine.observe(ClipboardEndpoint.Phone, "phone edit 2", PHONE, 2_300)

        assertTrue(engine.decide(ClipboardSyncMode.Bidirectional, 2_400) is ClipboardSyncAction.Conflict)
    }

    private fun conflictedEngine(): ClipboardSyncEngine = ClipboardSyncEngine().apply {
        observe(ClipboardEndpoint.Phone, "base", PHONE, 1_000)
        val initial = decide(ClipboardSyncMode.PhoneToMac, 1_010)
        markApplied(initial, 1_010)
        observe(ClipboardEndpoint.Mac, "base", MAC, 1_020)
        observe(ClipboardEndpoint.Phone, "phone edit", PHONE, 2_000)
        observe(ClipboardEndpoint.Mac, "mac edit", MAC, 2_050)
    }

    private companion object {
        val PHONE = ClipboardSourceId("phone")
        val MAC = ClipboardSourceId("mac")
    }
}
