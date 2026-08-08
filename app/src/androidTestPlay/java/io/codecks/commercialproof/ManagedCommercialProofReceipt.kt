package io.codecks.commercialproof

import android.app.Instrumentation
import android.os.Bundle
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal enum class ProofStatus {
    PASS,
    FAIL,
    NOT_RUN,
}

internal data class ProofCheck(
    val id: String,
    val status: ProofStatus,
    val evidence: String,
    val violations: List<String> = emptyList(),
)

internal class ManagedCommercialProofReceipt(
    private val instrumentation: Instrumentation,
) {
    private val checks = mutableListOf<ProofCheck>()

    fun add(
        id: String,
        status: ProofStatus,
        evidence: String,
        violations: List<String> = emptyList(),
    ) {
        checks += ProofCheck(id, status, evidence, violations)
    }

    fun failures(): List<ProofCheck> = checks.filter { it.status == ProofStatus.FAIL }

    fun emit(): String {
        val rendered = JSONObject().apply {
            put("schema", "codecks.commercial-managed-proof.v1")
            put("target_package", "app.codecks")
            put(
                "overall",
                when {
                    checks.any { it.status == ProofStatus.FAIL } -> ProofStatus.FAIL.name
                    checks.any { it.status == ProofStatus.NOT_RUN } -> ProofStatus.NOT_RUN.name
                    else -> ProofStatus.PASS.name
                },
            )
            put(
                "checks",
                JSONArray().apply {
                    checks.forEach { check ->
                        put(
                            JSONObject().apply {
                                put("id", check.id)
                                put("status", check.status.name)
                                put("evidence", check.evidence)
                                put("violations", JSONArray(check.violations))
                            },
                        )
                    }
                },
            )
        }.toString()
        instrumentation.sendStatus(
            2,
            Bundle().apply { putString("codecks.commercial.proof.receipt", rendered) },
        )
        val encoded = Base64.encodeToString(rendered.toByteArray(), Base64.NO_WRAP)
        val chunks = encoded.chunked(2_800)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rendered.toByteArray())
            .joinToString("") { "%02x".format(it) }
        System.out.println("CODECKS_COMMERCIAL_PROOF_RECEIPT_META=chunks:${chunks.size};sha256:$digest")
        chunks.forEachIndexed { index, chunk ->
            System.out.println("CODECKS_COMMERCIAL_PROOF_RECEIPT_CHUNK=${index + 1}/${chunks.size}:$chunk")
        }
        return rendered
    }
}

internal fun evidence(label: String, value: String): String {
    val normalized = value.replace(Regex("[\\r\\n\\t]+"), " ").trim()
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "$label bytes=${value.toByteArray().size} sha256=$digest excerpt=${normalized.take(240)}"
}
