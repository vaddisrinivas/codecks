package io.codecks.transport.ssh

import io.codecks.core.security.SensitiveChars
import io.codecks.domain.targets.MacTarget
import java.security.KeyPair

sealed interface SshPrivateKeyCredential

class PemSshPrivateKeyCredential(
    val privateKeyPem: SensitiveChars,
    val publicKey: String? = null,
    val passphrase: SensitiveChars? = null,
) : SshPrivateKeyCredential {
    override fun toString(): String = "SshPrivateKeyCredential(REDACTED)"
}

class KeyPairSshPrivateKeyCredential(
    val keyPair: KeyPair,
    val publicKey: String? = null,
) : SshPrivateKeyCredential {
    override fun toString(): String = "SshPrivateKeyCredential(NON_EXPORTABLE)"
}

interface SshCredentialProvider {
    suspend fun privateKeyFor(target: MacTarget): SshPrivateKeyCredential?
}
