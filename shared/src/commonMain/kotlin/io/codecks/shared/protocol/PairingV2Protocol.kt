package io.codecks.shared.protocol

const val PAIRING_V2_SCHEMA = "codecks.pairing.v2"
const val PAIRING_V2_LIFETIME_MILLIS = 120_000L

data class PairingV2TranscriptFields(
    val offerId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val macId: String,
    val displayName: String,
    val helperId: String,
    val publicKeyFingerprint: String,
    val host: String?,
    val port: Int?,
    val offerNonce: String,
    val deviceId: String,
    val deviceNonce: String,
)

fun pairingV2Canonical(vararg fields: String): String =
    fields.joinToString("|") { "${it.encodeToByteArray().size}:$it" }

fun isCanonicalPairingV2EntropyToken(value: String): Boolean =
    value.length == 22 &&
        value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' } &&
        value.last() in setOf('A', 'Q', 'g', 'w')

fun pairingV2Transcript(value: PairingV2TranscriptFields): String = pairingV2Canonical(
    PAIRING_V2_SCHEMA,
    value.offerId,
    value.issuedAtMillis.toString(),
    value.expiresAtMillis.toString(),
    value.macId,
    value.displayName,
    value.helperId,
    value.publicKeyFingerprint,
    value.host.orEmpty(),
    value.port?.toString().orEmpty(),
    value.offerNonce,
    value.deviceId,
    value.deviceNonce,
)
