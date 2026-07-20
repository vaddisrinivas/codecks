package io.codecks.transport.ssh

import io.codecks.core.common.CodecksClock
import io.codecks.core.common.SystemCodecksClock
import io.codecks.core.security.SensitiveChars
import io.codecks.domain.actions.TimeoutPolicy
import io.codecks.domain.targets.MacTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface SshPublicKeyInstallResult {
    data class Installed(
        val durationMillis: Long,
        val outputTruncated: Boolean = false,
    ) : SshPublicKeyInstallResult

    data class Failed(
        val kind: SshFailureKind,
        val safeMessage: String,
    ) : SshPublicKeyInstallResult
}

interface SshPublicKeyInstaller {
    suspend fun install(
        target: MacTarget,
        publicKey: String,
        password: SensitiveChars,
        timeoutPolicy: TimeoutPolicy = TimeoutPolicy(runTimeoutMillis = 30_000, outputLimitBytes = 8 * 1024),
    ): SshPublicKeyInstallResult
}

class SshjPublicKeyInstaller(
    private val clientFactory: () -> SSHClient = { SSHClient() },
    private val clock: CodecksClock = SystemCodecksClock,
) : SshPublicKeyInstaller {
    override suspend fun install(
        target: MacTarget,
        publicKey: String,
        password: SensitiveChars,
        timeoutPolicy: TimeoutPolicy,
    ): SshPublicKeyInstallResult = withContext(Dispatchers.IO) {
        runBlockingInstall(target, publicKey, password, timeoutPolicy)
    }

    private fun runBlockingInstall(
        target: MacTarget,
        publicKey: String,
        password: SensitiveChars,
        timeoutPolicy: TimeoutPolicy,
    ): SshPublicKeyInstallResult {
        val identity = target.sshIdentity
            ?: return SshPublicKeyInstallResult.Failed(SshFailureKind.MISSING_IDENTITY, "Target has no SSH identity.")
        val expectedFingerprint = identity.hostFingerprintSha256
            ?: return SshPublicKeyInstallResult.Failed(SshFailureKind.MISSING_HOST_FINGERPRINT, "Host fingerprint is not trusted.")
        val client = clientFactory()
        val startedAt = clock.nowEpochMillis()
        return try {
            client.addHostKeyVerifier(expectedFingerprint)
            client.setConnectTimeout(timeoutPolicy.runTimeoutMillis.toInt())
            client.setTimeout(timeoutPolicy.runTimeoutMillis.toInt())
            client.connect(identity.host, identity.port)
            val passwordCopy = password.copyChars()
            try {
                client.authPassword(identity.username, passwordCopy)
            } finally {
                passwordCopy.fill('\u0000')
            }
            client.startSession().use { session ->
                runInstallCommand(
                    session = session,
                    commandText = authorizedKeysInstallCommand(publicKey),
                    timeoutPolicy = timeoutPolicy,
                    startedAt = startedAt,
                )
            }
        } catch (error: UserAuthException) {
            SshPublicKeyInstallResult.Failed(SshFailureKind.AUTH_FAILED, "SSH password authentication failed.")
        } catch (error: TransportException) {
            SshPublicKeyInstallResult.Failed(SshFailureKind.HOST_KEY_CHANGED, "SSH host key did not match the trusted fingerprint.")
        } catch (error: IOException) {
            SshPublicKeyInstallResult.Failed(SshFailureKind.CONNECTION_FAILED, "SSH connection failed.")
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private fun runInstallCommand(
        session: Session,
        commandText: String,
        timeoutPolicy: TimeoutPolicy,
        startedAt: Long,
    ): SshPublicKeyInstallResult {
        val command = session.exec(commandText)
        val budget = OutputBudget(timeoutPolicy.outputLimitBytes)
        return try {
            command.join(timeoutPolicy.runTimeoutMillis, TimeUnit.MILLISECONDS)
            command.inputStream.readUtf8Capped(budget)
            command.errorStream.readUtf8Capped(budget)
            when {
                command.isOpen -> {
                    runCatching { command.close() }
                    SshPublicKeyInstallResult.Failed(SshFailureKind.TIMEOUT, "SSH key install timed out.")
                }
                command.exitStatus == 0 -> SshPublicKeyInstallResult.Installed(
                    durationMillis = clock.nowEpochMillis() - startedAt,
                    outputTruncated = budget.truncated,
                )
                else -> SshPublicKeyInstallResult.Failed(SshFailureKind.CONNECTION_FAILED, "SSH key install command failed.")
            }
        } finally {
            runCatching { command.close() }
        }
    }

    companion object {
        fun authorizedKeysInstallCommand(publicKey: String): String {
            require(publicKey.startsWith("ssh-")) { "OpenSSH public key required" }
            val quotedKey = ShellQuoter.single(publicKey)
            return "umask 077; mkdir -p ~/.ssh; touch ~/.ssh/authorized_keys; " +
                "grep -qxF $quotedKey ~/.ssh/authorized_keys || printf '%s\\n' $quotedKey >> ~/.ssh/authorized_keys"
        }
    }
}
