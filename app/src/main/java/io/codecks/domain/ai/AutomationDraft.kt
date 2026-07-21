package io.codecks.domain.ai

const val CURRENT_AUTOMATION_SCHEMA_VERSION = 1

data class AutomationDraft(
    val schemaVersion: Int = CURRENT_AUTOMATION_SCHEMA_VERSION,
    val prompt: String,
    val id: String,
    val label: String,
    val description: String = "",
    val category: String,
    val dangerous: Boolean = false,
    val definition: ActionDefinition,
    val metadata: DraftReviewMetadata = DraftReviewMetadata(),
)

class AutomationDraftValidator(
    private val supportedSchemaVersions: IntRange = CURRENT_AUTOMATION_SCHEMA_VERSION..CURRENT_AUTOMATION_SCHEMA_VERSION,
    private val actionValidator: ActionDraftValidator = ActionDraftValidator(),
) {
    fun validate(draft: AutomationDraft): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        if (draft.schemaVersion !in supportedSchemaVersions) {
            errors += ValidationError("schemaVersion", "Unsupported automation schema version ${draft.schemaVersion}")
        }
        if (draft.id.isBlank()) errors += ValidationError("id", "Automation id is required")
        if (draft.label.isBlank()) errors += ValidationError("label", "Automation label is required")
        if (draft.category.isBlank()) errors += ValidationError("category", "Automation category is required")
        when (val definitionValidation = actionValidator.validate(draft.definition)) {
            ValidationResult.Valid -> Unit
            is ValidationResult.Invalid -> definitionValidation.errors.forEach { error ->
                errors += ValidationError("definition.${error.path}", error.message)
            }
        }
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
