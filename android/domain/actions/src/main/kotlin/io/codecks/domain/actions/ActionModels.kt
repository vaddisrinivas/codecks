package io.codecks.domain.actions

import io.codecks.domain.targets.TargetCapability
import io.codecks.domain.targets.TargetSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class ExecutorKind {
    LOCAL_ANDROID,
    SSH_MAC,
    HID,
    AUTOMATION,
}

@Serializable
enum class SafetyClass {
    SAFE,
    CONFIRM,
    DANGEROUS,
    DEVELOPER_ONLY,
}

@Serializable
data class TimeoutPolicy(
    val runTimeoutMillis: Long = 30_000,
    val outputLimitBytes: Int = 64 * 1024,
)

@Serializable
data class ActionInput(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val redacted: Boolean = false,
)

@Serializable
data class ActionDefinition(
    val stableId: String,
    val version: Int,
    val title: String,
    val category: String,
    val inputs: List<ActionInput> = emptyList(),
    val capabilities: Set<TargetCapability>,
    val safetyClass: SafetyClass,
    val executorKind: ExecutorKind,
    val timeoutPolicy: TimeoutPolicy = TimeoutPolicy(),
    val receiptSchema: String = "io.codecks.receipt.v1",
)

@Serializable
data class ActionInvocation(
    val invocationId: String,
    val actionId: String,
    val parameters: Map<String, String> = emptyMap(),
    val targetSelection: TargetSelection,
    val origin: String,
    val requestedAtEpochMillis: Long,
)

@Serializable
data class PlannedStep(
    val stableId: String,
    val executorKind: ExecutorKind,
    val safeSummary: String,
    val timeoutPolicy: TimeoutPolicy,
)

@Serializable
data class ConfirmationRequest(
    val reason: String,
    val expiresAtEpochMillis: Long,
)

@Serializable
data class ActionPlan(
    val invocationId: String,
    val resolvedTargetId: String,
    val steps: List<PlannedStep>,
    val confirmations: List<ConfirmationRequest> = emptyList(),
    val redactions: List<String> = emptyList(),
    val expectedCapabilities: Set<TargetCapability>,
)

@Serializable
enum class ReceiptState {
    RUNNING,
    SUCCESS,
    FAILURE,
    CANCELLED,
    NEEDS_CONFIRMATION,
    UNAVAILABLE,
}

@Serializable
data class RepairHint(
    val title: String,
    val actionLabel: String? = null,
)

@Serializable
data class ActionReceipt(
    val invocationId: String,
    val state: ReceiptState,
    val targetId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val safeSummary: String,
    val repair: RepairHint? = null,
)

interface ActionReceiptRepository {
    fun observeReceipts(): Flow<List<ActionReceipt>>
    suspend fun append(receipt: ActionReceipt)
    suspend fun clear()
}
