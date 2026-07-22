package io.codecks.data.ai

import io.codecks.core.actions.RawCommandPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledActionCatalogContractTest {
    private val items = parseJsonArray(File("src/main/assets/codecks_actions.json").readText())
        .map(JsonValue::asObject)

    @Test
    fun catalogIdsAreUniqueAndSshActionsHaveCommands() {
        val ids = items.map { it.optString("id").orEmpty() }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.none(String::isBlank))
        items.filter { it.optString("kind") == "ssh" }.forEach { action ->
            assertTrue(action.optString("command").orEmpty().isNotBlank())
        }
    }

    @Test
    fun everyBundledCommandPassesTheHardSafetyPolicy() {
        items.forEach { action ->
            listOf("command", "test_command").forEach { field ->
                action.optString(field)?.takeIf(String::isNotBlank)?.let { command ->
                    assertNull("${action.optString("id")}.$field: $command", RawCommandPolicy.firstViolation(command))
                }
            }
        }
    }

    @Test
    fun disruptiveActionsAreMarkedDangerousAndExplainTheirEffect() {
        listOf("lock_mac", "sleep_display").forEach { id ->
            val action = items.firstOrNull { it.optString("id") == id }
            assertNotNull(id, action)
            assertTrue(id, action!!.bool("dangerous", false))
            assertTrue(id, action.optString("description").orEmpty().isNotBlank())
        }
    }
}
