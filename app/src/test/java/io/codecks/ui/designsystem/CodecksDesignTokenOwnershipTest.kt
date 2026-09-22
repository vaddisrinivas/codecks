package io.codecks.ui.designsystem

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecksDesignTokenOwnershipTest {
    @Test
    fun `semantic token owner defines the complete M09A taxonomy`() {
        val root = sourceRoot()
        val core = File(root, "app/src/main/java/io/codecks/core/design/CoreDesign.kt").readText()
        val material = File(root, "app/src/main/java/io/codecks/ui/designsystem/CodecksMaterialTokens.kt").readText()

        listOf("Spacing", "Shape", "Stroke", "Elevation", "Grid", "Motion", "Opacity", "Focus", "StateLayer")
            .forEach { assertTrue("missing token group $it", core.contains("object $it")) }
        listOf("success", "danger", "connected", "selected", "warning", "focus")
            .forEach { assertTrue("missing semantic color $it", material.contains("val $it: Color")) }
        assertTrue(core.contains("enum class CodecksHapticToken"))
        assertFalse("semantic color owner must use the active M09C theme", material.contains("Color(0x"))
    }

    @Test
    fun `feature surfaces consume tokens without owning palettes typography or dimensions`() {
        val root = sourceRoot()
        val lockscreen = File(root, "app/src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadScreen.kt").readText()
        val overlayFile = File(root, "app/src/main/java/io/codecks/AppDestinationSupport.kt").readText()
        val overlay = overlayFile.substringAfter("internal fun CelebrationOverlay").substringBefore("internal fun KeyboardDestination")
        val helperFile = File(root, "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt").readText()
        val helper = helperFile.substringAfter("internal fun CodecksHelperPanel").substringBefore("internal fun MacConnectionSettingsPanel")
        val widget = File(root, "app/src/main/res/layout/trackpad_widget.xml").readText()

        listOf(lockscreen, overlay, helper).forEach { source ->
            assertFalse(source.contains("Color(0x"))
            assertFalse(source.contains("fontSize ="))
            assertFalse(Regex("""\b\d+(?:\.\d+)?\.(dp|sp)\b""").containsMatchIn(source))
        }
        assertFalse(widget.contains("#"))
        assertFalse(Regex("""\"\d+(?:\.\d+)?(dp|sp)\"""").containsMatchIn(widget))
        assertTrue(lockscreen.contains("CodecksDesignTokens.Size.minTouchTarget"))
        assertTrue(helper.contains("CodecksDesignTokens.Size.minTouchTarget"))
        assertTrue(overlay.contains("LocalCodecksMotionPolicy"))
    }

    @Test
    fun `core controls use semantic state colors and reduced motion policy`() {
        val root = sourceRoot()
        val deck = File(root, "app/src/main/java/io/codecks/ui/designsystem/DeckComponents.kt").readText()
        val key = File(root, "app/src/main/java/io/codecks/ui/designsystem/CkDeckKey.kt").readText()
        val theme = File(root, "app/src/main/java/io/codecks/ui/theme/CodecksTheme.kt").readText()

        assertTrue(deck.contains("codecksSemanticColorTokens()"))
        assertTrue(deck.contains("LocalCodecksMotionPolicy.current"))
        assertTrue(key.contains("codecksSemanticColorTokens()"))
        assertTrue(key.contains("motionPolicy.allowsContinuousMotion"))
        assertTrue(theme.contains("LocalCodecksMotionPolicy provides motionPolicy"))
        assertTrue(theme.contains("CodecksMaterialTypography"))
        assertTrue(theme.contains("rememberCodecksMotionPolicy()"))
        assertFalse(theme.contains("remember { CodecksMotionPolicy"))
        assertTrue(materialSource(root).contains("registerContentObserver"))
        assertTrue(deck.contains("performCodecksHaptic(CodecksHapticToken.Confirm)"))
        assertTrue(deck.contains("CodecksDesignTokens.Focus.ringWidth"))
        assertTrue(key.contains("performCodecksHaptic(CodecksHapticToken.LongPress)"))
    }

    @Test
    fun `deck surfaces cannot bypass the semantic token owner`() {
        val root = sourceRoot()
        val sources = listOf("CkDeckKey.kt", "DeckComponents.kt", "CodecksDeckSurface.kt")
            .associateWith { File(root, "app/src/main/java/io/codecks/ui/designsystem/$it").readText() }
        val rawPalette = Regex("""Color(?:\.(?:White|Black|Transparent)|\(0x)""")
        val rawDimension = Regex("""\b\d+(?:\.\d+)?\.dp\b""")
        val rawAlpha = Regex("""alpha\s*=\s*\d""")
        sources.forEach { (name, source) ->
            assertFalse("$name owns a raw palette", rawPalette.containsMatchIn(source))
            assertFalse("$name owns a raw dimension", rawDimension.containsMatchIn(source))
            assertFalse("$name owns a raw alpha", rawAlpha.containsMatchIn(source))
        }
        val deck = sources.getValue("DeckComponents.kt")
        listOf("FlatDeckTileColors", "playfulDeckTileColors", "DeckButtonMaterialTokens", "CodecksDeckSurfaceTokens")
            .forEach { assertFalse("parallel taxonomy $it", sources.values.any { source -> source.contains(it) }) }
        assertTrue(deck.contains("codecksDeckTileVisual("))
    }

    @Test
    fun `home deck and trackpad cannot bypass typed visual tokens`() {
        val root = sourceRoot()
        val sources = listOf(
            "app/src/main/java/io/codecks/ui/home/HomeDeckSections.kt",
            "app/src/main/java/io/codecks/ui/mouse/TrackpadSurface.kt",
        ).associateWith { File(root, it).readText() }
        val forbidden = listOf(
            Regex("""Color(?:\.(?:White|Black|Transparent)|\(0x)"""),
            Regex("""\b\d+(?:\.\d+)?\.dp\b"""),
            Regex("""alpha\s*=\s*\d"""),
        )
        sources.forEach { (name, source) ->
            forbidden.forEach { pattern -> assertFalse("$name bypasses tokens: $pattern", pattern.containsMatchIn(source)) }
            assertTrue("$name does not consume CodecksDesignTokens", source.contains("CodecksDesignTokens"))
        }
    }

    @Test
    fun `deck focus observer precedes focus target`() {
        val root = sourceRoot()
        listOf("CkDeckKey.kt", "DeckComponents.kt").forEach { name ->
            val source = File(root, "app/src/main/java/io/codecks/ui/designsystem/$name").readText()
            assertTrue("$name focus ordering", Regex("""\.onFocusChanged[\s\S]*?\.focusable""").containsMatchIn(source))
        }
    }

    @Test
    fun `widget consumes mirrored semantic colors and resource tokens`() {
        val root = sourceRoot()
        val provider = File(root, "app/src/main/java/io/codecks/widget/TrackpadWidgetProvider.kt").readText()
        val store = File(root, "app/src/main/java/io/codecks/ui/theme/ThemeSystemSurfaceStore.kt").readText()

        assertTrue(provider.contains("setTextColor(R.id.trackpad_widget_title, theme.content)"))
        assertTrue(provider.contains("setTextColor(R.id.trackpad_widget_detail, theme.content)"))
        assertTrue(store.contains("ThemeContrast.readableForeground"))
        val layout = File(root, "app/src/main/res/layout/trackpad_widget.xml").readText()
        val tokens = File(root, "app/src/main/res/values/design_tokens.xml").readText()
        assertTrue(layout.contains("@color/codecks_widget_canvas_fallback"))
        assertTrue(layout.contains("@color/codecks_widget_content_fallback"))
        assertTrue(tokens.contains("codecks_widget_canvas_fallback"))
    }

    private fun materialSource(root: File) =
        File(root, "app/src/main/java/io/codecks/ui/designsystem/CodecksMaterialTokens.kt").readText()

    private fun sourceRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/java/io/codecks/ui/designsystem/CodecksMaterialTokens.kt").isFile }
}
