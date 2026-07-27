package io.codecks.protocol

data class ProtocolActionDefinition(
    val schemaVersion: String,
    val id: String,
    val title: String,
    val platform: String,
    val category: String,
    val description: String,
    val inputs: List<ProtocolActionInput>,
    val effects: List<String>,
    val requiresConfirmation: Boolean,
    val safety: ProtocolActionSafety,
    val implementation: ProtocolActionImplementation,
)

data class ProtocolActionInput(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String? = null,
)

data class ProtocolActionSafety(
    val level: String,
    val notes: String? = null,
)

data class ProtocolActionImplementation(
    val kind: String,
    val command: String? = null,
)

data class ProtocolActionInvocation(
    val schemaVersion: String,
    val invocationId: String,
    val actionId: String,
    val requestedAt: String,
    val source: ProtocolInvocationSource,
    val arguments: Map<String, Any?>,
    val dryRun: Boolean,
)

data class ProtocolInvocationSource(
    val deviceId: String,
    val surface: String,
)

data class ProtocolActionPlan(
    val schemaVersion: String,
    val planId: String,
    val createdAt: String,
    val summary: String,
    val steps: List<ProtocolActionPlanStep>,
)

data class ProtocolActionPlanStep(
    val stepId: String,
    val actionId: String,
    val arguments: Map<String, Any?>,
    val dependsOn: List<String> = emptyList(),
)

data class ProtocolActionReceipt(
    val schemaVersion: String,
    val receiptId: String,
    val invocationId: String,
    val actionId: String,
    val status: ProtocolActionReceiptStatus,
    val startedAt: String,
    val finishedAt: String,
    val message: String? = null,
    val outputs: Map<String, Any?> = emptyMap(),
    val error: ProtocolActionReceiptError? = null,
)

data class ProtocolActionReceiptError(
    val code: String,
    val message: String,
    val retryable: Boolean? = null,
)

enum class ProtocolActionReceiptStatus {
    Accepted,
    Completed,
    Failed,
    Denied,
    Skipped;

    companion object {
        fun decode(raw: String): ProtocolActionReceiptStatus = when (raw) {
            "accepted" -> Accepted
            "completed" -> Completed
            "failed" -> Failed
            "denied" -> Denied
            "skipped" -> Skipped
            else -> throw ProtocolValidationException("Unknown receipt status: $raw")
        }
    }

    fun encode(): String = name.lowercase()
}

class ProtocolValidationException(
    override val message: String,
) : IllegalArgumentException(message)
