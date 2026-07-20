package io.codecks.data.targets

import io.codecks.domain.targets.LiveState
import io.codecks.domain.targets.MacTarget
import io.codecks.domain.targets.SshIdentity
import io.codecks.domain.targets.TargetCapability
import io.codecks.domain.targets.TrustState
import org.junit.Assert.assertEquals
import org.junit.Test

class SshTargetKeyAliasTest {
    @Test
    fun aliasIsStableAndKeystoreSafe() {
        val target = MacTarget(
            logicalId = "mac:home/one",
            displayName = "Mac",
            sshIdentity = SshIdentity(host = "192.168.1.2", username = "sv"),
            capabilities = setOf(TargetCapability.SSH_APP_CONTROL),
            trustState = TrustState.TRUSTED,
            liveState = LiveState.CONFIGURED,
        )

        assertEquals("codecks.ssh.mac_home_one", SshTargetKeyAlias.forTarget(target))
    }
}
