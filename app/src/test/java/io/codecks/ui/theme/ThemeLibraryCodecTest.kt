package io.codecks.ui.theme

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.Locale

class ThemeLibraryCodecTest {
    private val first = NamedTheme("theme-ocean", "Ocean work", ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Ocean }))

    @Test
    fun `library round trips names and complete bundles`() {
        val state = ThemeLibraryState(listOf(first))
        val decoded = ThemeLibraryCodec.decode(ThemeLibraryCodec.encode(state)) as ThemeLibraryDecodeResult.Success
        assertEquals(state, decoded.state)
        assertEquals(false, decoded.migrated)
    }

    @Test
    fun `v1 labels migrate without weakening bundle validation`() {
        val legacy = JSONObject().apply {
            put("version", 1)
            put("themes", JSONArray().put(JSONObject().apply {
                put("id", first.id)
                put("label", first.name)
                put("bundle", JSONObject(ThemeSchemeCodec.encode(first.bundle)))
            }))
        }
        val decoded = ThemeLibraryCodec.decode(legacy.toString()) as ThemeLibraryDecodeResult.Success
        assertEquals(ThemeLibraryState(listOf(first)), decoded.state)
        assertTrue(decoded.migrated)
    }

    @Test
    fun `bounds duplicate names and unknown fields fail closed`() {
        assertRejected("x".repeat(ThemeLibraryCodec.MAX_BYTES + 1), ThemeLibraryDecodeResult.Reason.Oversized)
        val duplicate = ThemeLibraryState(listOf(first)).runCatching {
            save(first.copy(id = "theme-other", name = "ocean WORK"))
        }
        assertTrue(duplicate.isFailure)
        val unknown = JSONObject(ThemeLibraryCodec.encode(ThemeLibraryState(listOf(first)))).put("command", "open")
        assertRejected(unknown.toString(), ThemeLibraryDecodeResult.Reason.UnknownField)
    }

    @Test
    fun `version id and name require exact JSON types`() {
        val valid = JSONObject(ThemeLibraryCodec.encode(ThemeLibraryState(listOf(first))))
        assertRejected(JSONObject(valid.toString()).put("version", "2").toString(), ThemeLibraryDecodeResult.Reason.InvalidValue)
        assertRejected(JSONObject(valid.toString()).put("version", true).toString(), ThemeLibraryDecodeResult.Reason.InvalidValue)

        fun changedTheme(field: String, value: Any): String {
            val root = JSONObject(valid.toString())
            root.getJSONArray("themes").getJSONObject(0).put(field, value)
            return root.toString()
        }
        assertRejected(changedTheme("id", 123), ThemeLibraryDecodeResult.Reason.InvalidValue)
        assertRejected(changedTheme("id", false), ThemeLibraryDecodeResult.Reason.InvalidValue)
        assertRejected(changedTheme("name", 123), ThemeLibraryDecodeResult.Reason.InvalidValue)
        assertRejected(changedTheme("name", true), ThemeLibraryDecodeResult.Reason.InvalidValue)
    }

    @Test
    fun `library capacity and names are bounded`() {
        var state = ThemeLibraryState()
        repeat(ThemeLibraryCodec.MAX_THEMES) { index ->
            state = state.save(first.copy(id = "theme-$index", name = "Theme $index"))
        }
        assertEquals(ThemeLibraryCodec.MAX_THEMES, state.themes.size)
        assertTrue(runCatching { state.save(first.copy(id = "theme-extra", name = "Extra")) }.isFailure)
        assertEquals(null, ThemeLibraryCodec.cleanName("\n"))
        assertEquals(null, ThemeLibraryCodec.cleanName("x".repeat(ThemeLibraryCodec.MAX_NAME_CHARS + 1)))
    }

    @Test
    fun `rename and delete reject missing ids`() {
        val state = ThemeLibraryState(listOf(first))
        assertTrue(runCatching { state.rename("theme-missing", "Missing") }.isFailure)
        assertTrue(runCatching { state.delete("theme-missing") }.isFailure)
    }

    @Test
    fun `repository mutation helper preserves coroutine cancellation`() {
        var thrown = false
        try {
            runBlocking { runThemeLibraryMutation { throw CancellationException("cancel") } }
        } catch (_: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `theme name folding is stable under Turkish locale`() {
        val prior = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("indigo", canonicalThemeName("INDIGO"))
        } finally {
            Locale.setDefault(prior)
        }
    }

    private fun assertRejected(raw: String, reason: ThemeLibraryDecodeResult.Reason) {
        val decoded = ThemeLibraryCodec.decode(raw)
        assertTrue(decoded is ThemeLibraryDecodeResult.Rejected)
        assertEquals(reason, (decoded as ThemeLibraryDecodeResult.Rejected).reason)
    }
}
