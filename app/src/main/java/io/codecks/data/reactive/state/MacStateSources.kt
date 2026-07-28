package io.codecks.data.reactive.state

import io.codecks.data.ConnectionRepository
import io.codecks.platform.helper.ReactiveHelperClient
import io.codecks.platform.helper.ReactiveHelperClientState
import io.codecks.shared.protocol.ReactiveCapabilityId
import io.codecks.shared.protocol.ReactiveHelperBasicState
import io.codecks.shared.protocol.ReactiveHelperRequest
import io.codecks.shared.protocol.ReceiptStatus
import io.codecks.shared.protocol.StateProvenance
import io.codecks.shared.protocol.validateBasicState
import kotlinx.serialization.json.Json

fun interface SshMacStateSource {
    suspend fun refreshBasicState(macId: String): ReactiveHelperBasicState?
}

interface HelperMacStateSource {
    val connected: Boolean
    suspend fun refreshBasicState(deadlineMillis: Long): ReactiveHelperBasicState
}

class ReactiveHelperClientMacStateSource(
    private val client: ReactiveHelperClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        encodeDefaults = true
    },
) : HelperMacStateSource {
    override val connected: Boolean
        get() = client.state.value is ReactiveHelperClientState.Connected

    override suspend fun refreshBasicState(deadlineMillis: Long): ReactiveHelperBasicState {
        val response = client.request(ReactiveHelperRequest.BasicState, deadlineMillis)
        check(response.status == ReceiptStatus.Completed) {
            response.code ?: "helper_basic_state_${response.status.name.lowercase()}"
        }
        val body = requireNotNull(response.bodyJson) { "helper_basic_state_empty" }
        return json.decodeFromString(ReactiveHelperBasicState.serializer(), body).also {
            validateBasicState(it, nowMillis())
        }
    }
}

class ConnectionRepositorySshMacStateSource(
    private val connectionRepository: ConnectionRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SshMacStateSource {
    override suspend fun refreshBasicState(macId: String): ReactiveHelperBasicState? {
        val output = connectionRepository.runBundledCommandOnTarget(macId, FRONT_APP_COMMAND)
            .getOrElse { return null }
        return output.toSshBasicState(macId, nowMillis())
    }

    private companion object {
        val FRONT_APP_COMMAND = listOf(
            "/usr/bin/osascript",
            "-l",
            "JavaScript",
            "-e",
            "'const se=Application(\"System Events\"); const p=se.applicationProcesses.whose({frontmost:true})[0]; [p.bundleIdentifier(),p.name()].join(\"\\n\")'",
        ).joinToString(" ")
    }
}

internal fun String.toSshBasicState(macId: String, capturedAtMillis: Long): ReactiveHelperBasicState? {
    val lines = lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .take(3)
        .toList()
    val bundleId = lines.getOrNull(0)?.takeIf { it.isSafeBundleId() } ?: return null
    val rawAppName = lines.getOrNull(1)
    if (rawAppName != null && !rawAppName.isSafeAppName()) return null
    val appName = rawAppName ?: bundleId.substringAfterLast('.')
    return ReactiveHelperBasicState(
        macId = macId,
        snapshotRevision = capturedAtMillis,
        capturedAtMillis = capturedAtMillis,
        freshnessMillis = 3_000L,
        provenance = StateProvenance.Ssh,
        frontAppBundleId = bundleId,
        frontAppName = appName,
        capabilities = setOf(
            ReactiveCapabilityId.FrontAppState,
            ReactiveCapabilityId.ActionExecute,
            ReactiveCapabilityId.SpotlightSearch,
            ReactiveCapabilityId.TransferSftp,
        ),
    )
}

private fun String.isSafeBundleId(): Boolean =
    length in 3..128 &&
        matches(Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*$")) &&
        !contains("..")

private fun String.isSafeAppName(): Boolean =
    isNotBlank() &&
        length <= 96 &&
        none { it.code < 32 || it == '\u007f' }
