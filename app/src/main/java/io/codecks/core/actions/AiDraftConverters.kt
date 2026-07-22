package io.codecks.core.actions

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.ai.ActionDefinition
import io.codecks.domain.ai.ActionStepTypes
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactAction
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.ai.AiArtifactParameter
import io.codecks.domain.ai.AiArtifactReview
import io.codecks.domain.ai.AiArtifactRiskLevel
import io.codecks.domain.ai.AiArtifactStepReview
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.ai.SafetyLevel
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationSafety
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.device.DeviceGroupId
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector

fun GeneratedDraft.toDeckActions(): Result<List<DeckAction>> = runCatching {
    val definitions = actionDefinitions()
    definitions.mapIndexed { index, definition ->
        definition.toDeckAction(index, definitions.size).getOrThrow()
    }
}

fun GeneratedDraft.toAiArtifact(promptOverride: String? = null): Result<AiArtifact> = runCatching {
    val definitions = actionDefinitions()
    val actions = definitions.mapIndexed { index, definition ->
        val command = definition.toCommand().getOrThrow()
        command.requireGeneratedAllowed()
        AiArtifactAction(
            id = generatedActionId(definition.id, index, definitions.size),
            title = definition.title,
            command = command,
            dangerous = definition.safety.requiresConfirmation,
        )
    }
    AiArtifact(
        id = "artifact_${System.currentTimeMillis()}_${title().slug()}",
        kind = artifactKind(),
        title = title(),
        description = description(),
        prompt = promptOverride ?: prompt(),
        actions = actions,
        review = toAiArtifactReview(),
    )
}

fun GeneratedDraft.toAutomationRecipe(): Result<AutomationRecipe> = runCatching {
    val automation = this as? GeneratedDraft.Automation ?: error("Draft is not an automation")
    automation.draft.definition.toAutomationRecipe(automation.draft.prompt).getOrThrow()
}

fun ActionDefinition.toDeckAction(index: Int, count: Int): Result<DeckAction> = runCatching {
    val command = toCommand().getOrThrow()
    command.requireGeneratedAllowed()
    DeckAction(
        id = generatedActionId(id, index, count),
        label = title,
        kind = ActionKind.Ssh,
        icon = ActionIcon.Apps,
        description = description,
        command = command,
        dangerous = true,
        liveSafe = false,
        requiresTest = true,
        targetSelector = target.toDeviceTargetSelector(),
    )
}

fun ActionDefinition.toAutomationRecipe(prompt: String): Result<AutomationRecipe> = runCatching {
    val convertedSteps = steps.map { step ->
        val command = step.toCommandFragment()
        command.requireGeneratedAllowed()
        ActionSpec.ShellCommand(
            id = "${id}_${step.label.ifBlank { step.id }}".slug(),
            title = step.label.ifBlank { title },
            command = command,
            trustLevel = ShellTrustLevel.Generated,
            dangerous = safety.requiresConfirmation,
            targetSelector = target.toDeviceTargetSelector(),
        )
    }
    AutomationRecipe(
        id = "ai_${id.slug()}",
        title = title,
        description = description.ifBlank { "AI-created automation from: $prompt" },
        enabled = false,
        trigger = AutomationTrigger.Manual,
        steps = convertedSteps,
        safety = AutomationSafety(safety.requiresConfirmation),
    )
}

private fun io.codecks.domain.ai.TargetSelector.toDeviceTargetSelector(): TargetSelector =
    when (this) {
        io.codecks.domain.ai.TargetSelector.ActiveDevice -> TargetSelector.CurrentDevice
        io.codecks.domain.ai.TargetSelector.AnyConnected -> TargetSelector.AllCompatibleDevices
        is io.codecks.domain.ai.TargetSelector.DeviceId -> id.takeIf(String::isNotBlank)
            ?.let { TargetSelector.SpecificDevice(DeviceId(it)) }
            ?: TargetSelector.CurrentDevice
        is io.codecks.domain.ai.TargetSelector.GroupId -> id.takeIf(String::isNotBlank)
            ?.let { TargetSelector.DeviceGroup(DeviceGroupId(it)) }
            ?: TargetSelector.CurrentDevice
    }

fun ActionDefinition.toCommand(): Result<String> = runCatching {
    steps.joinToString("\n") { it.toCommandFragment() }
}

private fun GeneratedDraft.actionDefinitions(): List<ActionDefinition> =
    when (this) {
        is GeneratedDraft.Action -> listOf(draft.definition)
        is GeneratedDraft.Automation -> listOf(draft.definition)
        is GeneratedDraft.Deck -> draft.actions
    }

fun GeneratedDraft.title(): String =
    when (this) {
        is GeneratedDraft.Action -> draft.definition.title
        is GeneratedDraft.Automation -> draft.label.ifBlank { draft.definition.title }
        is GeneratedDraft.Deck -> draft.title
    }

fun GeneratedDraft.description(): String =
    when (this) {
        is GeneratedDraft.Action -> draft.definition.description
        is GeneratedDraft.Automation -> draft.description.ifBlank { draft.definition.description }
        is GeneratedDraft.Deck -> draft.description
    }

fun GeneratedDraft.prompt(): String =
    when (this) {
        is GeneratedDraft.Action -> draft.prompt
        is GeneratedDraft.Automation -> draft.prompt
        is GeneratedDraft.Deck -> draft.prompt
    }

private fun GeneratedDraft.artifactKind(): AiArtifactKind =
    when (this) {
        is GeneratedDraft.Action -> AiArtifactKind.Button
        is GeneratedDraft.Automation -> AiArtifactKind.Automation
        is GeneratedDraft.Deck -> AiArtifactKind.Deck
    }

private fun GeneratedDraft.toAiArtifactReview(): AiArtifactReview {
    val definitions = actionDefinitions()
    val requiresConfirmation = definitions.any { definition ->
        definition.safety.requiresConfirmation ||
            definition.safety.level == SafetyLevel.Dangerous ||
            definition.steps.any { it.confirmedDangerous }
    }
    val capabilities = definitions
        .flatMap { definition -> definition.requiredCapabilities + definition.steps.flatMap { it.requiredCapabilities } }
        .map { it.name }
        .distinct()
        .sorted()
    return AiArtifactReview(
        assumptions = metadata().assumptions,
        riskLevel = if (requiresConfirmation) AiArtifactRiskLevel.Dangerous else AiArtifactRiskLevel.Normal,
        requiresConfirmation = requiresConfirmation,
        riskReason = definitions
            .mapNotNull { it.safety.confirmationBody?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" ")
            .ifBlank { null },
        target = definitions.map { it.target.reviewLabel() }.distinct().joinToString().ifBlank { "Any connected Mac" },
        trigger = when (this) {
            is GeneratedDraft.Automation -> "Manual trigger until explicitly enabled"
            else -> null
        },
        requiredCapabilities = capabilities,
        parameters = definitions
            .flatMap { it.variables }
            .distinctBy { it.name }
            .map {
                AiArtifactParameter(
                    name = it.name,
                    label = it.label,
                    required = it.required,
                    defaultValue = it.defaultValue,
                )
            },
        steps = definitions.flatMapIndexed { actionIndex, definition ->
            definition.steps.mapIndexed { stepIndex, step ->
                AiArtifactStepReview(
                    id = "${definition.id}_${step.id}".slug(),
                    label = step.label.ifBlank { "${definition.title} step ${stepIndex + 1}" },
                    type = step.type.reviewTypeLabel(),
                    summary = step.reviewSummary(),
                    requiresConfirmation = definition.safety.requiresConfirmation ||
                        definition.safety.level == SafetyLevel.Dangerous ||
                        step.confirmedDangerous,
                )
            }
        },
    )
}

private fun GeneratedDraft.metadata(): io.codecks.domain.ai.DraftReviewMetadata =
    when (this) {
        is GeneratedDraft.Action -> draft.metadata
        is GeneratedDraft.Automation -> draft.metadata
        is GeneratedDraft.Deck -> draft.metadata
    }

private fun io.codecks.domain.ai.TargetSelector.reviewLabel(): String =
    when (this) {
        io.codecks.domain.ai.TargetSelector.ActiveDevice -> "Active Mac"
        io.codecks.domain.ai.TargetSelector.AnyConnected -> "Any connected Mac"
        is io.codecks.domain.ai.TargetSelector.DeviceId -> "Device: ${id.ifBlank { "unspecified" }}"
        is io.codecks.domain.ai.TargetSelector.GroupId -> "Group: ${id.ifBlank { "unspecified" }}"
    }

private fun String.reviewTypeLabel(): String =
    when (this) {
        ActionStepTypes.OpenUrl -> "Open URL"
        ActionStepTypes.Delay -> "Delay"
        ActionStepTypes.ClipboardText -> "Clipboard"
        ActionStepTypes.Shell -> "Approved template"
        ActionStepTypes.SshAction -> "SSH action"
        ActionStepTypes.HidKey -> "HID key"
        else -> this
    }

private fun io.codecks.domain.ai.ActionStep.reviewSummary(): String =
    when (type) {
        ActionStepTypes.OpenUrl -> url.orEmpty().ifBlank { "Open URL" }
        ActionStepTypes.Delay -> "Wait ${(delayMs ?: 0L).coerceAtLeast(0L)} ms"
        ActionStepTypes.ClipboardText -> "Copy ${value.orEmpty().take(80).ifBlank { "text" }} to clipboard"
        ActionStepTypes.Shell,
        ActionStepTypes.SshAction,
        -> value.orEmpty().lineSequence().firstOrNull().orEmpty().ifBlank { "Approved command" }
        ActionStepTypes.HidKey -> value.orEmpty().ifBlank { "Keyboard shortcut" }
        else -> value ?: url ?: "Typed step"
    }

private fun io.codecks.domain.ai.ActionStep.toCommandFragment(): String =
    when (type) {
        ActionStepTypes.Shell,
        ActionStepTypes.SshAction -> value?.takeIf(String::isNotBlank) ?: error("Missing command in ${label.ifBlank { id }}")
        ActionStepTypes.OpenUrl -> "open ${shellQuote(url ?: error("Missing URL"))}"
        ActionStepTypes.Delay -> "sleep ${(delayMs ?: 0L).coerceAtLeast(0L) / 1000.0}"
        ActionStepTypes.ClipboardText -> "printf %s ${shellQuote(value.orEmpty())} | pbcopy"
        else -> error("$type cannot be saved as an SSH action")
    }

private fun generatedActionId(id: String, index: Int, count: Int): String {
    val slug = id.slug().ifBlank { "control" }
    return if (count <= 1) "ai_$slug" else "ai_${slug}_${index + 1}"
}

private fun String.slug(): String =
    lowercase()
        .map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "control" }

private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

private fun String.requireGeneratedAllowed() {
    RawCommandPolicy.firstViolation(this)?.let { reason ->
        error("Generated command blocked: $reason")
    }
}
