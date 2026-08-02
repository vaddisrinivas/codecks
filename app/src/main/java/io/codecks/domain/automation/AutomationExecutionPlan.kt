package io.codecks.domain.automation

import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.RawCommandPolicy
import io.codecks.core.actions.ShellTrustLevel
import io.codecks.core.actions.commandRevision
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.ai.MacVisualEffectCatalog
import java.security.MessageDigest
import java.util.Collections

enum class AutomationAssertionKind {
    EXIT_CODE_ZERO,
}

data class AutomationActionAssertion(
    val assertionId: String,
    val actionId: String,
    val actionRevision: String,
    val kind: AutomationAssertionKind,
    val expectedExitCode: Int,
)

data class NormalizedAutomationAction(
    val ordinal: Int,
    val actionId: String,
    val title: String,
    val command: String,
    val actionRevision: String,
    val assertion: AutomationActionAssertion,
    val requiredTools: Set<String>,
)

data class NormalizedAutomationCleanup(
    val action: NormalizedAutomationAction,
    val runAfter: Set<AutomationCleanupTrigger>,
    val undoGuarantee: AutomationUndoGuarantee,
)

class NormalizedAutomationPlan private constructor(
    val recipeId: String,
    val triggerToken: String,
    val requiresConfirmation: Boolean,
    actions: List<NormalizedAutomationAction>,
    val cleanup: NormalizedAutomationCleanup?,
    val planHash: String,
) {
    val actions: List<NormalizedAutomationAction> =
        Collections.unmodifiableList(actions.toList())

    val assertions: List<AutomationActionAssertion> =
        Collections.unmodifiableList(this.actions.map(NormalizedAutomationAction::assertion))

    companion object {
        internal fun create(
            recipeId: String,
            triggerToken: String,
            requiresConfirmation: Boolean,
            actions: List<NormalizedAutomationAction>,
            cleanup: NormalizedAutomationCleanup?,
        ): NormalizedAutomationPlan {
            val canonical = buildList {
                add("automation-plan-v1")
                add("recipe=$recipeId")
                add("trigger=$triggerToken")
                add("confirmation=$requiresConfirmation")
                actions.forEach { action ->
                    add(
                        listOf(
                            action.ordinal,
                            action.actionId,
                            action.title,
                            action.command,
                            action.actionRevision,
                            action.assertion.assertionId,
                            action.assertion.kind.name,
                            action.assertion.expectedExitCode,
                            action.requiredTools.sorted().joinToString(","),
                        ).joinToString("\u0000"),
                    )
                }
                cleanup?.let {
                    add("cleanup=${it.action.actionRevision}")
                    add("cleanupTriggers=${it.runAfter.map(AutomationCleanupTrigger::persistedCode).sorted().joinToString(",")}")
                    add("undo=${it.undoGuarantee.persistedCode}")
                }
            }.joinToString("\u0000")
            return NormalizedAutomationPlan(
                recipeId = recipeId,
                triggerToken = triggerToken,
                requiresConfirmation = requiresConfirmation,
                actions = actions,
                cleanup = cleanup,
                planHash = canonical.sha256(),
            )
        }
    }
}

object AutomationExecutionPlanCompiler {
    fun compile(recipe: AutomationRecipe): Result<NormalizedAutomationPlan> = runCatching {
        require(recipe.id.isNotBlank()) { "Automation recipe id is empty" }
        require(recipe.steps.isNotEmpty()) { "Automation has no executable actions" }
        require(recipe.steps.map(ActionSpec::id).distinct().size == recipe.steps.size) {
            "Automation action ids must be unique"
        }
        val actions = recipe.steps.mapIndexed { index, spec ->
            spec.toNormalizedAction(index)
        }
        val cleanup = recipe.cleanupDefinition.action?.let { action ->
            require(action.id !in recipe.steps.map(ActionSpec::id)) {
                "Cleanup action id must be unique"
            }
            NormalizedAutomationCleanup(
                action = action.toNormalizedAction(actions.size),
                runAfter = Collections.unmodifiableSet(recipe.cleanupDefinition.runAfter.toSet()),
                undoGuarantee = recipe.cleanupDefinition.undoGuarantee,
            )
        }
        check(actions.size == recipe.steps.size) {
            "Every executable action requires a deterministic assertion"
        }
        NormalizedAutomationPlan.create(
            recipeId = recipe.id.trim(),
            triggerToken = recipe.trigger.canonicalToken(),
            requiresConfirmation = recipe.safety.requiresConfirmation,
            actions = actions,
            cleanup = cleanup,
        )
    }
}

private fun ActionSpec.toNormalizedAction(index: Int): NormalizedAutomationAction {
    val rawCommand = when (this) {
        is ActionSpec.ShellCommand -> command
        is ActionSpec.DeckActionSpec -> {
            require(action.kind == ActionKind.Ssh) {
                "Unsupported automation action ${action.id}: local deck action"
            }
            action.command ?: error("Unsupported automation action ${action.id}: missing command")
        }
        is ActionSpec.CatalogAction ->
            error("Unsupported automation action $id: unresolved catalog action")
        is ActionSpec.LocalRoute ->
            error("Unsupported automation action $id: local route")
    }
    val normalizedCommand = rawCommand.normalizeCommand()
    RawCommandPolicy.firstViolation(normalizedCommand)?.let { reason ->
        error("Blocked automation action $id: $reason")
    }
    if (isGeneratedOutput() && !normalizedCommand.isExactBuiltInVisualEffect()) {
        RawCommandPolicy.firstAllowlistViolation(normalizedCommand)?.let { reason ->
            error("Unsupported generated automation action $id: $reason")
        }
    }
    val revision = commandRevision(
        command = normalizedCommand,
        targetSelector = targetSelector,
        origin = commandOrigin,
        dangerous = dangerous,
        riskReason = riskReason,
        confirmationTitle = confirmationTitle,
        confirmationBody = confirmationBody,
    )
    if (commandOrigin != CommandOrigin.Bundled || isGeneratedOutput()) {
        require(review.reviewedRevision == revision) {
            "Automation action $id has missing or stale review hash"
        }
    }
    require(review.checkedRevision == null || review.checkedRevision == revision) {
        "Automation action $id has stale checked hash"
    }
    val assertion = AutomationActionAssertion(
        assertionId = "assert-${"$index\u0000$id\u0000$revision\u0000exit-zero".sha256().take(24)}",
        actionId = id,
        actionRevision = revision,
        kind = AutomationAssertionKind.EXIT_CODE_ZERO,
        expectedExitCode = 0,
    )
    return NormalizedAutomationAction(
        ordinal = index,
        actionId = id.trim(),
        title = title.trim(),
        command = normalizedCommand,
        actionRevision = revision,
        assertion = assertion,
        requiredTools = normalizedCommand.executableToolRequirements(),
    )
}

private fun ActionSpec.isGeneratedOutput(): Boolean =
    commandOrigin == CommandOrigin.AiGenerated ||
        (this is ActionSpec.ShellCommand && trustLevel == ShellTrustLevel.Generated)

private fun String.isExactBuiltInVisualEffect(): Boolean =
    MacVisualEffectCatalog.templateIds.any { templateId ->
        this == MacVisualEffectCatalog.commandForTemplate(templateId)
    }

private fun String.normalizeCommand(): String {
    require('\u0000' !in this) { "Automation command contains a NUL byte" }
    return replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .also { require(it.isNotEmpty()) { "Automation command is empty" } }
}

/**
 * Compiler-owned executable requirements. Shell control boundaries are parsed conservatively so
 * every pipeline/sequence/substitution command is checked, rather than only the first token.
 */
private fun String.executableToolRequirements(): Set<String> {
    val commandBoundary = Regex("""(?:^|[|;&]|\$\()\s*([A-Za-z0-9_.+-]+)""")
    return commandBoundary.findAll(this)
        .map { it.groupValues[1] }
        .filterNot { it in SHELL_BUILTINS }
        .toSet()
}

private val SHELL_BUILTINS = setOf(
    "cd",
    "command",
    "echo",
    "else",
    "env",
    "exit",
    "export",
    "false",
    "fi",
    "if",
    "printf",
    "read",
    "set",
    "test",
    "then",
    "true",
    "unset",
)

private fun AutomationTrigger.canonicalToken(): String = when (this) {
    AutomationTrigger.Manual -> "manual"
    is AutomationTrigger.AiSuggested -> "ai:${prompt.trim()}"
    is AutomationTrigger.TimeOfDay ->
        "time:${hour.coerceIn(0, 23)}:${minute.coerceIn(0, 59)}:${days.map(String::trim).sorted().joinToString(",")}"
    is AutomationTrigger.ActiveApp -> "app:${appName.trim()}"
    is AutomationTrigger.ClipboardContains -> "clipboard:${text.trim()}"
    is AutomationTrigger.WifiSsid -> "wifi:${ssid.trim()}"
    AutomationTrigger.MacAwake -> "mac_awake"
    is AutomationTrigger.FileChanged -> "file:${path.trim()}"
    is AutomationTrigger.BatteryBelow -> "battery:${percent.coerceIn(1, 100)}"
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
