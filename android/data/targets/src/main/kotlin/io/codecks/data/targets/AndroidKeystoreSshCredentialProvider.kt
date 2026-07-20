package io.codecks.data.targets

import io.codecks.domain.targets.MacTarget
import io.codecks.transport.ssh.KeyPairSshPrivateKeyCredential
import io.codecks.transport.ssh.SshCredentialProvider
import io.codecks.transport.ssh.SshPrivateKeyCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidKeystoreSshCredentialProvider(
    private val keyStore: AndroidKeystoreSshKeyStore = AndroidKeystoreSshKeyStore(),
) : SshCredentialProvider {
    override suspend fun privateKeyFor(target: MacTarget): SshPrivateKeyCredential = withContext(Dispatchers.IO) {
        val alias = SshTargetKeyAlias.forTarget(target)
        KeyPairSshPrivateKeyCredential(
            keyPair = keyStore.getOrCreateKeyPair(alias),
            publicKey = keyStore.publicKeyOpenSsh(alias, "codecks-${target.logicalId}"),
        )
    }

    suspend fun publicKeyFor(target: MacTarget): String = withContext(Dispatchers.IO) {
        keyStore.publicKeyOpenSsh(
            alias = SshTargetKeyAlias.forTarget(target),
            comment = "codecks-${target.logicalId}",
        )
    }

    suspend fun rotate(target: MacTarget): String = withContext(Dispatchers.IO) {
        val alias = SshTargetKeyAlias.forTarget(target)
        keyStore.delete(alias)
        keyStore.publicKeyOpenSsh(alias, "codecks-${target.logicalId}")
    }
}
