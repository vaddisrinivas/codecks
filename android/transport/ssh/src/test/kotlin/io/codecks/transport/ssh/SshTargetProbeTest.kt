package io.codecks.transport.ssh

import io.codecks.core.security.SensitiveChars
import io.codecks.domain.actions.TimeoutPolicy
import io.codecks.domain.targets.LiveState
import io.codecks.domain.targets.MacTarget
import io.codecks.domain.targets.SshIdentity
import io.codecks.domain.targets.TargetCapability
import io.codecks.domain.targets.TrustState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SshTargetProbeTest {
    @Test
    fun successfulProbeMarksTargetOnline() = runTest {
        val probe = SshTargetProbe(
            credentialProvider = StaticProbeCredentialProvider,
            transport = RecordingProbeTransport(SshCommandResult.Completed(0, "codecks-ready", "", 10)),
        )

        assertEquals(LiveState.ONLINE, probe.probe(target()))
    }

    @Test
    fun authFailureMapsToAuthFailed() = runTest {
        val probe = SshTargetProbe(
            credentialProvider = StaticProbeCredentialProvider,
            transport = RecordingProbeTransport(SshCommandResult.Failed(SshFailureKind.AUTH_FAILED, "Nope")),
        )

        assertEquals(LiveState.AUTH_FAILED, probe.probe(target()))
    }

    @Test
    fun changedHostKeyMapsToHostKeyChangedWithoutNetwork() = runTest {
        val probe = SshTargetProbe(
            credentialProvider = StaticProbeCredentialProvider,
            transport = RecordingProbeTransport(SshCommandResult.Completed(0, "", "", 1)),
        )

        assertEquals(LiveState.HOST_KEY_CHANGED, probe.probe(target().copy(trustState = TrustState.HOST_KEY_CHANGED)))
    }

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
        liveState = LiveState.CONFIGURED,
    )
}

private object StaticProbeCredentialProvider : SshCredentialProvider {
    override suspend fun privateKeyFor(target: MacTarget): SshPrivateKeyCredential = PemSshPrivateKeyCredential(
        privateKeyPem = SensitiveChars.copyOf("-----BEGIN PRIVATE KEY-----\nredacted\n-----END PRIVATE KEY-----"),
    )
}

private class RecordingProbeTransport(
    private val result: SshCommandResult,
) : SshCommandTransport {
    override suspend fun execute(request: SshCommandRequest): SshCommandResult {
        assertEquals("printf codecks-ready", request.command)
        assertEquals(TimeoutPolicy(runTimeoutMillis = 5_000, outputLimitBytes = 1_024), request.timeoutPolicy)
        return result
    }
}
