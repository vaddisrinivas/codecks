package io.codecks.ui.connection

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.domain.connection.MacCapabilityCheckReceipt
import io.codecks.domain.connection.MacCapabilityCheckResult
import io.codecks.domain.connection.MacSetupCapability
import io.codecks.domain.connection.SshSetupFailureCode
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SetupTerminalProof(
    val sshReceipt: SshTerminalReceipt?,
    val capabilityReceipts: List<MacCapabilityCheckReceipt>,
)

@Singleton
class SetupTerminalProofStore private constructor(
    private val preferences: SharedPreferences?,
) {
    private var inMemoryEncoded: String? = null
    private val writeMutex = Mutex()

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    @Synchronized
    fun load(): SetupTerminalProof = decode(
        preferences?.getString(PROOF_KEY, null) ?: inMemoryEncoded,
    )

    suspend fun record(
        sshReceipt: SshTerminalReceipt,
        capabilityReceipts: List<MacCapabilityCheckReceipt>,
    ) {
        val encoded = encode(SetupTerminalProof(sshReceipt, capabilityReceipts))
        writeMutex.withLock {
            if (preferences == null) {
                synchronized(this@SetupTerminalProofStore) {
                    inMemoryEncoded = encoded
                }
            } else {
                withContext(Dispatchers.IO) {
                    synchronized(this@SetupTerminalProofStore) {
                        check(preferences.edit().putString(PROOF_KEY, encoded).commit()) {
                            "Could not durably save setup verification"
                        }
                    }
                }
            }
        }
    }

    suspend fun clear() {
        writeMutex.withLock {
            if (preferences == null) {
                synchronized(this@SetupTerminalProofStore) {
                    inMemoryEncoded = null
                }
            } else {
                withContext(Dispatchers.IO) {
                    synchronized(this@SetupTerminalProofStore) {
                        check(preferences.edit().remove(PROOF_KEY).commit()) {
                            "Could not durably clear setup verification"
                        }
                    }
                }
            }
        }
    }

    private fun encode(proof: SetupTerminalProof): String {
        val receipt = requireNotNull(proof.sshReceipt)
        return JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("revision", receipt.setupRevision)
            .put("target", receipt.macTargetId)
            .put("result", receipt.result.name)
            .put("attempt", receipt.attempt)
            .put("duration", receipt.durationMs)
            .put("completed", receipt.completedAtEpochMs)
            .put(
                "capabilities",
                JSONArray(proof.capabilityReceipts.map { capability ->
                    JSONObject()
                        .put("capability", capability.capability.persistedCode)
                        .put("result", capability.result.persistedCode)
                        .put("failure", capability.failureCode?.persistedCode)
                        .put("checked", capability.checkedAtEpochMs)
                }),
            )
            .toString()
    }

    private fun decode(encoded: String?): SetupTerminalProof {
        if (encoded.isNullOrBlank()) return SetupTerminalProof(null, emptyList())
        return runCatching {
            val json = JSONObject(encoded)
            require(json.getInt("schema") == SCHEMA_VERSION)
            val items = json.getJSONArray("capabilities")
            val capabilities = buildList {
                repeat(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    val capability = MacSetupCapability.entries.single {
                        it.persistedCode == item.getString("capability")
                    }
                    val result = MacCapabilityCheckResult.entries.single {
                        it.persistedCode == item.getString("result")
                    }
                    val failure = item.optString("failure").takeIf(String::isNotBlank)?.let { code ->
                        SshSetupFailureCode.entries.single { it.persistedCode == code }
                    }
                    add(
                        MacCapabilityCheckReceipt(
                            capability = capability,
                            result = result,
                            failureCode = failure,
                            checkedAtEpochMs = item.getLong("checked"),
                        ),
                    )
                }
            }
            SetupTerminalProof(
                sshReceipt = SshTerminalReceipt(
                    setupRevision = json.getString("revision"),
                    macTargetId = json.getString("target"),
                    result = SshTerminalResult.valueOf(json.getString("result")),
                    attempt = json.getInt("attempt"),
                    durationMs = json.getLong("duration"),
                    completedAtEpochMs = json.getLong("completed"),
                    confirmedCapabilities = capabilities
                        .filter { it.result == MacCapabilityCheckResult.Passed }
                        .mapTo(linkedSetOf()) { it.capability },
                    capabilityCheckedAtEpochMs = capabilities.associate {
                        it.capability to it.checkedAtEpochMs
                    },
                ),
                capabilityReceipts = capabilities,
            )
        }.getOrElse { SetupTerminalProof(null, emptyList()) }
    }

    companion object {
        private const val PREFERENCES_NAME = "codecks_settings"
        private const val PROOF_KEY = "setup_terminal_proof_v1"
        private const val SCHEMA_VERSION = 1

        internal fun inMemory(): SetupTerminalProofStore =
            SetupTerminalProofStore(preferences = null)
    }
}
