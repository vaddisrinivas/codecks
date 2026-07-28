package io.codecks.data.reactive.state

import io.codecks.platform.helper.ReactiveHelperClient
import io.codecks.platform.helper.ReactiveHelperClientState
import io.codecks.shared.protocol.ReactiveHelperBasicState
import io.codecks.shared.protocol.ReactiveHelperRequest
import io.codecks.shared.protocol.ReceiptStatus
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
