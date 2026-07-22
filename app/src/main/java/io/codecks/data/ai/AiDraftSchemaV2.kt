package io.codecks.data.ai

import io.codecks.domain.ai.ActionCapability
import io.codecks.domain.ai.AiActionCatalog
import io.codecks.domain.ai.CURRENT_DRAFT_ENVELOPE_VERSION
import io.codecks.domain.ai.DraftEnvelopeStatus
import io.codecks.domain.ai.DraftKind
import io.codecks.domain.ai.SafetyLevel
import io.codecks.domain.ai.TargetKind

internal object AiDraftSchemaV2 {
    fun schemaFor(kind: DraftKind): Map<String, Any> =
        envelopeSchema(proposalSchema(kind))

    private fun envelopeSchema(proposal: Map<String, Any>): Map<String, Any> =
        obj(
            "schemaVersion" to mapOf("type" to "integer", "enum" to listOf(CURRENT_DRAFT_ENVELOPE_VERSION)),
            "status" to enumString(DraftEnvelopeStatus.entries.map { it.wireName }),
            "message" to string(),
            "questions" to stringArray(maxItems = 4),
            "assumptions" to stringArray(maxItems = 8),
            "proposal" to nullableObject(proposal),
        )

    private fun proposalSchema(kind: DraftKind): Map<String, Any> =
        when (kind) {
            DraftKind.Action -> definitionSchema()
            DraftKind.Automation -> obj(
                "id" to string(),
                "label" to string(),
                "description" to string(),
                "category" to string(),
                "dangerous" to bool(),
                "definition" to definitionSchema(),
            )
            DraftKind.Deck -> obj(
                "id" to string(),
                "title" to string(),
                "description" to string(),
                "actions" to array(definitionSchema(), minItems = 1, maxItems = 12),
            )
        }

    private fun definitionSchema(): Map<String, Any> =
        obj(
            "id" to string(),
            "title" to string(),
            "description" to string(),
            "requiredCapabilities" to enumArray(ActionCapability.entries.map { it.name }, maxItems = 4),
            "target" to obj(
                "type" to enumString(TargetKind.entries.map { it.name }),
                "id" to nullableString(),
            ),
            "safety" to obj(
                "level" to enumString(SafetyLevel.entries.map { it.name }),
                "requiresConfirmation" to bool(),
                "confirmationTitle" to nullableString(),
                "confirmationBody" to nullableString(),
            ),
            "steps" to array(stepSchema(), minItems = 1, maxItems = 8),
        )

    private fun stepSchema(): Map<String, Any> =
        obj(
            "id" to string(),
            "type" to enumString(AiActionCatalog.stepTypes.sorted()),
            "label" to string(),
            "url" to nullableString(),
            "text" to nullableString(),
            "delayMs" to nullableInteger(),
            "templateId" to nullableEnumString(AiActionCatalog.templateIds.sorted()),
            "command" to nullableCommandString(),
            "requiresConfirmation" to bool(),
        )

    private fun obj(vararg properties: Pair<String, Map<String, Any>>): Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to properties.map { it.first },
            "properties" to properties.toMap(),
        )

    private fun nullableObject(schema: Map<String, Any>): Map<String, Any> =
        schema + ("type" to listOf("object", "null"))

    private fun array(
        items: Map<String, Any>,
        minItems: Int = 0,
        maxItems: Int,
    ): Map<String, Any> =
        mapOf(
            "type" to "array",
            "minItems" to minItems,
            "maxItems" to maxItems,
            "items" to items,
        )

    private fun enumArray(values: List<String>, maxItems: Int): Map<String, Any> =
        array(enumString(values), maxItems = maxItems)

    private fun stringArray(maxItems: Int): Map<String, Any> =
        array(string(), maxItems = maxItems)

    private fun string(): Map<String, Any> = mapOf("type" to "string", "maxLength" to 600)

    private fun nullableCommandString(): Map<String, Any> =
        mapOf("type" to listOf("string", "null"), "maxLength" to 8_000)

    private fun nullableString(): Map<String, Any> = mapOf("type" to listOf("string", "null"), "maxLength" to 1200)

    private fun bool(): Map<String, Any> = mapOf("type" to "boolean")

    private fun nullableInteger(): Map<String, Any> = mapOf("type" to listOf("integer", "null"))

    private fun enumString(values: List<String>): Map<String, Any> =
        mapOf("type" to "string", "enum" to values)

    private fun nullableEnumString(values: List<String>): Map<String, Any> =
        mapOf("type" to listOf("string", "null"), "enum" to values + null)
}
