package io.codecks.domain.targets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSetupPolicyTest {
    @Test
    fun `blank draft starts at target name`() {
        val steps = TargetSetupPolicy.checklist(TargetSetupDraft())

        assertEquals(TargetSetupStepState.ACTIVE, steps[0].state)
        assertEquals(TargetSetupStepState.TODO, steps[1].state)
        assertEquals(TargetSetupStepState.TODO, steps[4].state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot trust fingerprint before address`() {
        TargetSetupDraft(targetName = "Mac", fingerprintSha256 = "SHA256:abc").trustFingerprint()
    }

    @Test
    fun `trust key install and proof create trusted configured target`() {
        val draft = TargetSetupDraft(
            targetName = "MacBook",
            host = "192.168.1.42",
            username = "sv",
            fingerprintSha256 = "SHA256:abc",
        )
            .trustFingerprint()
            .installPhoneKey("ssh-ed25519 AAAA codecks")

        assertTrue(draft.fingerprintTrusted)
        assertTrue(draft.keyInstalled)
        assertFalse(draft.finderProofSucceeded)

        val target = draft.toMacTarget()
        assertEquals("MacBook", target.displayName)
        assertEquals(TrustState.TRUSTED, target.trustState)
        assertEquals(LiveState.CONFIGURED, target.liveState)
        assertEquals("ssh-ed25519 AAAA codecks", target.sshIdentity?.phonePublicKey)
    }

    @Test
    fun `trusted SSH target exists before key install for public key install`() {
        val draft = TargetSetupDraft(
            targetName = "MacBook",
            host = "192.168.1.42",
            username = "sv",
            fingerprintSha256 = "SHA256:abc",
        ).trustFingerprint()

        val target = draft.toTrustedSshTarget()

        assertEquals("MacBook", target.displayName)
        assertEquals(TrustState.TRUSTED, target.trustState)
        assertEquals(LiveState.CONFIGURED, target.liveState)
    }

    @Test
    fun `finder proof marks final setup step done`() {
        val draft = TargetSetupDraft(
            targetName = "MacBook",
            host = "192.168.1.42",
            username = "sv",
            fingerprintSha256 = "SHA256:abc",
        )
            .trustFingerprint()
            .installPhoneKey("ssh-ed25519 AAAA codecks")
            .markFinderProofSucceeded()

        val steps = TargetSetupPolicy.checklist(draft)

        assertTrue(steps.all { it.state == TargetSetupStepState.DONE })
        assertEquals(LiveState.ONLINE, draft.toMacTarget().liveState)
    }
}
