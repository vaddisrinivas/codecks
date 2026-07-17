package io.codex.s23deck.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codex.s23deck.core.actions.RawCommandPolicy
import io.codex.s23deck.data.ai.EncryptedApiKeyCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

interface ConnectionRepository {
    val config: Flow<ConnectionConfig>
    suspend fun save(host: String, port: Int, user: String)
    suspend fun generateKey(): Result<String>
    suspend fun publicKey(): String
    suspend fun trustHostKey(): Result<String> =
        Result.failure(UnsupportedOperationException("Host verification is unavailable"))
    suspend fun confirmPendingHostKey(): Result<String> =
        Result.failure(UnsupportedOperationException("Host trust confirmation is unavailable"))
    suspend fun rotateKey(): Result<String> =
        Result.failure(UnsupportedOperationException("SSH key rotation is unavailable"))
    suspend fun resetTrust(): Result<String> =
        Result.failure(UnsupportedOperationException("SSH trust reset is unavailable"))
    suspend fun installKey(password: String): Result<String>
    suspend fun test(password: String? = null): Result<String>
    suspend fun runAction(actionId: String, dangerous: Boolean): Result<String>
    suspend fun runCommand(command: String): Result<String> =
        Result.failure(UnsupportedOperationException("Raw commands are unavailable"))
    suspend fun runCommandRaw(command: String): Result<String> = runCommand(command)
    suspend fun runCommandSecret(command: String): Result<String> =
        Result.failure(UnsupportedOperationException("Secret commands are unavailable"))
    suspend fun savedTargets(): List<ConnectionTarget> = emptyList()
    suspend fun selectTarget(targetId: String): Result<String> =
        Result.failure(UnsupportedOperationException("Target switching is unavailable"))
    suspend fun removeTarget(targetId: String): Result<String> =
        Result.failure(UnsupportedOperationException("Target removal is unavailable"))
    suspend fun runActionOnTarget(targetId: String, actionId: String, dangerous: Boolean): Result<String> =
        runAction(actionId, dangerous)
    suspend fun runCommandOnTarget(targetId: String, command: String): Result<String> = runCommand(command)
}

@Singleton
class DefaultConnectionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConnectionRepository {
    private val keyRevision = MutableStateFlow(0)
    private val privateKeyCodec = EncryptedApiKeyCodec("ssh.private")

    override val config: Flow<ConnectionConfig> = combine(
        context.connectionDataStore.data,
        keyRevision,
    ) { preferences, _ ->
        val hasKey = hasPrivateKey() && publicKeyFile().exists()
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
        context.connectionDataStore.edit { preferences ->
            val endpointChanged = preferences[HOST] != host.trim() ||
                preferences[PORT] != port || preferences[USER] != user.trim()
            val targetId = targetId(host, user, port)
            val currentTargets = preferences.targets(hasKey = hasPrivateKey() && publicKeyFile().exists())
            val previous = currentTargets.firstOrNull { it.id == targetId } ?: currentTargets.firstOrNull {
                it.host == host.trim() && it.user == user.trim() && it.port == port
            }
            val nextTarget = ConnectionTarget(
                id = targetId,
                host = host.trim(),
                port = port,
                user = user.trim(),
                hasKey = hasPrivateKey() && publicKeyFile().exists(),
                hostKey = if (endpointChanged) previous?.hostKey.orEmpty() else preferences[HOST_KEY].orEmpty(),
            )
            preferences[TARGETS] = currentTargets
                .filterNot { it.id == targetId }
                .plus(nextTarget)
                .toJson()
            preferences[CURRENT_TARGET_ID] = targetId
            preferences[HOST] = host.trim()
            preferences[PORT] = port
            preferences[USER] = user.trim()
            if (endpointChanged) {
                preferences.remove(PENDING_HOST_KEY)
                preferences.remove(PENDING_HOST_FINGERPRINT)
                if (previous?.hostKey.isNullOrBlank()) preferences.remove(HOST_KEY)
            }
        }
    }

    override suspend fun generateKey(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasPrivateKey() || !publicKeyFile().exists()) {
                val keyPair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 3072)
                val privateOutput = ByteArrayOutputStream()
                val publicOutput = ByteArrayOutputStream()
                keyPair.writePrivateKey(privateOutput)
                keyPair.writePublicKey(publicOutput, "deckbridge")
                keyPair.dispose()
                writePrivateKey(privateOutput.toString(Charsets.UTF_8.name()))
                publicKeyFile().writeBytes(publicOutput.toByteArray())
            }
            hardenPrivateKeyFile()
            keyRevision.value += 1
            publicKeyFile().readText().trim()
        }
    }

    override suspend fun publicKey(): String = withContext(Dispatchers.IO) {
        publicKeyFile().takeIf { it.exists() }?.readText()?.trim().orEmpty()
    }

    override suspend fun trustHostKey(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            require(current.isConfigured) { "Save the Mac address and username first" }
            val hostKey = fetchHostKey(current)
            rememberPendingHostKey(hostKey)
            "Fingerprint found: ${hostKey.fingerprint}"
        }
    }

    override suspend fun confirmPendingHostKey(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val preferences = context.connectionDataStore.data.first()
            val pendingLine = preferences[PENDING_HOST_KEY].orEmpty()
            val pendingFingerprint = preferences[PENDING_HOST_FINGERPRINT].orEmpty()
            require(pendingLine.isNotBlank()) { "Verify the Mac fingerprint first" }
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
            privateKeyFile().delete()
            legacyPrivateKeyFile().delete()
            publicKeyFile().delete()
            val publicKey = generateKey().getOrThrow()
            hardenPrivateKeyFile()
            "New SSH key ready. Reinstall it on your Mac.\n$publicKey"
        }
    }

    override suspend fun resetTrust(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.connectionDataStore.edit { preferences ->
                val hasKey = hasPrivateKey() && publicKeyFile().exists()
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
        val hasKey = hasPrivateKey() && publicKeyFile().exists()
        return context.connectionDataStore.data.first().targets(hasKey)
    }

    override suspend fun selectTarget(targetId: String): Result<String> = runCatching {
        var selected: ConnectionTarget? = null
        context.connectionDataStore.edit { preferences ->
            val hasKey = hasPrivateKey() && publicKeyFile().exists()
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
        var removed: ConnectionTarget? = null
        context.connectionDataStore.edit { preferences ->
            val hasKey = hasPrivateKey() && publicKeyFile().exists()
            val targets = preferences.targets(hasKey)
            removed = targets.firstOrNull { it.id == targetId } ?: error("Target not found")
            val remaining = targets.filterNot { it.id == targetId }
            preferences[TARGETS] = remaining.toJson()
            val next = remaining.firstOrNull()
            if (preferences[CURRENT_TARGET_ID] == targetId || next == null) {
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
        Result.failure(UnsupportedOperationException("Legacy catalog runner removed; use an inline reviewed command"))

    override suspend fun runActionOnTarget(targetId: String, actionId: String, dangerous: Boolean): Result<String> =
        Result.failure(UnsupportedOperationException("Legacy catalog runner removed; use an inline reviewed command"))

    override suspend fun runCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            require(current.isReady) { "Connect your Mac first" }
            require(command.isNotBlank()) { "Command is empty" }
            RawCommandPolicy.requireSafeTemplate(command)
            val result = runSsh(current, null, readPrivateKey(), command)
            check(result.isSuccess) { result.summary }
            result.summary
        }
    }

    override suspend fun runCommandRaw(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
            require(current.isReady) { "Connect your Mac first" }
            require(command.isNotBlank()) { "Command is empty" }
            RawCommandPolicy.requireSafeTemplate(command)
            val result = runSsh(current, null, readPrivateKey(), command)
            check(result.isSuccess) { result.summary }
            result.stdout.trim()
        }
    }

    override suspend fun runCommandSecret(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentConfig()
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
                require(target.isReady) { "Connect ${target.host} first" }
                require(command.isNotBlank()) { "Command is empty" }
                RawCommandPolicy.requireAllowed(command)
                val result = runSsh(target.toConfig(), null, readPrivateKey(), command)
                check(result.isSuccess) { result.summary }
                result.summary
            }
        }

    private suspend fun currentConfig(): ConnectionConfig = config.first()

    private suspend fun targetById(targetId: String): ConnectionTarget {
        val hasKey = hasPrivateKey() && publicKeyFile().exists()
        return context.connectionDataStore.data.first().targets(hasKey)
            .firstOrNull { it.id == targetId }
            ?: error("Target not found")
    }

    private fun runSsh(
        config: ConnectionConfig,
        password: String?,
        privateKey: String?,
        command: String,
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
                jsch.addIdentity("DeckBridge", privateKey.toByteArray(), null, null)
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
    private fun publicKeyFile() = context.filesDir.resolve("deckbridge_ssh_public")

    private fun hasPrivateKey(): Boolean = privateKeyFile().exists() || legacyPrivateKeyFile().exists()

    private fun writePrivateKey(privateKey: String) {
        privateKeyFile().writeText(privateKeyCodec.encrypt(privateKey))
        legacyPrivateKeyFile().delete()
        hardenPrivateKeyFile()
    }

    private fun readPrivateKeyOrNull(): String? {
        if (privateKeyFile().exists()) {
            return privateKeyCodec.decrypt(privateKeyFile().readText())
        }
        val legacy = legacyPrivateKeyFile().takeIf { it.exists() }?.readText()?.takeIf(String::isNotBlank)
            ?: return null
        writePrivateKey(legacy)
        return legacy
    }

    private fun readPrivateKey(): String =
        requireNotNull(readPrivateKeyOrNull()) { "Generate or install the SSH key first" }

    private fun hardenPrivateKeyFile() {
        privateKeyFile().takeIf { it.exists() }?.apply {
            setReadable(false, false)
            setWritable(false, false)
            setExecutable(false, false)
            setReadable(true, true)
            setWritable(true, true)
        }
    }

    private suspend fun rememberHostKey(hostKey: String) {
        if (hostKey.isBlank()) return
        context.connectionDataStore.edit { preferences ->
            val hasKey = hasPrivateKey() && publicKeyFile().exists()
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

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private companion object {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val USER = stringPreferencesKey("user")
        val HOST_KEY = stringPreferencesKey("host_key")
        val PENDING_HOST_KEY = stringPreferencesKey("pending_host_key")
        val PENDING_HOST_FINGERPRINT = stringPreferencesKey("pending_host_fingerprint")
        val TARGETS = stringPreferencesKey("targets")
        val CURRENT_TARGET_ID = stringPreferencesKey("current_target_id")
        const val CONNECT_TIMEOUT_MS = 9_000
    }
}

private fun ByteArrayOutputStream.writeBounded(buffer: ByteArray, count: Int) {
    if (size() >= SSH_OUTPUT_LIMIT_BYTES) return
    val allowed = (SSH_OUTPUT_LIMIT_BYTES - size()).coerceAtMost(count)
    if (allowed > 0) write(buffer, 0, allowed)
}

private fun InputStream.readAvailableBounded(output: ByteArrayOutputStream, buffer: ByteArray) {
    while (available() > 0) {
        val count = read(buffer)
        if (count < 0) break
        output.writeBounded(buffer, count)
    }
}

private data class VerifiedHostKey(
    val line: String,
    val fingerprint: String,
)

private fun Preferences.targets(hasKey: Boolean): List<ConnectionTarget> {
    val stored = this[ConnectionPreferenceKeys.TARGETS]
        .orEmpty()
        .toConnectionTargets(hasKey)
    val legacy = legacyTarget(hasKey)
    return (stored + listOfNotNull(legacy))
        .distinctBy(ConnectionTarget::id)
        .sortedWith(compareByDescending<ConnectionTarget> { it.id == this[ConnectionPreferenceKeys.CURRENT_TARGET_ID] }.thenBy { it.host })
}

private fun Preferences.currentTarget(hasKey: Boolean): ConnectionTarget? {
    val targets = targets(hasKey)
    val currentId = this[ConnectionPreferenceKeys.CURRENT_TARGET_ID]
    return targets.firstOrNull { it.id == currentId } ?: targets.firstOrNull()
}

private fun Preferences.legacyTarget(hasKey: Boolean): ConnectionTarget? {
    val host = this[ConnectionPreferenceKeys.HOST].orEmpty()
    val user = this[ConnectionPreferenceKeys.USER].orEmpty()
    val port = this[ConnectionPreferenceKeys.PORT] ?: 22
    if (host.isBlank() || user.isBlank()) return null
    return ConnectionTarget(
        id = targetId(host, user, port),
        host = host,
        port = port,
        user = user,
        hasKey = hasKey,
        hostKey = this[ConnectionPreferenceKeys.HOST_KEY].orEmpty(),
    )
}

private fun String.toConnectionTargets(hasKey: Boolean): List<ConnectionTarget> =
    runCatching {
        val array = JSONArray(this)
        buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val host = item.optString("host")
                val user = item.optString("user")
                if (host.isBlank() || user.isBlank()) return@repeat
                val port = item.optInt("port", 22).coerceIn(1, 65535)
                add(
                    ConnectionTarget(
                        id = item.optString("id").ifBlank { targetId(host, user, port) },
                        host = host,
                        port = port,
                        user = user,
                        hasKey = hasKey,
                        hostKey = item.optString("hostKey"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

private fun List<ConnectionTarget>.toJson(): String {
    val array = JSONArray()
    forEach { target ->
        array.put(
            JSONObject()
                .put("id", target.id)
                .put("host", target.host)
                .put("port", target.port)
                .put("user", target.user)
                .put("hostKey", target.hostKey),
        )
    }
    return array.toString()
}

private fun targetId(host: String, user: String, port: Int = 22): String =
    "mac_${user}_${host}_${port}"
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "mac_current" }

private object ConnectionPreferenceKeys {
    val HOST = stringPreferencesKey("host")
    val PORT = intPreferencesKey("port")
    val USER = stringPreferencesKey("user")
    val HOST_KEY = stringPreferencesKey("host_key")
    val TARGETS = stringPreferencesKey("targets")
    val CURRENT_TARGET_ID = stringPreferencesKey("current_target_id")
}
