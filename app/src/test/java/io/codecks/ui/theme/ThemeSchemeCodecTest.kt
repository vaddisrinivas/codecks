package io.codecks.ui.theme

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSchemeCodecTest {
    private val bundle = ThemeBundle(
        global = ThemePresetCatalog.default,
        deck = ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Aurora },
        trackpad = ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Oled },
    )

    @Test
    fun `canonical export round trips all scopes`() {
        val encoded = ThemeSchemeCodec.encode(bundle)
        val decoded = ThemeSchemeCodec.decode(encoded) as ThemeImportResult.Success
        assertEquals(bundle, decoded.bundle)
        assertEquals(encoded, ThemeSchemeCodec.encode(decoded.bundle))
    }

    @Test
    fun `empty malformed and oversized imports fail closed`() {
        assertRejected("", ThemeImportResult.Reason.Empty)
        assertRejected("{oops", ThemeImportResult.Reason.Malformed)
        assertRejected("x".repeat(ThemeSchemeCodec.MAX_BYTES + 1), ThemeImportResult.Reason.Oversized)
    }

    @Test
    fun `unknown field and version fail closed`() {
        val encoded = JSONObject(ThemeSchemeCodec.encode(bundle))
        assertRejected(encoded.put("command", "open /tmp").toString(), ThemeImportResult.Reason.UnknownField)
        val version = JSONObject(ThemeSchemeCodec.encode(bundle)).put("version", 99)
        assertRejected(version.toString(), ThemeImportResult.Reason.UnsupportedVersion)
    }

    @Test
    fun `invalid color opacity and missing roles fail transactionally`() {
        val badColor = JSONObject(ThemeSchemeCodec.encode(bundle))
        badColor.getJSONObject("global").getJSONObject("colors").put("danger", "file:///tmp/red")
        assertRejected(badColor.toString(), ThemeImportResult.Reason.InvalidValue)

        val badOpacity = JSONObject(ThemeSchemeCodec.encode(bundle))
        badOpacity.getJSONObject("global").getJSONObject("opacity").put("surface", 1.5)
        assertRejected(badOpacity.toString(), ThemeImportResult.Reason.InvalidValue)

        val missing = JSONObject(ThemeSchemeCodec.encode(bundle))
        missing.getJSONObject("global").getJSONObject("colors").remove("primary")
        assertRejected(missing.toString(), ThemeImportResult.Reason.InvalidValue)
    }

    @Test
    fun `corrupt persisted data migrates to accessible default`() {
        assertEquals(ThemePresetCatalog.default, resolvePersistedThemeBundle("{bad").global)
        assertEquals(ThemePresetCatalog.default, resolvePersistedThemeBundle(null).global)
        assertEquals(bundle, resolvePersistedThemeBundle(ThemeSchemeCodec.encode(bundle)))
    }

    @Test
    fun `import field bounds reject labels and ids`() {
        val longLabel = JSONObject(ThemeSchemeCodec.encode(bundle))
        longLabel.getJSONObject("global").put("label", "a".repeat(49))
        assertRejected(longLabel.toString(), ThemeImportResult.Reason.InvalidValue)
        val unsafeId = JSONObject(ThemeSchemeCodec.encode(bundle))
        unsafeId.getJSONObject("global").put("id", "../../theme")
        assertRejected(unsafeId.toString(), ThemeImportResult.Reason.InvalidValue)
    }

    @Test
    fun `transparent role colors are rejected rather than incorrectly composited`() {
        val transparent = JSONObject(ThemeSchemeCodec.encode(bundle))
        transparent.getJSONObject("global").getJSONObject("colors").put("primary", "#803DDC84")
        assertRejected(transparent.toString(), ThemeImportResult.Reason.InvalidValue)
    }

    @Test
    fun `unreadable scoped scheme rejects entire import`() {
        val unreadable = JSONObject(ThemeSchemeCodec.encode(bundle))
        val deck = unreadable.getJSONObject("deck")
        val colors = deck.getJSONObject("colors")
        colors.put("border", colors.getString("background"))
        assertRejected(unreadable.toString(), ThemeImportResult.Reason.Unreadable)
    }

    @Test
    fun `unknown preset id is rejected and persisted failure is surfaced`() {
        val unknown = JSONObject(ThemeSchemeCodec.encode(bundle))
        unknown.getJSONObject("global").put("preset", "removed-preset")
        assertRejected(unknown.toString(), ThemeImportResult.Reason.InvalidValue)
        assertEquals(ThemeImportResult.Reason.InvalidValue, resolvePersistedTheme(unknown.toString()).issue)
    }

    @Test
    fun `legacy scalar opacity migrates to all semantic strengths`() {
        val legacy = JSONObject(ThemeSchemeCodec.encode(bundle))
        legacy.getJSONObject("global").put("opacity", .6)
        val decoded = ThemeSchemeCodec.decode(legacy.toString()) as ThemeImportResult.Success
        assertEquals(ThemeOpacity(.6f, .6f, .6f), decoded.bundle.global.opacity)
    }

    private fun assertRejected(raw: String, reason: ThemeImportResult.Reason) {
        val result = ThemeSchemeCodec.decode(raw)
        assertTrue(result is ThemeImportResult.Rejected)
        assertEquals(reason, (result as ThemeImportResult.Rejected).reason)
    }
}
