package io.codecks.domain.ai

import java.net.URI

class ActionDraftValidator(
    private val supportedSchemaVersions: IntRange = CURRENT_ACTION_SCHEMA_VERSION..CURRENT_ACTION_SCHEMA_VERSION,
    private val supportedCapabilities: Set<ActionCapability> = ActionCapability.entries.toSet(),
    private val supportedTargets: Set<TargetKind> = TargetKind.entries.toSet(),
    private val maxDelayMs: Long = 60_000,
    private val maxRetryAttempts: Int = 5,
    private val maxRetryDelayMs: Long = 30_000,
) {
    fun validate(draft: ActionDraft): ValidationResult = validate(draft.definition)

    fun validate(definition: ActionDefinition): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (definition.schemaVersion !in supportedSchemaVersions) {
            errors += ValidationError("schemaVersion", "Unsupported schema version ${definition.schemaVersion}")
        }
        if (definition.id.isBlank()) errors += ValidationError("id", "Action id is required")
        if (definition.title.isBlank()) errors += ValidationError("title", "Action title is required")
        if (definition.steps.isEmpty()) errors += ValidationError("steps", "At least one step is required")
        rejectDuplicateVariables(definition.variables, errors)
        rejectDuplicateTemplates(definition.templates, errors)

        rejectMissingCapabilities("requiredCapabilities", definition.requiredCapabilities, errors)
        rejectUnsupportedTarget(definition.target, errors)
        rejectDangerousWithoutConfirmation(definition.safety, errors)

        val stepIds = mutableSetOf<String>()
        definition.steps.forEachIndexed { index, step ->
            val path = "steps[$index]"
            if (step.id.isBlank()) errors += ValidationError("$path.id", "Step id is required")
            if (!stepIds.add(step.id)) errors += ValidationError("$path.id", "Step id must be unique")
            if (step.type !in ActionStepTypes.supported) {
                errors += ValidationError("$path.type", "Unsupported step type ${step.type}")
            }
            rejectMissingCapabilities("$path.requiredCapabilities", step.requiredCapabilities, errors)
            rejectMissingStepPayload(path, step, errors)
            rejectUnboundedDelay(path, step, errors)
            rejectUnboundedRetry(path, step.retry, errors)
            rejectInvalidUrl(path, step, errors)
            rejectRawShellWithoutAdvanced(path, definition, step, errors)
            rejectDangerousStepWithoutConfirmation(path, definition, step, errors)
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    fun migrate(definition: ActionDefinition): ActionDefinition =
        when (definition.schemaVersion) {
            CURRENT_ACTION_SCHEMA_VERSION -> definition
            else -> definition
        }

    private fun rejectMissingCapabilities(
        path: String,
        capabilities: List<ActionCapability>,
        errors: MutableList<ValidationError>,
    ) {
        capabilities.filterNot { it in supportedCapabilities }.forEach {
            errors += ValidationError(path, "Unsupported capability $it")
        }
    }

    private fun rejectUnsupportedTarget(target: TargetSelector, errors: MutableList<ValidationError>) {
        val kind = when (target) {
            TargetSelector.ActiveDevice -> TargetKind.ActiveDevice
            TargetSelector.AnyConnected -> TargetKind.AnyConnected
            is TargetSelector.DeviceId -> TargetKind.DeviceId
            is TargetSelector.GroupId -> TargetKind.GroupId
        }
        if (kind !in supportedTargets) {
            errors += ValidationError("target", "Unsupported target $kind")
        }
        if (target is TargetSelector.DeviceId && target.id.isBlank()) {
            errors += ValidationError("target.id", "Device target id is required")
        }
        if (target is TargetSelector.GroupId && target.id.isBlank()) {
            errors += ValidationError("target.id", "Group target id is required")
        }
    }

    private fun rejectDuplicateVariables(
        variables: List<ActionVariable>,
        errors: MutableList<ValidationError>,
    ) {
        val names = mutableSetOf<String>()
        variables.forEachIndexed { index, variable ->
            val path = "variables[$index].name"
            if (variable.name.isBlank()) errors += ValidationError(path, "Variable name is required")
            if (!names.add(variable.name)) errors += ValidationError(path, "Variable name must be unique")
        }
    }

    private fun rejectDuplicateTemplates(
        templates: List<ActionTemplate>,
        errors: MutableList<ValidationError>,
    ) {
        val ids = mutableSetOf<String>()
        templates.forEachIndexed { index, template ->
            val path = "templates[$index].id"
            if (template.id.isBlank()) errors += ValidationError(path, "Template id is required")
            if (!ids.add(template.id)) errors += ValidationError(path, "Template id must be unique")
        }
    }

    private fun rejectDangerousWithoutConfirmation(
        safety: SafetyMetadata,
        errors: MutableList<ValidationError>,
    ) {
        if (safety.level == SafetyLevel.Dangerous && !safety.requiresConfirmation) {
            errors += ValidationError("safety.requiresConfirmation", "Dangerous actions require confirmation metadata")
        }
        if (safety.requiresConfirmation && safety.confirmationTitle.isNullOrBlank()) {
            errors += ValidationError("safety.confirmationTitle", "Confirmation title is required")
        }
    }

    private fun rejectUnboundedDelay(
        path: String,
        step: ActionStep,
        errors: MutableList<ValidationError>,
    ) {
        val delay = step.delayMs ?: return
        if (delay < 0 || delay > maxDelayMs) {
            errors += ValidationError("$path.delayMs", "Delay must be between 0 and $maxDelayMs")
        }
    }

    private fun rejectMissingStepPayload(
        path: String,
        step: ActionStep,
        errors: MutableList<ValidationError>,
    ) {
        when (step.type) {
            ActionStepTypes.OpenUrl -> {
                if (step.url.isNullOrBlank()) errors += ValidationError("$path.url", "URL is required")
            }
            ActionStepTypes.Delay -> {
                if (step.delayMs == null) errors += ValidationError("$path.delayMs", "Delay is required")
            }
            ActionStepTypes.ClipboardText -> {
                if (step.value.isNullOrBlank()) errors += ValidationError("$path.value", "Clipboard text is required")
            }
            ActionStepTypes.Shell,
            ActionStepTypes.SshAction,
            -> {
                if (step.value.isNullOrBlank()) errors += ValidationError("$path.value", "Command value is required")
            }
        }
    }

    private fun rejectUnboundedRetry(
        path: String,
        retry: RetryPolicy,
        errors: MutableList<ValidationError>,
    ) {
        if (retry.maxAttempts !in 1..maxRetryAttempts) {
            errors += ValidationError("$path.retry.maxAttempts", "Retry attempts must be between 1 and $maxRetryAttempts")
        }
        if (retry.delayMs < 0 || retry.delayMs > maxRetryDelayMs) {
            errors += ValidationError("$path.retry.delayMs", "Retry delay must be between 0 and $maxRetryDelayMs")
        }
    }

    private fun rejectInvalidUrl(
        path: String,
        step: ActionStep,
        errors: MutableList<ValidationError>,
    ) {
        if (step.type != ActionStepTypes.OpenUrl) return
        val url = step.url
        if (url.isNullOrBlank()) {
            errors += ValidationError("$path.url", "URL is required")
            return
        }
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            errors += ValidationError("$path.url", "URL must be absolute http or https")
        }
    }

    private fun rejectRawShellWithoutAdvanced(
        path: String,
        definition: ActionDefinition,
        step: ActionStep,
        errors: MutableList<ValidationError>,
    ) {
        if (step.type != ActionStepTypes.Shell) return
        val hasAdvanced = ActionCapability.Advanced in definition.requiredCapabilities ||
            ActionCapability.Advanced in step.requiredCapabilities
        if (!hasAdvanced) {
            errors += ValidationError("$path.requiredCapabilities", "Raw shell requires Advanced capability")
        }
    }

    private fun rejectDangerousStepWithoutConfirmation(
        path: String,
        definition: ActionDefinition,
        step: ActionStep,
        errors: MutableList<ValidationError>,
    ) {
        if (definition.safety.level == SafetyLevel.Dangerous && !step.confirmedDangerous) {
            errors += ValidationError("$path.confirmedDangerous", "Dangerous steps require confirmation metadata")
        }
    }
}

enum class TargetKind {
    AnyConnected,
    ActiveDevice,
    DeviceId,
    GroupId,
}

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: List<ValidationError>) : ValidationResult
}

data class ValidationError(
    val path: String,
    val message: String,
)
