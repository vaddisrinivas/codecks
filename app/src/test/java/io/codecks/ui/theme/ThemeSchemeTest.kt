package io.codecks.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSchemeTest {
    @Test
    fun `all eight offline presets have complete accessible roles`() {
        assertEquals(8, ThemePresetCatalog.presets.size)
        assertEquals(8, ThemePresetCatalog.presets.map { it.id }.toSet().size)
        ThemePresetCatalog.presets.forEach { scheme ->
            assertEquals(ThemeColorRole.entries.toSet(), scheme.colors.keys)
            assertTrue(scheme.label, ThemeContrast.isCriticalReadable(scheme))
            assertTrue(
                scheme.label,
                ThemeContrast.ratio(scheme[ThemeColorRole.Border], scheme[ThemeColorRole.Background]) >= 3.0,
            )
        }
    }

    @Test
    fun `unknown preset is surfaced instead of silently substituted`() {
        assertEquals(null, ThemePresetCatalog.resolve("removed-theme"))
        assertEquals(ThemePresetCatalog.default, ThemePresetCatalog.resolve("codecks-green"))
    }

    @Test
    fun `scoped appearance falls back while global semantics stay global`() {
        val global = ThemePresetCatalog.default
        val deck = ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Aurora }
        val bundle = ThemeBundle(global = global, deck = deck)
        assertEquals(deck, bundle.resolve(ThemeTarget.Deck))
        assertEquals(global, bundle.resolve(ThemeTarget.Trackpad))
        assertEquals(global[ThemeColorRole.Danger], bundle.semantic(ThemeColorRole.Danger))
    }

    @Test
    fun `foreground derives from actual color and satisfies normal text contrast`() {
        ThemePresetCatalog.presets.forEach { scheme ->
            ThemeColorRole.entries.forEach { role ->
                val background = scheme[role]
                assertTrue(ThemeContrast.ratio(ThemeContrast.readableForeground(background), background) >= 4.5)
            }
        }
    }

    @Test
    fun `editor previews undo resets applies and restores process draft`() {
        val applied = ThemeBundle(ThemePresetCatalog.default)
        val aurora = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Aurora })
        val preview = ThemeEditorState(applied).preview(aurora)
        assertTrue(preview.canUndo)
        assertTrue(preview.canApply)
        assertEquals(applied, preview.undo().draft)
        assertEquals(aurora, ThemeEditorState.restore(applied, preview.save()).draft)
        assertEquals(applied, preview.reset().draft)
        assertFalse(preview.applied().canApply)
    }

    @Test
    fun `editor opacity and scoped edits do not mutate global scheme`() {
        val original = ThemeBundle(ThemePresetCatalog.default)
        val color = requireNotNull(ThemeArgb.parse("#FF123456"))
        val edited = ThemeEditorState(original)
            .edit(ThemeTarget.Trackpad, ThemeColorRole.Grid, color)
            .setOpacity(ThemeTarget.Trackpad, ThemeOpacityRole.Grid, .4f)
        assertEquals(original.global, edited.draft.global)
        assertEquals(color, edited.draft.resolve(ThemeTarget.Trackpad)[ThemeColorRole.Grid])
        assertEquals(.4f, edited.draft.resolve(ThemeTarget.Trackpad).opacity.grid)
        assertNotEquals(original, edited.draft)
    }

    @Test
    fun `local only mode lock preserves chosen theme bundle`() {
        val chosen = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Cyber })
        val settings = CodecksThemeSettings(mode = CodecksThemeMode.Light, themeBundle = chosen)
        val resolved = settings.resolveForCodecksRelease(customizationEnabled = false)
        assertEquals(CodecksThemeMode.Oled, resolved.mode)
        assertEquals(chosen, resolved.themeBundle)
    }

    @Test
    fun `every non-null scope must pass contrast`() {
        val default = ThemePresetCatalog.default
        val unreadable = default.copy(colors = default.colors + (ThemeColorRole.Border to default[ThemeColorRole.Background]))
        assertFalse(ThemeContrast.isBundleReadable(ThemeBundle(default, deck = unreadable)))
        assertFalse(ThemeContrast.isBundleReadable(ThemeBundle(default, trackpad = unreadable)))
        assertTrue(ThemeContrast.isBundleReadable(ThemeBundle(default)))
    }

    @Test
    fun `duplicate creates independent stable identity`() {
        val original = ThemeBundle(ThemePresetCatalog.default)
        val duplicated = ThemeEditorState(original).duplicate(ThemeTarget.Deck, "custom-deck-1", "My deck")
        assertEquals("custom-deck-1", duplicated.draft.resolve(ThemeTarget.Deck).id)
        assertEquals("My deck", duplicated.draft.resolve(ThemeTarget.Deck).label)
        assertEquals(null, duplicated.draft.resolve(ThemeTarget.Deck).preset)
        assertEquals(original.global, duplicated.draft.global)
    }

    @Test
    fun `preset selection changes only selected target`() {
        val original = ThemeBundle(ThemePresetCatalog.default)
        val cyber = ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Cyber }
        val deck = original.withScheme(ThemeTarget.Deck, cyber)
        assertEquals(cyber, deck.resolve(ThemeTarget.Deck))
        assertEquals(original.global, deck.global)
        assertEquals(original.global, deck.resolve(ThemeTarget.Trackpad))
    }

    @Test
    fun `opacity composites actual surfaces without producing transparent output`() {
        val foreground = requireNotNull(ThemeArgb.parse("#FFFFFFFF"))
        val background = requireNotNull(ThemeArgb.parse("#FF000000"))
        assertEquals(background, ThemeColorMath.composite(foreground, background, 0f))
        assertEquals(foreground, ThemeColorMath.composite(foreground, background, 1f))
        assertEquals("#FF7F7F7F", ThemeColorMath.composite(foreground, background, .5f).toHex())
    }

    @Test
    fun `seed generator is deterministic complete and accessible`() {
        val seed = requireNotNull(ThemeArgb.parse("#FF7C4DFF"))
        val first = ThemeTonalGenerator.generate(seed, "custom-seed", "Seed", dark = true)
        val second = ThemeTonalGenerator.generate(seed, "custom-seed", "Seed", dark = true)
        assertEquals(first, second)
        assertEquals(ThemeColorRole.entries.toSet(), first.colors.keys)
        assertTrue(ThemeContrast.isCriticalReadable(first))
    }
}
