package io.codecks.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.core.actions.RawCommandPolicy
import io.codecks.data.ai.EncryptedApiKeyCodec
import io.codecks.data.persistence.AtomicBoundedFileStore
import io.codecks.data.persistence.BoundedPayload
import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.TransactionalFilePairStore
import io.codecks.domain.reactive.SafeSftpTransferRequest
import io.codecks.domain.reactive.TransferDirection
import io.codecks.domain.connection.ChangedHostKeyException
import io.codecks.domain.connection.HostTrustState
import io.codecks.domain.connection.evaluateHostTrust
import io.codecks.ui.connection.TerminalProofExecutionGuard
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

private val Context.connectionDataStore by preferencesDataStore(name = "connection")
private const val SSH_COMMAND_TIMEOUT_MS = 30_000
private const val SSH_OUTPUT_LIMIT_BYTES = 64 * 1024

data class ConnectionConfig(
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val hasKey: Boolean = false,
    val hostKey: String = "",
) {
    val isConfigured: Boolean get() = host.isNotBlank() && user.isNotBlank() && port in 1..65535
    val isReady: Boolean get() = isConfigured && hasKey && hostKey.isNotBlank()
}

/** Candidate work is complete before [commit] may touch the live SSH key files. */
internal fun prepareAndCommitSshKeyPair(
    generate: () -> Pair<String, String>,
    encrypt: (String) -> String,
    validatePlain: (String, String) -> Unit,
    validateStored: (String, String) -> Unit,
    commit: (String, String, (String, String) -> Unit) -> Unit,
    afterCommit: () -> Unit = {},
): String {
    val (privateKey, publicKey) = generate()
    validatePlain(privateKey, publicKey)
    val encryptedPrivate = encrypt(privateKey)
    validateStored(encryptedPrivate, publicKey)
    commit(encryptedPrivate, publicKey, validateStored)
    afterCommit()
    return publicKey.trim()
}

data class ConnectionTarget(
    val id: String,
    val host: String,
    val port: Int,
    val user: String,
    val hasKey: Boolean,
    val hostKey: String,
) {
    val isConfigured: Boolean get() = host.isNotBlank() && user.isNotBlank() && port in 1..65535
    val isReady: Boolean get() = isConfigured && hasKey && hostKey.isNotBlank()

    fun toConfig(): ConnectionConfig = ConnectionConfig(
        host = host,
        port = port,
        user = user,
        hasKey = hasKey,
        hostKey = hostKey,
    )
}

data class SshResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val hostKey: String = "",
    val outputTruncated: Boolean = false,
) {
    val isSuccess: Boolean get() = exitCode == 0
    val summary: String
        get() = (if (isSuccess) stdout else stderr).trim().ifBlank {
            if (isSuccess) "Command completed" else "SSH exited with code $exitCode"
        }.let {
            if (outputTruncated) "${it.take(220)}\n[output truncated]" else it
        }.take(240)
}

data class HostKeyVerification(
    val message: String,
    val fingerprint: String? = null,
    val confirmationRequired: Boolean = false,
)

interface ConnectionRepository {
    val config: Flow<ConnectionConfig>
    suspend fun save(host: String, port: Int, user: String)
    suspend fun generateKey(): Result<String>
    suspend fun publicKey(): String
    suspend fun trustHostKey(): Result<String>
    suspend fun verifyHostKey(): Result<HostKeyVerification> = trustHostKey().map { message ->
        val fingerprintPrefix = "Fingerprint found:"
        HostKeyVerification(
            message = message,
            fingerprint = message
                .takeIf { it.startsWith(fingerprintPrefix) }
                ?.removePrefix(fingerprintPrefix)
                ?.trim(),
            confirmationRequired = message.startsWith(fingerprintPrefix),
        )
    }
    suspend fun confirmPendingHostKey(): Result<String>
    suspend fun rotateKey(): Result<String>
    suspend fun resetTrust(): Result<String>
    suspend fun installKey(password: String): Result<String>
    suspend fun test(password: String? = null): Result<String>
    suspend fun runRequiredSetupProbe(): Result<String> =
        Result.failure(UnsupportedOperationException("required_setup_probe_unavailable"))
    suspend fun runAction(actionId: String, dangerous: Boolean): Result<String>
    suspend fun runCommand(command: String): Result<String>
    suspend fun runCommandRaw(command: String): Result<String> = runCommand(command)
    suspend fun runCommandWithInput(command: String, stdin: String): Result<String>
    suspend fun validateCommandSyntax(command: String): Result<String>
    suspend fun writeMacClipboard(text: String): Result<String> = runCommandWithInput("pbcopy", text)
    suspend fun runCommandSecret(command: String): Result<String>
    suspend fun savedTargets(): List<ConnectionTarget> = emptyList()
    suspend fun legacyTargetIdMigrations(): Map<String, String> = emptyMap()
    suspend fun selectTarget(targetId: String): Result<String>
    suspend fun removeTarget(targetId: String): Result<String>
    suspend fun runActionOnTarget(targetId: String, actionId: String, dangerous: Boolean): Result<String> =
        runAction(actionId, dangerous)
    suspend fun runCommandOnTarget(targetId: String, command: String): Result<String> = runCommand(command)
    suspend fun runReviewedCommandOnTarget(targetId: String, command: String): Result<String> =
        runCommandOnTarget(targetId, command)
    suspend fun runBundledCommand(command: String): Result<String> = runCommand(command)
    suspend fun runBundledCommandOnTarget(targetId: String, command: String): Result<String> =
        runCommandOnTarget(targetId, command)
    suspend fun runSftpTransferOnTarget(targetId: String, request: SafeSftpTransferRequest): Result<String> =
        Result.failure(UnsupportedOperationException("sftp_transfer_unavailable"))
}

@Singleton
class DefaultConnectionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConnectionRepository {
    private val keyRevision = MutableStateFlow(0)
    private val privateKeyCodec = EncryptedApiKeyCodec("ssh.private")
    private val terminalProofExecutionGuard = TerminalProofExecutionGuard(context)

    override val config: Flow<ConnectionConfig> = combine(
        context.connectionDataStore.data.onStart { migrateTargetIdentities() },
        keyRevision,
    ) { preferences, _ ->
        val hasKey = hasPrivateKey() && hasPublicKey()
        preferences.currentTarget(hasKey)?.toConfig() ?: ConnectionConfig(
            host = preferences[HOST].orEmpty(),
            port = preferences[PORT] ?: 22,
            user = preferences[USER].orEmpty(),
            hasKey = hasKey,
            hostKey = preferences[HOST_KEY].orEmpty(),
        )
    }

    override suspend fun save(host: String, port: Int, user: String) {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        check(migrateTargetIdentities()) {
            "Saved Mac targets could not be decoded. Existing data was preserved for recovery."
        }
        context.connectionDataStore.edit { preferences ->
            val normalizedHost = host.trim()
            val normalizedUser = user.trim()
            val endpointChanged = preferences[HOST] != normalizedHost ||
                preferences[PORT] != port || preferences[USER] != normalizedUser
            val currentTargets = preferences.targets(hasKey = hasPrivateKey() && hasPublicKey())
            val previous = currentTargets.firstOrNull {
                it.sameEndpoint(normalizedHost, port, normalizedUser)
            }
            val targetId = previous?.id ?: newOpaqueTargetId()
            val nextTarget = ConnectionTarget(
                id = targetId,
                host = normalizedHost,
                port = port,
                user = normalizedUser,
                hasKey = hasPrivateKey() && hasPublicKey(),
                hostKey = if (endpointChanged) previous?.hostKey.orEmpty() else preferences[HOST_KEY].orEmpty(),
            )
            preferences[TARGETS] = currentTargets
                .filterNot { it.id == targetId || it.sameEndpoint(normalizedHost, port, normalizedUser) }
                .plus(nextTarget)
                .toJson()
            preferences[CURRENT_TARGET_ID] = targetId
            preferences[HOST] = normalizedHost
            preferences[PORT] = port
            preferences[USER] = normalizedUser
            if (endpointChanged) {
                preferences.remove(PENDING_HOST_KEY)
                preferences.remove(PENDING_HOST_FINGERPRINT)
                if (previous?.hostKey.isNullOrBlank()) preferences.remove(HOST_KEY)
            }
        }
    }

    override suspend fun generateKey(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            recoverKeyPairTransaction()
            val hasPrivate = hasPrivateKey()
            val hasPublic = hasPublicKey()
            check(hasPrivate == hasPublic) { "Incomplete SSH keypair preserved; repair is required" }
            if (!hasPrivate) {
                prepareAndCommitSshKeyPair(
                    generate = ::generateSshKeyPairCandidate,
                    encrypt = privateKeyCodec::encrypt,
                    validatePlain = ::validatePlainKeyPair,
                    validateStored = ::validateStoredKeyPair,
                    commit = { privateKey, publicKey, validate ->
                        keyPairTransaction().commit(privateKey, publicKey, validate)
                    },
                )
                hardenPrivateKeyFile()
            } else {
                validatePlainKeyPair(readPrivateKey(), readPublicKey())
            }
            hardenPrivateKeyFile()
            keyRevision.value += 1
            readPublicKey().trim()
        }
    }

    override suspend fun publicKey(): String = withContext(Dispatchers.IO) {
        readPublicKeyOrNull()?.trim().orEmpty()
    }

    override suspend fun verifyHostKey(): Result<HostKeyVerification> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            require(current.isConfigured) { "Save the Mac address and username first" }
            val hostKey = fetchHostKey(current)
            when (evaluateHostTrust(current.hostKey, hostKey.line)) {
                HostTrustState.FirstSeenConfirmationRequired -> {
                    rememberPendingHostKey(hostKey)
                    HostKeyVerification(
                        message = "Fingerprint found: ${hostKey.fingerprint}",
                        fingerprint = hostKey.fingerprint,
                        confirmationRequired = true,
                    )
                }
                HostTrustState.Trusted -> HostKeyVerification(
                    message = "Mac fingerprint already trusted",
                )
                HostTrustState.ChangedHostKeyBlocked -> throw ChangedHostKeyException()
            }
        }
    }

    override suspend fun trustHostKey(): Result<String> = verifyHostKey().map(HostKeyVerification::message)

    override suspend fun confirmPendingHostKey(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val preferences = context.connectionDataStore.data.first()
            val pendingLine = preferences[PENDING_HOST_KEY].orEmpty()
            val pendingFingerprint = preferences[PENDING_HOST_FINGERPRINT].orEmpty()
            require(pendingLine.isNotBlank()) { "Verify the Mac fingerprint first" }
            val current = currentConfig()
            val observed = fetchHostKey(current)
            if (evaluateHostTrust(pendingLine, observed.line) != HostTrustState.Trusted) {
                throw ChangedHostKeyException()
            }
            rememberHostKey(pendingLine)
            context.connectionDataStore.edit {
                it.remove(PENDING_HOST_KEY)
                it.remove(PENDING_HOST_FINGERPRINT)
            }
            "Fingerprint trusted: ${pendingFingerprint.ifBlank { "saved" }}"
        }
    }

    override suspend fun rotateKey(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            recoverKeyPairTransaction()
            val publicKey = prepareAndCommitSshKeyPair(
                generate = ::generateSshKeyPairCandidate,
                encrypt = privateKeyCodec::encrypt,
                validatePlain = ::validatePlainKeyPair,
                validateStored = ::validateStoredKeyPair,
                commit = { privateKey, candidatePublicKey, validate ->
                    keyPairTransaction().commit(privateKey, candidatePublicKey, validate)
                },
                afterCommit = {
                    legacyPrivateKeyFile().delete()
                    legacyPublicKeyFile().delete()
                },
            )
            hardenPrivateKeyFile()
            keyRevision.value += 1
            "New SSH key ready. Reinstall it on your Mac.\n$publicKey"
        }
    }

    override suspend fun resetTrust(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            migrateTargetIdentities()
            context.connectionDataStore.edit { preferences ->
                val hasKey = hasPrivateKey() && hasPublicKey()
                val current = preferences.currentTarget(hasKey)
                preferences.remove(HOST_KEY)
                preferences.remove(PENDING_HOST_KEY)
                preferences.remove(PENDING_HOST_FINGERPRINT)
                if (current != null) {
                    preferences[TARGETS] = preferences.targets(hasKey)
                        .map { target ->
                            if (target.id == current.id) target.copy(hostKey = "") else target
                        }
                        .toJson()
                }
            }
            "Mac fingerprint reset. Verify the fingerprint again before running commands."
        }
    }

    override suspend fun savedTargets(): List<ConnectionTarget> {
        migrateTargetIdentities()
        val hasKey = hasPrivateKey() && hasPublicKey()
        return context.connectionDataStore.data.first().targets(hasKey)
    }

    override suspend fun legacyTargetIdMigrations(): Map<String, String> {
        if (!migrateTargetIdentities()) return emptyMap()
        val hasKey = hasPrivateKey() && hasPublicKey()
        return legacyConnectionTargetIdMigrations(
            context.connectionDataStore.data.first().targets(hasKey),
        )
    }

    override suspend fun selectTarget(targetId: String): Result<String> = runCatching {
        check(migrateTargetIdentities()) {
            "Saved Mac targets could not be decoded. Existing data was preserved for recovery."
        }
        var selected: ConnectionTarget? = null
        context.connectionDataStore.edit { preferences ->
            val hasKey = hasPrivateKey() && hasPublicKey()
            selected = preferences.targets(hasKey).firstOrNull { it.id == targetId }
            val target = selected ?: error("Target not found")
            preferences[CURRENT_TARGET_ID] = target.id
            preferences[HOST] = target.host
            preferences[PORT] = target.port
            preferences[USER] = target.user
            if (target.hostKey.isNotBlank()) {
                preferences[HOST_KEY] = target.hostKey
            } else {
                preferences.remove(HOST_KEY)
            }
        }
        "Switched to ${selected?.host.orEmpty()}"
    }

    override suspend fun removeTarget(targetId: String): Result<String> = runCatching {
        check(migrateTargetIdentities()) {
            "Saved Mac targets could not be decoded. Existing data was preserved for recovery."
        }
        var removed: ConnectionTarget? = null
        context.connectionDataStore.edit { preferences ->
            val hasKey = hasPrivateKey() && hasPublicKey()
            val targets = preferences.targets(hasKey)
            removed = targets.firstOrNull { it.id == targetId } ?: error("Target not found")
            val removedId = requireNotNull(removed).id
            val remaining = targets.filterNot { it.id == removedId }
            preferences[TARGETS] = remaining.toJson()
            val next = remaining.firstOrNull()
            if (preferences[CURRENT_TARGET_ID] == removedId || next == null) {
                if (next == null) {
                    preferences.remove(CURRENT_TARGET_ID)
                    preferences.remove(HOST)
                    preferences.remove(PORT)
                    preferences.remove(USER)
                    preferences.remove(HOST_KEY)
                    preferences.remove(PENDING_HOST_KEY)
                    preferences.remove(PENDING_HOST_FINGERPRINT)
                } else {
                    preferences[CURRENT_TARGET_ID] = next.id
                    preferences[HOST] = next.host
                    preferences[PORT] = next.port
                    preferences[USER] = next.user
                    if (next.hostKey.isNotBlank()) preferences[HOST_KEY] = next.hostKey else preferences.remove(HOST_KEY)
                }
            }
        }
        "Removed ${removed?.host.orEmpty()}"
    }

    override suspend fun installKey(password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(password.isNotEmpty()) { "Enter the Mac password for this one-time authorization" }
            val current = currentConfig()
            require(current.isConfigured) { "Save the Mac address and username first" }
            require(current.hostKey.isNotBlank()) { "Verify the Mac fingerprint before installing the SSH key" }
            val publicKey = generateKey().getOrThrow()
            val escapedKey = shellQuote(publicKey)
            val command = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && " +
                "(grep -qxF $escapedKey ~/.ssh/authorized_keys 2>/dev/null || " +
                "printf '%s\\n' $escapedKey >> ~/.ssh/authorized_keys) && " +
                "chmod 600 ~/.ssh/authorized_keys"
            val result = runSsh(current, password, privateKey = null, command = command)
            check(result.isSuccess) { result.summary }
            "SSH key installed"
        }
    }

    override suspend fun test(password: String?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            require(current.isConfigured) { "Save the Mac address and username first" }
            val privateKey = readPrivateKeyOrNull()
            require(!privateKey.isNullOrBlank() || !password.isNullOrEmpty()) {
                "Generate or install the SSH key first"
            }
            require(current.hostKey.isNotBlank()) { "Verify the Mac fingerprint before testing SSH" }
            val result = runSsh(
                config = current,
                password = password,
                privateKey = privateKey,
                command = "printf 'Connected to '; hostname",
            )
            check(result.isSuccess) { result.summary }
            result.summary
        }
    }

    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> =
        Result.failure(IllegalStateException("Legacy catalog runner removed; use an inline reviewed command"))

    override suspend fun runActionOnTarget(targetId: String, actionId: String, dangerous: Boolean): Result<String> =
        Result.failure(IllegalStateException("Legacy catalog runner removed; use an inline reviewed command"))

    override suspend fun runCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            terminalProofExecutionGuard.requireVerified(current)
            require(current.isReady) { "Connect your Mac first" }
            require(command.isNotBlank()) { "Command is empty" }
            RawCommandPolicy.requireSafeTemplate(command)
            val result = runSsh(current, null, readPrivateKey(), command)
            check(result.isSuccess) { result.summary }
            result.summary
        }
    }

    override suspend fun runRequiredSetupProbe(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            require(current.isReady) { "Connect your Mac first" }
            val command = io.codecks.domain.connection.requiredCoreMacCapabilityProbeCommand()
            RawCommandPolicy.requireSafeTemplate(command)
            val result = runSsh(current, null, readPrivateKey(), command)
            check(result.isSuccess) { result.summary }
            result.summary
        }
    }

    override suspend fun runBundledCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            terminalProofExecutionGuard.requireVerified(current)
            require(current.isReady) { "Connect your Mac first" }
            require(command.isNotBlank()) { "Command is empty" }
            RawCommandPolicy.requireAllowed(command)
            val result = runSsh(current, null, readPrivateKey(), command)
            check(result.isSuccess) { result.summary }
            result.summary
        }
    }

    override suspend fun runCommandRaw(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            terminalProofExecutionGuard.requireVerified(current)
            require(current.isReady) { "Connect your Mac first" }
            require(command.isNotBlank()) { "Command is empty" }
            RawCommandPolicy.requireSafeTemplate(command)
            val result = runSsh(current, null, readPrivateKey(), command)
            check(result.isSuccess) { result.summary }
            result.stdout.trim()
        }
    }

    override suspend fun runCommandWithInput(command: String, stdin: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            terminalProofExecutionGuard.requireVerified(current)
            require(current.isReady) { "Connect your Mac first" }
            require(command.isNotBlank()) { "Command is empty" }
            RawCommandPolicy.requireSafeTemplate(command)
            val result = runSsh(current, null, readPrivateKey(), command, stdin)
            check(result.isSuccess) { result.summary }
            result.summary
        }
    }

    override suspend fun validateCommandSyntax(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            terminalProofExecutionGuard.requireVerified(current)
            require(current.isReady) { "Connect your Mac first" }
            require(command.isNotBlank()) { "Command is empty" }
            val result = runSsh(current, null, readPrivateKey(), "zsh -n", command)
            check(result.isSuccess) { result.summary }
            "Command syntax verified"
        }
    }

    override suspend fun runCommandSecret(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            terminalProofExecutionGuard.requireVerified(current)
            require(current.isReady) { "Connect your Mac first" }
            require(command.isNotBlank()) { "Command is empty" }
            RawCommandPolicy.requireSafeTemplate(command)
            val result = runSsh(current, null, readPrivateKey(), command)
            check(result.isSuccess) { result.summary }
            result.stdout.trim()
        }
    }

    override suspend fun runCommandOnTarget(targetId: String, command: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = targetById(targetId)
                terminalProofExecutionGuard.requireVerified(target.toConfig())
                require(target.isReady) { "Connect ${target.host} first" }
                require(command.isNotBlank()) { "Command is empty" }
                RawCommandPolicy.requireSafeTemplate(command)
                val result = runSsh(target.toConfig(), null, readPrivateKey(), command)
                check(result.isSuccess) { result.summary }
                result.summary
            }
        }

    override suspend fun runReviewedCommandOnTarget(targetId: String, command: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = targetById(targetId)
                terminalProofExecutionGuard.requireVerified(target.toConfig())
                require(target.isReady) { "Connect ${target.host} first" }
                require(command.isNotBlank()) { "Command is empty" }
                RawCommandPolicy.requireAllowed(command)
                val result = runSsh(target.toConfig(), null, readPrivateKey(), command)
                check(result.isSuccess) { result.summary }
                result.summary
            }
        }


    override suspend fun runBundledCommandOnTarget(targetId: String, command: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = targetById(targetId)
                terminalProofExecutionGuard.requireVerified(target.toConfig())
                require(target.isReady) { "Connect ${target.host} first" }
                require(command.isNotBlank()) { "Command is empty" }
                RawCommandPolicy.requireAllowed(command)
                val result = runSsh(target.toConfig(), null, readPrivateKey(), command)
                check(result.isSuccess) { result.summary }
                result.summary
            }
        }

    override suspend fun runSftpTransferOnTarget(
        targetId: String,
        request: SafeSftpTransferRequest,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val target = targetById(targetId)
            terminalProofExecutionGuard.requireVerified(target.toConfig())
            require(target.isReady) { "Connect ${target.host} first" }
            runSftpTransfer(target.toConfig(), readPrivateKey(), request)
            "SFTP transfer completed"
        }
    }

    private suspend fun currentConfig(): ConnectionConfig = config.first()

    private suspend fun targetById(targetId: String): ConnectionTarget {
        check(migrateTargetIdentities()) {
            "Saved Mac targets could not be decoded. Existing data was preserved for recovery."
        }
        val hasKey = hasPrivateKey() && hasPublicKey()
        return context.connectionDataStore.data.first().targets(hasKey)
            .firstOrNull { it.id == targetId }
            ?: error("Target not found")
    }

    private fun runSsh(
        config: ConnectionConfig,
        password: String?,
        privateKey: String?,
        command: String,
        stdin: String? = null,
    ): SshResult {
        require(config.hostKey.isNotBlank()) { "Verify the Mac fingerprint first" }
        var session: Session? = null
        var channel: ChannelExec? = null
        val startedAt = System.currentTimeMillis()
        return try {
            val jsch = JSch()
            if (config.hostKey.isNotBlank()) {
                jsch.setKnownHosts(ByteArrayInputStream((config.hostKey + "\n").toByteArray()))
            }
            if (!privateKey.isNullOrBlank()) {
                jsch.addIdentity("Codecks", privateKey.toByteArray(), null, null)
            }
            session = jsch.getSession(config.user, config.host, config.port).apply {
                if (!password.isNullOrEmpty()) setPassword(password.toByteArray())
                setConfig(
                    Properties().apply {
                        put("StrictHostKeyChecking", if (config.hostKey.isBlank()) "no" else "yes")
                        put("PreferredAuthentications", "publickey,password,keyboard-interactive")
                    },
                )
                connect(CONNECT_TIMEOUT_MS)
            }
            val hostKeyName = if (config.port == 22) config.host else "[${config.host}]:${config.port}"
            val hostKeyLine = "$hostKeyName ${session.hostKey.type} ${session.hostKey.key}"
            channel = (session.openChannel("exec") as ChannelExec).apply {
                setCommand(command)
                if (stdin != null) {
                    setInputStream(ByteArrayInputStream(stdin.toByteArray(Charsets.UTF_8)))
                }
            }
            val standardOutput = channel.inputStream
            val errorStream = channel.errStream
            channel.connect(CONNECT_TIMEOUT_MS)
            val output = ByteArrayOutputStream()
            val errorOutput = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            val errorBuffer = ByteArray(4096)
            while (true) {
                if (System.currentTimeMillis() - startedAt > SSH_COMMAND_TIMEOUT_MS) {
                    throw SocketTimeoutException("SSH command timed out after ${SSH_COMMAND_TIMEOUT_MS}ms")
                }
                standardOutput.readAvailableBounded(output, buffer)
                errorStream.readAvailableBounded(errorOutput, errorBuffer)
                if (channel.isClosed) {
                    standardOutput.readAvailableBounded(output, buffer)
                    errorStream.readAvailableBounded(errorOutput, errorBuffer)
                    break
                }
                Thread.sleep(25)
            }
            SshResult(
                exitCode = channel.exitStatus,
                stdout = output.toString(Charsets.UTF_8.name()),
                stderr = errorOutput.toString(Charsets.UTF_8.name()),
                hostKey = hostKeyLine,
                outputTruncated = output.size() >= SSH_OUTPUT_LIMIT_BYTES || errorOutput.size() >= SSH_OUTPUT_LIMIT_BYTES,
            )
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    private fun runSftpTransfer(
        config: ConnectionConfig,
        privateKey: String?,
        request: SafeSftpTransferRequest,
    ) {
        require(config.hostKey.isNotBlank()) { "Verify the Mac fingerprint first" }
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            val jsch = JSch()
            jsch.setKnownHosts(ByteArrayInputStream((config.hostKey + "\n").toByteArray()))
            if (!privateKey.isNullOrBlank()) {
                jsch.addIdentity("Codecks", privateKey.toByteArray(), null, null)
            }
            session = jsch.getSession(config.user, config.host, config.port).apply {
                setConfig(
                    Properties().apply {
                        put("StrictHostKeyChecking", "yes")
                        put("PreferredAuthentications", "publickey")
                    },
                )
                connect(CONNECT_TIMEOUT_MS)
            }
            channel = (session.openChannel("sftp") as ChannelSftp).apply {
                connect(CONNECT_TIMEOUT_MS)
            }
            when (request.direction) {
                TransferDirection.MacToPhone -> {
                    val remoteSize = channel.stat(request.remotePath).size
                    require(remoteSize in 0..request.maxBytes) { "SFTP remote file exceeds maxBytes" }
                    File(request.localPath).parentFile?.mkdirs()
                    channel.get(request.remotePath, request.localPath)
                }
                TransferDirection.PhoneToMac -> {
                    val localFile = File(request.localPath)
                    require(localFile.isFile) { "SFTP local file missing" }
                    require(localFile.length() in 0..request.maxBytes) { "SFTP local file exceeds maxBytes" }
                    channel.put(request.localPath, request.remotePath, ChannelSftp.OVERWRITE)
                }
            }
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    private fun fetchHostKey(config: ConnectionConfig): VerifiedHostKey {
        var session: Session? = null
        val jsch = JSch()
        return try {
            session = jsch.getSession(config.user, config.host, config.port).apply {
                setConfig(
                    Properties().apply {
                        put("StrictHostKeyChecking", "no")
                        put("PreferredAuthentications", "none")
                    },
                )
            }
            try {
                session.connect(CONNECT_TIMEOUT_MS)
            } catch (error: JSchException) {
                if (session.hostKey == null) throw error
            }
            val hostKey = session.hostKey ?: error("Could not read Mac fingerprint")
            val hostKeyName = if (config.port == 22) config.host else "[${config.host}]:${config.port}"
            VerifiedHostKey(
                line = "$hostKeyName ${hostKey.type} ${hostKey.key}",
                fingerprint = "${hostKey.type} ${hostKey.getFingerPrint(jsch)}",
            )
        } finally {
            session?.disconnect()
        }
    }

    private fun privateKeyFile() = context.filesDir.resolve("codecks_ssh_private.enc")
    private fun legacyPrivateKeyFile() = context.filesDir.resolve("deckbridge_ssh_private")
    private fun publicKeyFile() = context.filesDir.resolve("codecks_ssh_public")
    private fun legacyPublicKeyFile() = context.filesDir.resolve("deckbridge_ssh_public")
    private fun privateKeyStore() = AtomicBoundedFileStore(privateKeyFile(), MAX_PRIVATE_KEY_BYTES)
    private fun publicKeyStore() = AtomicBoundedFileStore(publicKeyFile(), MAX_PUBLIC_KEY_BYTES)
    private fun keyPairTransaction() = TransactionalFilePairStore(
        privateKeyFile(), publicKeyFile(), MAX_PRIVATE_KEY_BYTES, MAX_PUBLIC_KEY_BYTES,
    )

    private fun recoverKeyPairTransaction() = keyPairTransaction().recover(::validateStoredKeyPair)

    private fun hasPrivateKey(): Boolean {
        recoverKeyPairTransaction()
        migrateLegacyKeyPairIfComplete()
        return privateKeyFile().exists()
    }
    private fun hasPublicKey(): Boolean {
        recoverKeyPairTransaction()
        migrateLegacyKeyPairIfComplete()
        return publicKeyFile().exists()
    }

    private fun migrateLegacyKeyPairIfComplete() {
        val hasNewPrivate = privateKeyFile().isFile
        val hasNewPublic = publicKeyFile().isFile
        if (hasNewPrivate || hasNewPublic) {
            check(hasNewPrivate && hasNewPublic) { "Incomplete SSH keypair preserved; repair is required" }
            return
        }
        val hasLegacyPrivate = legacyPrivateKeyFile().isFile
        val hasLegacyPublic = legacyPublicKeyFile().isFile
        if (!hasLegacyPrivate && !hasLegacyPublic) return
        check(hasLegacyPrivate && hasLegacyPublic) { "Incomplete legacy SSH keypair preserved; repair is required" }
        check(legacyPrivateKeyFile().length() <= MAX_PRIVATE_KEY_PLAINTEXT_BYTES)
        check(legacyPublicKeyFile().length() <= MAX_PUBLIC_KEY_BYTES)
        val privateKey = legacyPrivateKeyFile().readText().takeIf(String::isNotBlank)
            ?: error("Legacy SSH private key is blank")
        val publicKey = legacyPublicKeyFile().readText().takeIf(String::isNotBlank)
            ?: error("Legacy SSH public key is blank")
        validatePlainKeyPair(privateKey, publicKey)
        keyPairTransaction().commit(privateKeyCodec.encrypt(privateKey), publicKey, ::validateStoredKeyPair)
        hardenPrivateKeyFile()
        legacyPrivateKeyFile().delete()
        legacyPublicKeyFile().delete()
    }

    private fun readPrivateKeyOrNull(): String? {
        recoverKeyPairTransaction()
        migrateLegacyKeyPairIfComplete()
        if (privateKeyFile().exists()) {
            return privateKeyCodec.decrypt(privateKeyStore().read().requiredValue("SSH private key"))
        }
        return null
    }

    private fun readPrivateKey(): String =
        requireNotNull(readPrivateKeyOrNull()) { "Generate or install the SSH key first" }

    private fun readPublicKeyOrNull(): String? {
        recoverKeyPairTransaction()
        migrateLegacyKeyPairIfComplete()
        if (publicKeyFile().exists()) return publicKeyStore().read().requiredValue("SSH public key")
        return null
    }

    private fun readPublicKey(): String =
        requireNotNull(readPublicKeyOrNull()) { "Generate or install the SSH key first" }

    private fun hardenPrivateKeyFile() {
        privateKeyFile().takeIf { it.exists() }?.apply {
            setReadable(false, false)
            setWritable(false, false)
            setExecutable(false, false)
            setReadable(true, true)
            setWritable(true, true)
        }
    }

    private fun validateStoredKeyPair(encryptedPrivate: String, publicKey: String) =
        validatePlainKeyPair(privateKeyCodec.decrypt(encryptedPrivate), publicKey)

    private fun generateSshKeyPairCandidate(): Pair<String, String> {
        val keyPair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 3072)
        return try {
            val privateOutput = ByteArrayOutputStream()
            val publicOutput = ByteArrayOutputStream()
            keyPair.writePrivateKey(privateOutput)
            keyPair.writePublicKey(publicOutput, "codecks")
            BoundedPayload.utf8(
                privateOutput.toString(Charsets.UTF_8.name()),
                MAX_PRIVATE_KEY_PLAINTEXT_BYTES,
            ) to BoundedPayload.utf8(
                publicOutput.toString(Charsets.UTF_8.name()),
                MAX_PUBLIC_KEY_BYTES,
            )
        } finally {
            keyPair.dispose()
        }
    }

    private fun validatePlainKeyPair(privateKey: String, publicKey: String) {
        val loaded = KeyPair.load(JSch(), privateKey.toByteArray(), null)
        try {
            val derived = ByteArrayOutputStream().also { loaded.writePublicKey(it, "codecks") }
                .toString(Charsets.UTF_8.name())
            check(publicMaterial(derived) == publicMaterial(publicKey)) { "SSH public/private key mismatch" }
        } finally {
            loaded.dispose()
        }
    }

    private fun publicMaterial(value: String): String = value.trim().split(Regex("\\s+")).take(2).joinToString(" ")

    private fun PersistenceRead<String>.requiredValue(label: String): String = when (this) {
        is PersistenceRead.Value -> value
        PersistenceRead.Missing -> error("$label is missing")
        is PersistenceRead.Corrupt -> error("$label is corrupt")
        PersistenceRead.KeyUnavailable -> error("$label key is unavailable")
        is PersistenceRead.FutureVersion -> error("$label is from a newer app")
    }

    private suspend fun rememberHostKey(hostKey: String) {
        if (hostKey.isBlank()) return
        migrateTargetIdentities()
        context.connectionDataStore.edit { preferences ->
            val hasKey = hasPrivateKey() && hasPublicKey()
            val current = preferences.currentTarget(hasKey)
            preferences[HOST_KEY] = hostKey
            if (current != null) {
                preferences[TARGETS] = preferences.targets(hasKey)
                    .map { target ->
                        if (target.id == current.id) target.copy(hostKey = hostKey, hasKey = hasKey) else target
                    }
                    .toJson()
            }
        }
    }

    private suspend fun rememberPendingHostKey(hostKey: VerifiedHostKey) {
        context.connectionDataStore.edit { preferences ->
            preferences[PENDING_HOST_KEY] = hostKey.line
            preferences[PENDING_HOST_FINGERPRINT] = hostKey.fingerprint
        }
    }

    private suspend fun migrateTargetIdentities(): Boolean {
        val hasKey = hasPrivateKey() && hasPublicKey()
        var decodedSuccessfully = false
        context.connectionDataStore.edit { preferences ->
            val rawTargets = preferences[TARGETS].orEmpty()
            when (
                val storageMigration = planConnectionTargetStorageMigration(
                    rawTargets = rawTargets,
                    hasKey = hasKey,
                    legacyTarget = preferences.legacyTarget(hasKey),
                    currentTargetId = preferences[CURRENT_TARGET_ID],
                )
            ) {
                is ConnectionTargetStorageMigration.PreserveUndecodable -> {
                    if (preferences[TARGETS_QUARANTINE] != storageMigration.raw) {
                        preferences[TARGETS_QUARANTINE] = storageMigration.raw
                    }
                    return@edit
                }
                is ConnectionTargetStorageMigration.Ready -> {
                    decodedSuccessfully = true
                    if (preferences[TARGETS] != storageMigration.targetsJson) {
                        preferences[TARGETS] = storageMigration.targetsJson
                    }
                    if (storageMigration.currentTargetId == null) {
                        preferences.remove(CURRENT_TARGET_ID)
                    } else if (preferences[CURRENT_TARGET_ID] != storageMigration.currentTargetId) {
                        preferences[CURRENT_TARGET_ID] = storageMigration.currentTargetId
                    }
                }
            }
        }
        return decodedSuccessfully
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private companion object {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val USER = stringPreferencesKey("user")
        val HOST_KEY = stringPreferencesKey("host_key")
        val PENDING_HOST_KEY = stringPreferencesKey("pending_host_key")
        val PENDING_HOST_FINGERPRINT = stringPreferencesKey("pending_host_fingerprint")
        val TARGETS = stringPreferencesKey("targets")
        val TARGETS_QUARANTINE = stringPreferencesKey("targets_quarantine")
        val CURRENT_TARGET_ID = stringPreferencesKey("current_target_id")
        const val CONNECT_TIMEOUT_MS = 9_000
        const val MAX_PRIVATE_KEY_BYTES = 64 * 1024
        const val MAX_PRIVATE_KEY_PLAINTEXT_BYTES = 32 * 1024
        const val MAX_PUBLIC_KEY_BYTES = 16 * 1024
    }
}
