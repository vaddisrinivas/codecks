package io.codecks.domain.reactive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacStateModelsTest {
    @Test(expected = IllegalArgumentException::class)
    fun macIdRejectsBlank() {
        MacId(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun controlIdRejectsOverlongUtf8Value() {
        ControlId("a".repeat(129))
    }

    @Test
    fun snapshotExpiryUsesCapturedTime() {
        val snapshot = MacStateSnapshot(
            macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
            snapshotRevision = 7L,
            capturedAtMillis = 1_000L,
            frontApp = observed(null),
            activeWindow = observed(null),
            displays = observed(emptyList<MacDisplay>()),
            cursor = observed(null),
            selection = observed(MacSelection.None),
            clipboard = observed(null),
            media = observed(null),
            system = observed(null),
            meeting = observed(null),
            latestScreenshot = observed(null),
            capabilities = emptySet(),
        )

        assertFalse(snapshot.isBasicStateExpired(nowMillis = 3_999L))
        assertTrue(snapshot.isBasicStateExpired(nowMillis = 4_001L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refreshSuccessRequiresUpdatedFields() {
        MacStateRefreshResult.Succeeded(
            source = StateSource.Helper,
            updatedFields = emptySet(),
            snapshotRevision = 1L,
        )
    }

    private fun <T> observed(value: T?) = Observed(
        value = value,
        status = ObservationStatus.Unavailable,
        observedAtMillis = null,
        source = null,
    )
}
