package io.codecks.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedTargetSelectorMigrationTest {
    private val legacyId = "mac_alice_studio_local_22"
    private val opaqueId = "8ee80175-c04f-4b03-ae3c-ad18f129a553"
    private val migrations = mapOf(legacyId to opaqueId)

    @Test
    fun deckPayloadRewritesOnlyKnownLegacySpecificDeviceIds() {
        val currentOpaqueId = "5b1d3b35-d5c0-451e-90ef-4dc2bc086369"
        val raw = """
            {
              "schemaVersion": 4,
              "columns": 3,
              "items": [
                {"id":"legacy","target":{"type":"device","id":"$legacyId"}},
                {"id":"current","target":{"type":"device","id":"$currentOpaqueId"}},
                {"id":"unknown","target":{"type":"device","id":"mac_guessed_target_22"}}
              ]
            }
        """.trimIndent()

        val result = migrateDeckTargetSelectorPayload(raw, migrations)

        assertTrue(result is PersistedTargetSelectorMigration.Migrated)
        result as PersistedTargetSelectorMigration.Migrated
        assertEquals(1, result.count)
        assertFalse(result.payload.contains(legacyId))
        val items = JSONObject(result.payload).getJSONArray("items")
        assertEquals(opaqueId, items.getJSONObject(0).getJSONObject("target").getString("id"))
        assertEquals(currentOpaqueId, items.getJSONObject(1).getJSONObject("target").getString("id"))
        assertEquals("mac_guessed_target_22", items.getJSONObject(2).getJSONObject("target").getString("id"))
    }

    @Test
    fun automationPayloadRewritesEveryKnownStepAndPreservesOtherData() {
        val raw = """
            {
              "schemaVersion": 3,
              "items": [{
                "id": "daily",
                "title": "Daily",
                "runHistory": [{"message":"keep me"}],
                "steps": [
                  {"type":"shell","id":"one","target":{"type":"device","id":"$legacyId"}},
                  {"type":"local","id":"two","target":{"type":"current"}}
                ]
              }]
            }
        """.trimIndent()

        val result = migrateAutomationTargetSelectorPayload(raw, migrations)

        assertTrue(result is PersistedTargetSelectorMigration.Migrated)
        result as PersistedTargetSelectorMigration.Migrated
        assertEquals(1, result.count)
        assertFalse(result.payload.contains(legacyId))
        val recipe = JSONObject(result.payload).getJSONArray("items").getJSONObject(0)
        assertEquals(
            opaqueId,
            recipe.getJSONArray("steps").getJSONObject(0).getJSONObject("target").getString("id"),
        )
        assertEquals("keep me", recipe.getJSONArray("runHistory").getJSONObject(0).getString("message"))
    }

    @Test
    fun partiallyUndecodablePayloadIsNeverReturnedAsMigrated() {
        val corruptDeck = """
            {"schemaVersion":4,"items":[
              {"id":"valid","target":{"type":"device","id":"$legacyId"}},
              {"id":"broken","target":"not-an-object"}
            ]}
        """.trimIndent()
        val corruptAutomation = """
            {"schemaVersion":3,"items":[
              {"id":"valid","steps":[{"id":"one","target":{"type":"device","id":"$legacyId"}}]},
              {"id":"broken","steps":"not-an-array"}
            ]}
        """.trimIndent()

        assertEquals(
            PersistedTargetSelectorMigration.Undecodable,
            migrateDeckTargetSelectorPayload(corruptDeck, migrations),
        )
        assertEquals(
            PersistedTargetSelectorMigration.Undecodable,
            migrateAutomationTargetSelectorPayload(corruptAutomation, migrations),
        )
    }

    @Test
    fun unknownOrAlreadyOpaqueSelectorsDoNotTriggerRewrite() {
        val raw = """
            {"schemaVersion":4,"items":[
              {"id":"current","target":{"type":"device","id":"$opaqueId"}},
              {"id":"unknown","target":{"type":"device","id":"mac_guessed_target_22"}}
            ]}
        """.trimIndent()

        assertEquals(
            PersistedTargetSelectorMigration.Unchanged,
            migrateDeckTargetSelectorPayload(raw, migrations),
        )
    }
}
