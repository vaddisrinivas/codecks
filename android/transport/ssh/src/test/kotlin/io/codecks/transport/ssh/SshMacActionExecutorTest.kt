package io.codecks.transport.ssh

import io.codecks.core.common.CodecksClock
import io.codecks.core.security.SensitiveChars
import io.codecks.domain.actions.ActionPlan
import io.codecks.domain.actions.ExecutorKind
import io.codecks.domain.actions.PlannedStep
import io.codecks.domain.actions.ReceiptState
import io.codecks.domain.actions.TimeoutPolicy
import io.codecks.domain.targets.LiveState
import io.codecks.domain.targets.MacTarget
import io.codecks.domain.targets.SshIdentity
import io.codecks.domain.targets.TargetCapability
import io.codecks.domain.targets.TrustState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshMacActionExecutorTest {
    @Test
    fun successfulCommandCreatesSuccessReceipt() = runTest {
        val executor = SshMacActionExecutor(
            credentialProvider = StaticCredentialProvider,
            transport = FakeSshCommandTransport(SshCommandResult.Completed(0, "", "", 25)),
            clock = FixedTestClock(125),
        )

        val receipt = executor.execute(plan(), target(), startedAtEpochMillis = 100)

        assertEquals(ReceiptState.SUCCESS, receipt.state)
        assertEquals(125L, receipt.endedAtEpochMillis)
        assertTrue(receipt.safeSummary.contains("succeeded"))
    }

    @Test
    fun failedAuthCreatesRepairReceipt() = runTest {
        val executor = SshMacActionExecutor(
            credentialProvider = StaticCredentialProvider,
            transport = FakeSshCommandTransport(SshCommandResult.Failed(SshFailureKind.AUTH_FAILED, "SSH authentication failed.")),
            clock = FixedTestClock(125),
        )

        val receipt = executor.execute(plan(), target(), startedAtEpochMillis = 100)

        assertEquals(ReceiptState.UNAVAILABLE, receipt.state)
        assertNotNull(receipt.repair)
        assertEquals("Repair connection", receipt.repair?.actionLabel)
    }

    private fun plan() = ActionPlan(
        invocationId = "inv-1",
        resolvedTargetId = "mac-1",
        steps = listOf(
            PlannedStep(
                stableId = "mac.finder.open",
                executorKind = ExecutorKind.SSH_MAC,
                safeSummary = "Finder",
                timeoutPolicy = TimeoutPolicy(),
            ),
        ),
        expectedCapabilities = setOf(TargetCapability.SSH_APP_CONTROL),
    )

    private fun target() = MacTarget(
        logicalId = "mac-1",
        displayName = "Mac",
        sshIdentity = SshIdentity(
            host = "192.168.1.2",
            username = "sv",
            hostFingerprintSha256 = "SHA256:abc",
        ),
        capabilities = setOf(TargetCapability.SSH_APP_CONTROL),
        trustState = TrustState.TRUSTED,
        liveState = LiveState.ONLINE,
    )
}

private object StaticCredentialProvider : SshCredentialProvider {
    override suspend fun privateKeyFor(target: MacTarget): SshPrivateKeyCredential = PemSshPrivateKeyCredential(
        privateKeyPem = SensitiveChars.copyOf("-----BEGIN PRIVATE KEY-----\nredacted\n-----END PRIVATE KEY-----"),
    )
}

private class FakeSshCommandTransport(
    private val result: SshCommandResult,
) : SshCommandTransport {
    override suspend fun execute(request: SshCommandRequest): SshCommandResult {
        assertEquals("open -a Finder", request.command)
        return result
    }
}

private class FixedTestClock(
    private val now: Long,
) : CodecksClock {
    override fun nowEpochMillis(): Long = now
}
