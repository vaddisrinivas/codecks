package io.codecks.data.reactive.helper

import io.codecks.platform.helper.ReactiveHelperEndpoint
import io.codecks.platform.helper.ReactiveHelperTransport
import io.codecks.platform.helper.ReactiveHelperTransportFactory
import io.codecks.shared.protocol.ReactiveFrameCodec
import org.json.JSONObject

class ReactiveHelperPairingV2Client(private val transportFactory: ReactiveHelperTransportFactory) {
    suspend fun begin(pending: PendingReactiveHelperPairing) {
        val transport = transportFactory.connect(pending.offer.endpoint())
        try {
            val response = exchange(transport, JSONObject().put("pairingType", "pairing_begin")
                .put("offerId", pending.offer.offerId).put("deviceId", pending.deviceId)
                .put("deviceNonce", pending.deviceNonce).put("proof", pending.beginProof))
            require(response.getString("offerId") == pending.offer.offerId && response.getString("deviceId") == pending.deviceId)
            require(response.getString("matchingCode") == pending.matchingCode) { "Pairing code mismatch" }
            require(response.getLong("expiresAtMillis") == pending.offer.expiresAtMillis) { "Pairing expiry mismatch" }
        } finally { transport.close() }
    }

    suspend fun finish(pending: PendingReactiveHelperPairing): PairingV2ServerAcceptance {
        val transport = transportFactory.connect(pending.offer.endpoint())
        return try {
            val response = exchange(transport, JSONObject().put("pairingType", "pairing_finish")
                .put("offerId", pending.offer.offerId).put("phoneProof", pending.confirmationProof))
            PairingV2ServerAcceptance(response.getString("offerId"), response.getString("deviceId"), response.getBoolean("accepted"), response.getString("serverProof"))
        } finally { transport.close() }
    }

    private suspend fun exchange(transport: ReactiveHelperTransport, value: JSONObject): JSONObject {
        val payload = value.toString().encodeToByteArray()
        require(payload.size <= 4 * 1024)
        val response = ReactiveFrameCodec.decode(transport.exchange(ReactiveFrameCodec.encode(payload)))
        require(response.size <= 4 * 1024)
        return JSONObject(response.decodeToString())
    }

    private fun ReactiveHelperPairingV2Offer.endpoint() = ReactiveHelperEndpoint(
        requireNotNull(host) { "Pairing host missing" }, requireNotNull(port) { "Pairing port missing" },
    )
}
