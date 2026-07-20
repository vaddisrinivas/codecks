package io.codecks.transport.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshjPublicKeyInstallerTest {
    @Test
    fun installCommandQuotesPublicKey() {
        val command = SshjPublicKeyInstaller.authorizedKeysInstallCommand(
            "ssh-rsa AAAA'BBBB codecks",
        )

        assertTrue(command.contains("'\"'\"'"))
        assertTrue(command.contains("grep -qxF"))
    }

    @Test
    fun installCommandHasNoRawNewLineCommandInjection() {
        val command = SshjPublicKeyInstaller.authorizedKeysInstallCommand(
            "ssh-rsa AAAABBBB codecks",
        )

        assertFalse(command.contains("\nrm "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun installCommandRejectsNonSshKeys() {
        SshjPublicKeyInstaller.authorizedKeysInstallCommand("not-a-key")
    }
}
