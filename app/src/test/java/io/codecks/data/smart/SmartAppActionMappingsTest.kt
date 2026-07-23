package io.codecks.data.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAppActionMappingsTest {

    @Test
    fun malformedAssetJsonReturnsNoMappings() {
        val raw = "{ \"schemaVersion\": 1, \"mappings\": [ { "

        val mappings = SmartAppActionMappings.parse(raw)

        assertTrue("Malformed JSON should safely fall back to empty mappings", mappings.isEmpty())
    }

    @Test
    fun missingMappingsKeyReturnsNoMappings() {
        val raw = "{\"schemaVersion\":1}"

        val mappings = SmartAppActionMappings.parse(raw)

        assertTrue(mappings.isEmpty())
    }

    @Test
    fun missingSchemaVersionReturnsNoMappings() {
        val raw = """
            {
              "mappings": [
                {
                  "appTokens": ["chrome"],
                  "actionTokens": ["reload"],
                  "reason": "must not load",
                  "score": 18
                }
              ]
            }
        """.trimIndent()

        val mappings = SmartAppActionMappings.parse(raw)

        assertTrue("Missing schema version must fail closed", mappings.isEmpty())
    }

    @Test
    fun unsupportedSchemaVersionReturnsNoMappings() {
        val raw = """
            {
              "schemaVersion": 2,
              "mappings": [
                {
                  "appTokens": ["chrome"],
                  "actionTokens": ["reload"],
                  "reason": "must not load",
                  "score": 18
                }
              ]
            }
        """.trimIndent()

        val mappings = SmartAppActionMappings.parse(raw)

        assertTrue("Unsupported schema version must fail closed", mappings.isEmpty())
    }

    @Test
    fun nonNumericSchemaVersionReturnsNoMappings() {
        val raw = """
            {
              "schemaVersion": "1",
              "mappings": [
                {
                  "appTokens": ["chrome"],
                  "actionTokens": ["reload"],
                  "reason": "must not load",
                  "score": 18
                }
              ]
            }
        """.trimIndent()

        val mappings = SmartAppActionMappings.parse(raw)

        assertTrue("Schema version must use the expected numeric representation", mappings.isEmpty())
    }

    @Test
    fun invalidEntriesAreSkipped() {
        val raw = """
            {
              "schemaVersion": 1,
              "mappings": [
                {
                  "appTokens": ["chrome"],
                  "actionTokens": ["reload"],
                  "reason": "good",
                  "score": 18
                },
                {
                  "appTokens": [],
                  "actionTokens": ["ignore"],
                  "reason": "bad",
                  "score": 18
                }
              ]
            }
        """.trimIndent()

        val mappings = SmartAppActionMappings.parse(raw)

        assertEquals(1, mappings.size)
        assertEquals("good", mappings.single().reason)
        assertEquals("chrome", mappings.single().appTokens.single())
    }
}
