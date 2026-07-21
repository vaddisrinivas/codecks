package io.codecks.data.ai

import io.codecks.domain.ai.DraftKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDraftSchemaV2Test {
    @Test
    fun allV2DraftSchemas_areStrictRequiredObjects() {
        listOf(DraftKind.Action, DraftKind.Automation, DraftKind.Deck).forEach { kind ->
            assertStrictObjects(AiDraftSchemaV2.schemaFor(kind), path = kind.name)
        }
    }

    @Test
    fun v2Schema_doesNotExposeRawCommandFields() {
        val schemaText = jsonObject("schema" to AiDraftSchemaV2.schemaFor(DraftKind.Action))

        assertFalse(schemaText.contains("\"shell\""))
        assertFalse(schemaText.contains("\"ssh_action\""))
        assertFalse(schemaText.contains("\"command\""))
        assertFalse(schemaText.contains("\"value\""))
        assertTrue(schemaText.contains("\"templateId\""))
        assertTrue(schemaText.contains("\"mac.focus_ping_30\""))
    }

    @Suppress("UNCHECKED_CAST")
    private fun assertStrictObjects(schema: Map<String, Any>, path: String) {
        val type = schema["type"]
        val isObject = type == "object" || (type as? List<*>)?.contains("object") == true
        if (isObject) {
            assertEquals("$path must set additionalProperties=false", false, schema["additionalProperties"])
            val properties = schema["properties"] as Map<String, Any>
            val required = schema["required"] as List<String>
            assertEquals("$path required fields must match properties", properties.keys.toSet(), required.toSet())
            properties.forEach { (name, child) ->
                assertStrictObjects(child as Map<String, Any>, "$path.$name")
            }
        }

        val items = schema["items"] as? Map<String, Any>
        if (items != null) {
            assertStrictObjects(items, "$path[]")
        }
    }
}
