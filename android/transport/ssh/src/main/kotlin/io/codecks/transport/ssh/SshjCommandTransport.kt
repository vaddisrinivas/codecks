package io.codecks.transport.ssh

import io.codecks.core.common.CodecksClock
import io.codecks.core.common.SystemCodecksClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SshjCommandTransport(
    private val clientFactory: () -> SSHClient = { SSHClient() },
    private val clock: CodecksClock = SystemCodecksClock,
) : SshCommandTransport {
    override suspend fun execute(request: SshCommandRequest): SshCommandResult = withContext(Dispatchers.IO) {
        runBlockingCommand(request)
    }

    private fun runBlockingCommand(request: SshCommandRequest): SshCommandResult {
        val identity = request.target.sshIdentity
            ?: return SshCommandResult.Failed(SshFailureKind.MISSING_IDENTITY, "Target has no SSH identity.")
        val expectedFingerprint = identity.hostFingerprintSha256
            ?: return SshCommandResult.Failed(SshFailureKind.MISSING_HOST_FINGERPRINT, "Host fingerprint is not trusted.")
        val startedAt = clock.nowEpochMillis()
        val client = clientFactory()
        return try {
            client.addHostKeyVerifier(expectedFingerprint)
            client.setConnectTimeout(request.timeoutPolicy.runTimeoutMillis.toInt())
            client.setTimeout(request.timeoutPolicy.runTimeoutMillis.toInt())
            client.connect(identity.host, identity.port)
            val keyProvider = when (val credential = request.credential) {
                is KeyPairSshPrivateKeyCredential -> client.loadKeys(credential.keyPair)
                is PemSshPrivateKeyCredential -> {
                    val passwordFinder = credential.passphrase?.let { passphrase ->
                        object : PasswordFinder {
                            override fun reqPassword(resource: Resource<*>): CharArray = passphrase.copyChars()
                            override fun shouldRetry(resource: Resource<*>): Boolean = false
                        }
                    }
                    client.loadKeys(
                        credential.privateKeyPem.asStringForOneShotInterop(),
                        credential.publicKey,
                        passwordFinder,
                    )
                }
            }
            client.authPublickey(identity.username, keyProvider)
            client.startSession().use { session ->
                runCommand(session, request, startedAt)
            }
        } catch (error: UserAuthException) {
            SshCommandResult.Failed(SshFailureKind.AUTH_FAILED, "SSH authentication failed.")
        } catch (error: TransportException) {
            SshCommandResult.Failed(SshFailureKind.HOST_KEY_CHANGED, "SSH host key did not match the trusted fingerprint.")
        } catch (error: IOException) {
            SshCommandResult.Failed(SshFailureKind.CONNECTION_FAILED, "SSH connection failed.")
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private fun runCommand(
        session: Session,
        request: SshCommandRequest,
        startedAt: Long,
    ): SshCommandResult {
        val command = session.exec(request.command)
        val budget = OutputBudget(request.timeoutPolicy.outputLimitBytes)
        val executor = Executors.newFixedThreadPool(2)
        val stdout = executor.submit<String> { command.inputStream.readUtf8Capped(budget) }
        val stderr = executor.submit<String> { command.errorStream.readUtf8Capped(budget) }
        return try {
            command.join(request.timeoutPolicy.runTimeoutMillis, TimeUnit.MILLISECONDS)
            if (command.isOpen) {
                runCatching { command.close() }
                SshCommandResult.Failed(SshFailureKind.TIMEOUT, "SSH command timed out.")
            } else {
                SshCommandResult.Completed(
                    exitStatus = command.exitStatus,
                    stdout = stdout.get(1, TimeUnit.SECONDS),
                    stderr = stderr.get(1, TimeUnit.SECONDS),
                    durationMillis = clock.nowEpochMillis() - startedAt,
                    outputTruncated = budget.truncated,
                )
            }
        } finally {
            executor.shutdownNow()
            runCatching { command.close() }
        }
    }
}
