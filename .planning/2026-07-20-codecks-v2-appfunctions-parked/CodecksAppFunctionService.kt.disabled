package io.codecks.app

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import io.codecks.app.data.CodecksDataContainer
import io.codecks.core.common.SystemCodecksClock
import io.codecks.domain.actions.ActionInvocation
import io.codecks.domain.actions.ActionPlan
import io.codecks.domain.actions.CoreActionCatalog
import io.codecks.domain.actions.ExecutorKind
import io.codecks.domain.decks.Deck
import io.codecks.domain.decks.DeckButton
import io.codecks.domain.decks.DefaultDeckFactory
import io.codecks.domain.targets.MacTarget
import io.codecks.runtime.actions.ActionPlanner
import io.codecks.transport.ssh.SshMacActionExecutor
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * AppFunctions entrypoint for command deck operations.
 *
 * The exposed functions are intentionally approval-first. Preview and queue calls are
 * read-only, while execution happens through `runApprovedDeckCommand` and only proceeds
 * when the caller presents the queue token for confirmation-required actions.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@AppFunctionServiceEntryPoint(
    serviceName = "CodecksAppFunctionService",
    appFunctionXmlFileName = "codecks_appfunctions",
)
abstract class BaseCodecksAppFunctionService : AppFunctionService() {
    private val clock = SystemCodecksClock
    private val actionPlanner = ActionPlanner()
    private val dataContainer by lazy { CodecksDataContainer.get(applicationContext) }
    private val actionExecutor by lazy { SshMacActionExecutor(dataContainer.sshCredentialProvider) }
    private val queuedCommands = ConcurrentHashMap<String, QueuedDeckCommand>()

    private val defaultDeck by lazy { DefaultDeckFactory.mainDeck() }
    private val approvalWindowMillis = 60_000L

    /**
     * listCommandDecks()
     *
     * Returns every known deck and command button metadata that Codecks can potentially
     * expose through AppFunctions.
     *
     * @return list of deck summaries for UI/agent discovery.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listCommandDecks(): List<CommandDeckSummary> {
        val decks = dataContainer.deckRepository.observeDecks().first()
        val mergedDecks = if (decks.none { it.id == defaultDeck.id }) decks + defaultDeck else decks
        return mergedDecks.map { deck ->
            val buttons = deck.pages.flatMap { it.buttons }.map { button ->
                resolveDeckButtonSummary(button)
            }
            CommandDeckSummary(
                deckId = deck.id,
                deckName = deck.name,
                styleRef = deck.styleRef,
                version = deck.version,
                buttonCount = buttons.size,
                buttons = buttons,
            )
        }
    }

    /**
     * previewDeckCommand(deckId, buttonId)
     *
     * Produces the resolved action plan and confirmation requirements for a deck button.
     * This call is read-only and does not execute any command.
     *
     * @param deckId The deck logical identifier (for example, "deck-main").
     * @param buttonId Button id inside the deck (for example, "button-lock").
     * @return A preview containing action, target, and confirmation metadata.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun previewDeckCommand(
        deckId: String,
        buttonId: String,
    ): DeckCommandPreview {
        val command = resolveDeckCommand(deckId, buttonId)
        val firstConfirmation = command.plan.confirmations.firstOrNull()
        return DeckCommandPreview(
            deckId = command.deck.id,
            deckName = command.deck.name,
            buttonId = command.button.id,
            actionId = command.definition.stableId,
            actionTitle = command.definition.title,
            actionCategory = command.definition.category,
            targetSelection = command.button.targetRef.toDisplayLabel(),
            targetId = command.plan.resolvedTargetId,
            parameters = command.parametersAsList(),
            executorKind = command.definition.executorKind.name,
            requiresConfirmation = command.plan.confirmations.isNotEmpty(),
            confirmationReason = firstConfirmation?.reason,
            estimatedSafeSummary = command.plan.steps.singleOrNull()?.safeSummary ?: command.definition.title,
            estimatedCapabilities = command.definition.capabilities.map { it.name },
            confirmationWindowExpiresAtEpochMillis = firstConfirmation?.expiresAtEpochMillis,
        )
    }

    /**
     * queueDeckCommand(deckId, buttonId)
     *
     * Creates a queued execution entry for a button in a given deck.
     * Confirmation-required commands return an approval token; safe commands are
     * queued for direct approval-free execution.
     *
     * @param deckId The deck logical identifier.
     * @param buttonId Button id inside the deck.
     * @return Queue metadata including the `queueId` and optional `approvalToken`.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun queueDeckCommand(
        deckId: String,
        buttonId: String,
    ): QueuedDeckCommandHandle {
        val command = resolveDeckCommand(deckId, buttonId)
        val now = clock.nowEpochMillis()
        val requiresApproval = command.plan.confirmations.isNotEmpty()
        val expiry = if (requiresApproval) {
            command.plan.confirmations.firstOrNull()?.expiresAtEpochMillis
                ?: (now + approvalWindowMillis)
        } else {
            null
        }
        val queueId = UUID.randomUUID().toString()
        val approvalToken = if (requiresApproval) UUID.randomUUID().toString().replace("-", "").take(12) else null
        queuedCommands[queueId] = command.copy(
            queueId = queueId,
            queuedAtEpochMillis = now,
            requiresApproval = requiresApproval,
            approvalToken = approvalToken,
            approvalExpiresAtEpochMillis = expiry,
        )

        return QueuedDeckCommandHandle(
            queueId = queueId,
            deckId = command.deck.id,
            buttonId = command.button.id,
            actionId = command.definition.stableId,
            actionTitle = command.definition.title,
            requiresApproval = requiresApproval,
            approvalToken = approvalToken,
            queuedAtEpochMillis = now,
            approvalExpiresAtEpochMillis = expiry,
            safeSummary = command.plan.steps.singleOrNull()?.safeSummary ?: command.definition.title,
            requiresManualApproval = requiresApproval,
            instructions = if (requiresApproval) {
                "Execute `runApprovedDeckCommand` with queueId=$queueId and matching approvalToken before approval window expires."
            } else {
                "This command is approval-free and can run through runApprovedDeckCommand."
            },
        )
    }

    /**
     * runApprovedDeckCommand(queueId, approvalToken)
     *
     * Executes one queued command. If the queued command requires approval,
     * supply the matching `approvalToken` returned by queueDeckCommand.
     *
     * @param queueId Identifier produced by queueDeckCommand.
     * @param approvalToken Optional token only required for confirmation-gated commands.
     * @return Execution result and safety state.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun runApprovedDeckCommand(
        queueId: String,
        approvalToken: String? = null,
    ): CommandExecutionResult {
        val queued = queuedCommands[queueId] ?: throw IllegalArgumentException(
            "Unknown queueId: $queueId. Use queueDeckCommand first.",
        )
        val now = clock.nowEpochMillis()

        if (queued.requiresApproval && queued.approvalToken == null) {
            queuedCommands.remove(queueId)
            throw IllegalArgumentException("Queue command is missing an approval token configuration.")
        }
        if (queued.requiresApproval && queued.approvalToken != approvalToken) {
            queuedCommands.remove(queueId)
            throw IllegalArgumentException(
                "Missing or invalid approvalToken for queueId=$queueId. Call queueDeckCommand and supply that token.",
            )
        }
        val expiresAt = queued.approvalExpiresAtEpochMillis
        if (expiresAt != null && now > expiresAt) {
            queuedCommands.remove(queueId)
            throw IllegalArgumentException(
                "Queue item $queueId expired at $expiresAt. Re-queue before running.",
            )
        }
        if (queued.requiresApproval && dataContainer.currentTarget?.logicalId != queued.target.logicalId) {
            queuedCommands.remove(queueId)
            throw IllegalArgumentException(
                "Current target changed since queueing. Recreate this queue after setting the desired current target.",
            )
        }

        return try {
            val executionStartedAt = clock.nowEpochMillis()
            val receipt = actionExecutor.execute(queued.plan, queued.target, executionStartedAt)
            dataContainer.receiptRepository.append(receipt)
            CommandExecutionResult.fromReceipt(receipt, queued.queueId)
        } finally {
            queuedCommands.remove(queueId)
        }
    }

    private suspend fun resolveDeckCommand(deckId: String, buttonId: String): QueuedDeckCommand {
        val deck = findDeck(deckId)
        val button = deck.pages.flatMap { it.buttons }.firstOrNull { it.id == buttonId }
            ?: throw IllegalArgumentException("Button $buttonId was not found in deck $deckId.")

        val definition = runCatching { CoreActionCatalog.requireDefinition(button.actionRef.stableId) }
            .getOrElse {
                throw IllegalArgumentException("Unknown action ${button.actionRef.stableId}.")
            }
        if (definition.executorKind != ExecutorKind.SSH_MAC) {
            throw IllegalArgumentException("Action ${button.actionRef.stableId} is not supported via AppFunction execution.")
        }
        val currentTarget = dataContainer.currentTarget
        val planned = createPlan(
            deck = deck,
            button = button,
            definition = definition,
            invocationId = "codecks-${UUID.randomUUID()}",
            target = currentTarget,
        )
        return QueuedDeckCommand(
            queueId = "",
            deck = deck,
            button = button,
            definition = definition,
            invocation = planned.invocation,
            plan = planned.plan,
            target = planned.target,
            queuedAtEpochMillis = clock.nowEpochMillis(),
            requiresApproval = planned.plan.confirmations.isNotEmpty(),
            approvalToken = null,
            approvalExpiresAtEpochMillis = planned.plan.confirmations.firstOrNull()?.expiresAtEpochMillis,
        )
    }

    private suspend fun findDeck(deckId: String): Deck {
        val decks = dataContainer.deckRepository.observeDecks().first()
        return decks.firstOrNull { it.id == deckId } ?: if (defaultDeck.id == deckId) {
            defaultDeck
        } else {
            throw IllegalArgumentException("Deck $deckId not found.")
        }
    }

    private fun createPlan(
        deck: Deck,
        button: DeckButton,
        definition: io.codecks.domain.actions.ActionDefinition,
        invocationId: String,
        target: MacTarget?,
    ): PlannedDeckCommand {
        val invocation = ActionInvocation(
            invocationId = invocationId,
            actionId = definition.stableId,
            parameters = button.actionRef.parameters,
            targetSelection = button.targetRef,
            origin = "appfunction",
            requestedAtEpochMillis = clock.nowEpochMillis(),
        )
        val savedTargets = listOfNotNull(target)
        val plan = actionPlanner.plan(
            definition = definition,
            invocation = invocation,
            currentTarget = target,
            savedTargets = savedTargets,
        )
        val resolvedTarget = savedTargets.firstOrNull { it.logicalId == plan.resolvedTargetId }
            ?: throw IllegalArgumentException("Planned target ${plan.resolvedTargetId} is not available.")
        return PlannedDeckCommand(
            deck = deck,
            button = button,
            definition = definition,
            invocation = invocation,
            plan = plan,
            target = resolvedTarget,
        )
    }

    private fun resolveDeckButtonSummary(button: DeckButton): CommandDeckButtonSummary {
        val definition = runCatching { CoreActionCatalog.requireDefinition(button.actionRef.stableId) }.getOrNull()
        val supported = definition?.executorKind == ExecutorKind.SSH_MAC
        val requiresConfirmation = definition?.let { it.safetyClass != io.codecks.domain.actions.SafetyClass.SAFE } == true
        return CommandDeckButtonSummary(
            buttonId = button.id,
            actionId = button.actionRef.stableId,
            title = button.presentation.label,
            glyph = button.presentation.glyph,
            targetSelection = button.targetRef.toDisplayLabel(),
            supportedByAppFunctions = definition != null && supported,
            requiresConfirmation = requiresConfirmation,
            parameters = button.actionRef.parameters.entries
                .sortedBy { it.key }
                .map { CommandParameter(name = it.key, value = it.value) },
            note = definition?.run { "$category / $safetyClass / ${executorKind.name}" },
        )
    }

    private fun QueuedDeckCommand.parametersAsList(): List<CommandParameter> =
        invocation.parameters.entries
            .sortedBy { it.key }
            .map { CommandParameter(name = it.key, value = it.value) }

    private fun io.codecks.domain.targets.TargetSelection.toDisplayLabel(): String = when (this) {
        is io.codecks.domain.targets.TargetSelection.Current -> "current"
        is io.codecks.domain.targets.TargetSelection.Specific -> "specific:${logicalId}"
        is io.codecks.domain.targets.TargetSelection.Group -> "group:${groupId}"
        io.codecks.domain.targets.TargetSelection.All -> "all"
        io.codecks.domain.targets.TargetSelection.Ask -> "ask"
    }
}

private data class PlannedDeckCommand(
    val deck: Deck,
    val button: DeckButton,
    val definition: io.codecks.domain.actions.ActionDefinition,
    val invocation: ActionInvocation,
    val plan: ActionPlan,
    val target: MacTarget,
)

private data class QueuedDeckCommand(
    val queueId: String,
    val deck: Deck,
    val button: DeckButton,
    val definition: io.codecks.domain.actions.ActionDefinition,
    val invocation: ActionInvocation,
    val plan: ActionPlan,
    val target: MacTarget,
    val queuedAtEpochMillis: Long,
    val requiresApproval: Boolean,
    val approvalToken: String?,
    val approvalExpiresAtEpochMillis: Long?,
)

@AppFunctionSerializable
data class CommandDeckSummary(
    val deckId: String,
    val deckName: String,
    val styleRef: String,
    val version: Int,
    val buttonCount: Int,
    val buttons: List<CommandDeckButtonSummary>,
)

@AppFunctionSerializable
data class CommandDeckButtonSummary(
    val buttonId: String,
    val actionId: String,
    val title: String,
    val glyph: String,
    val targetSelection: String,
    val supportedByAppFunctions: Boolean,
    val requiresConfirmation: Boolean,
    val parameters: List<CommandParameter>,
    val note: String?,
)

@AppFunctionSerializable
data class CommandParameter(
    val name: String,
    val value: String,
)

@AppFunctionSerializable
data class DeckCommandPreview(
    val deckId: String,
    val deckName: String,
    val buttonId: String,
    val actionId: String,
    val actionTitle: String,
    val actionCategory: String,
    val targetSelection: String,
    val targetId: String,
    val parameters: List<CommandParameter>,
    val executorKind: String,
    val requiresConfirmation: Boolean,
    val confirmationReason: String?,
    val estimatedSafeSummary: String,
    val estimatedCapabilities: List<String>,
    val confirmationWindowExpiresAtEpochMillis: Long?,
)

@AppFunctionSerializable
data class QueuedDeckCommandHandle(
    val queueId: String,
    val deckId: String,
    val buttonId: String,
    val actionId: String,
    val actionTitle: String,
    val requiresApproval: Boolean,
    val approvalToken: String?,
    val queuedAtEpochMillis: Long,
    val approvalExpiresAtEpochMillis: Long?,
    val safeSummary: String,
    val requiresManualApproval: Boolean,
    val instructions: String,
)

@AppFunctionSerializable
data class CommandExecutionResult(
    val queueId: String,
    val invocationId: String,
    val targetId: String,
    val state: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val safeSummary: String,
    val repairTitle: String?,
    val repairActionLabel: String?,
) {
    companion object {
        fun fromReceipt(receipt: io.codecks.domain.actions.ActionReceipt, queueId: String): CommandExecutionResult =
            CommandExecutionResult(
                queueId = queueId,
                invocationId = receipt.invocationId,
                targetId = receipt.targetId,
                state = receipt.state.name,
                startedAtEpochMillis = receipt.startedAtEpochMillis,
                endedAtEpochMillis = receipt.endedAtEpochMillis,
                safeSummary = receipt.safeSummary,
                repairTitle = receipt.repair?.title,
                repairActionLabel = receipt.repair?.actionLabel,
            )
    }
}
