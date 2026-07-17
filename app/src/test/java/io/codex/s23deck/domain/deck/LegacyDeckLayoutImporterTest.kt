package io.codex.s23deck.domain.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyDeckLayoutImporterTest {
    private val availableIds = setOf(
        "finder",
        "terminal",
        "spotlight",
        "new_tab",
        "play_pause",
        "vol_up",
        "vol_down",
        "add_button",
    )

    @Test
    fun imports24SlotCsvLayout() {
        val raw = listOf("Finder", "terminal", "empty", "add", "volume_up").joinToString(",")

        val result = LegacyDeckLayoutImporter.importActionIds(raw, availableIds, requestedSlotCount = 24)

        assertEquals(24, result.slotCount)
        assertEquals(listOf("finder", "terminal", null, "add_button", "vol_up"), result.actionIds.take(5))
        assertEquals(4, result.importedCount)
        assertEquals(emptyList<String>(), result.unknownIds)
        assertNull(result.actionIds.last())
    }

    @Test
    fun imports32SlotKeyedLegacyLayoutAndTracksUnknownIds() {
        val raw = """
            size=32
            buttonId=finder
            buttonId=legacy_magic
            buttonId=playpause
            buttonId=none
        """.trimIndent()

        val result = LegacyDeckLayoutImporter.importActionIds(raw, availableIds)

        assertEquals(32, result.slotCount)
        assertEquals(listOf("finder", null, "play_pause", null), result.actionIds.take(4))
        assertEquals(listOf("legacy_magic"), result.unknownIds)
    }

    @Test
    fun imports64SlotJsonLikeLayout() {
        val raw = (listOf("new_tab", "spotlight", "vol_down") + List(61) { "blank" })
            .joinToString(prefix = """{"layout":64,"ids":[""", separator = """",""", postfix = """"]}""")

        val result = LegacyDeckLayoutImporter.importActionIds(raw, availableIds)

        assertEquals(64, result.slotCount)
        assertEquals(listOf("new_tab", "spotlight", "vol_down"), result.actionIds.take(3))
        assertEquals(64, result.actionIds.size)
    }
}
