package io.codecks.runtime.actions

import io.codecks.core.testing.FixedClock
import io.codecks.domain.actions.ReceiptState
import io.codecks.domain.targets.LiveState
import io.codecks.domain.targets.TargetSetupDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunFirstFinderProofUseCaseTest {
    @Test
    fun `successful Finder proof returns redacted success receipt`() = runTest {
        val receipt = RunFirstFinderProofUseCase(
            executor = FakeMacActionExecutor(succeeds = true),
            clock = FixedClock(42_000),
        )(target())

        assertEquals("finder-proof-42000", receipt.invocationId)
        assertEquals(ReceiptState.SUCCESS, receipt.state)
        assertEquals(42_000L, receipt.startedAtEpochMillis)
        assertEquals(42_120L, receipt.endedAtEpochMillis)
        assertTrue(receipt.safeSummary.contains("succeeded"))
        assertNull(receipt.repair)
    }

    @Test
    fun `failed Finder proof returns one repair hint`() = runTest {
        val receipt = RunFirstFinderProofUseCase(
            executor = FakeMacActionExecutor(succeeds = false),
            clock = FixedClock(42_000),
        )(target())

        assertEquals(ReceiptState.FAILURE, receipt.state)
        assertNotNull(receipt.repair)
        assertEquals("Repair connection", receipt.repair?.actionLabel)
    }

    private fun target() = TargetSetupDraft(
        targetName = "MacBook",
        host = "192.168.1.42",
        username = "sv",
        fingerprintSha256 = "SHA256:abc",
    )
        .trustFingerprint()
        .installPhoneKey("ssh-ed25519 AAAA codecks")
        .toMacTarget(liveState = LiveState.ONLINE)
}
