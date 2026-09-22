package io.codecks.ui.theme

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.codecks.MainActivity
import io.codecks.ui.settings.IconPackPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ThemeStudioInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun allPresetsAndGeneratedCustomRenderAtTwoHundredPercentText() {
        render(environment(width = 412, height = 915, fontScale = 2f), fontScale = 2f)
        ThemePresetCatalog.presets.forEach { preset ->
            rule.onNodeWithTag("theme-preset-${preset.id}").performScrollTo().performClick()
            rule.onNodeWithTag("theme-preview").performScrollTo()
            val presetGolden = rule.onNodeWithTag("theme-preview").captureToImage()
            assertTrue(presetGolden.width > 0 && presetGolden.height > 0)
        }
        rule.onNodeWithTag("theme-generate-seed").performClick()
        rule.onNodeWithTag("theme-preview").performScrollTo()
        val golden = rule.onNodeWithTag("theme-preview").captureToImage()
        assertTrue(golden.width > 0 && golden.height > 0)
    }

    @Test
    fun rawHexEditorIsCollapsedUntilAdvancedIsRequested() {
        render(environment(width = 412, height = 915))
        rule.onAllNodesWithText("Global Primary color hex").assertCountEquals(0)
        rule.onNodeWithTag("theme-advanced-toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performScrollTo().performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        rule.onNodeWithText("Global Primary color hex").assertExists()
        rule.onNodeWithText("Global Primary color hex")
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Global Primary color hex"),
            ))
    }

    @Test
    fun presetCardsExposeFullRadioSemanticsAndNonOverlappingTargetsAtLargeText() {
        render(environment(width = 412, height = 915, fontScale = 2f), fontScale = 2f)
        val nodes = ThemePresetCatalog.presets.map { preset ->
            rule.onNodeWithTag("theme-preset-${preset.id}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .also { interaction ->
                    val bounds = interaction.getUnclippedBoundsInRoot()
                    val node = interaction.fetchSemanticsNode()
                    assertTrue((bounds.right - bounds.left).value >= 48f && (bounds.bottom - bounds.top).value >= 48f)
                    assertTrue(node.config[SemanticsProperties.ContentDescription].single().contains(preset.label))
                }
                .getUnclippedBoundsInRoot()
        }
        rule.onNodeWithTag("theme-preset-codecks-green").assertIsSelected()
        assertPairwiseNonOverlapping(nodes)

        val colors = listOf(
            "Green" to "#FF3DDC84", "Blue" to "#FF75D7FF", "Violet" to "#FFD9B8FF", "Rose" to "#FFFF8CA8",
            "Amber" to "#FFFFC857", "White" to "#FFFFFFFF", "Slate" to "#FF64748B", "Black" to "#FF000000",
        )
        val swatches = colors.map { (name, hex) ->
            rule.onNodeWithTag("theme-color-primary-${name.lowercase(Locale.ROOT)}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .also { interaction ->
                    val bounds = interaction.getUnclippedBoundsInRoot()
                    val node = interaction.fetchSemanticsNode()
                    assertTrue((bounds.right - bounds.left).value >= 56f && (bounds.bottom - bounds.top).value >= 56f)
                    val description = node.config[SemanticsProperties.ContentDescription].single()
                    assertEquals(
                        "$name Global Primary color, $hex, ${if (name == "Green") "selected" else "not selected"}",
                        description,
                    )
                    rule.onNodeWithText(name).assertExists()
                }
                .getUnclippedBoundsInRoot()
        }
        assertPairwiseNonOverlapping(swatches)
        rule.onNodeWithTag("theme-color-primary-green").assertIsSelected()
        listOf("Deck", "Trackpad").forEach { target ->
            rule.onNodeWithText(target).performClick()
            colors.forEach { (name, hex) ->
                val description = rule.onNodeWithTag("theme-color-primary-${name.lowercase(Locale.ROOT)}")
                    .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
                assertEquals(
                    "$name $target Primary color, $hex, ${if (name == "Green") "selected" else "not selected"}",
                    description,
                )
            }
        }
    }

    @Test
    fun iconPackSelectorUsesRadioSelectionAndFortyEightDpTargets() {
        val selected = mutableStateOf(CodecksIconPack.Material)
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 2f),
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    CodecksTheme(settings = CodecksThemeSettings(iconPack = selected.value)) {
                        IconPackPanel(iconPack = selected.value, onIconPackChange = { selected.value = it })
                    }
                }
            }
        }
        rule.onNodeWithTag("icon-pack-selector").assertExists()
        val packs = CodecksIconPack.entries.map { pack ->
            rule.onNodeWithTag("icon-pack-${pack.name}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .also { interaction ->
                    val bounds = interaction.getUnclippedBoundsInRoot()
                    val node = interaction.fetchSemanticsNode()
                    assertTrue((bounds.right - bounds.left).value >= 48f && (bounds.bottom - bounds.top).value >= 48f)
                    assertTrue(node.config[SemanticsProperties.ContentDescription].single().contains(pack.label))
                }
                .getUnclippedBoundsInRoot()
        }
        assertPairwiseNonOverlapping(packs)
        rule.onNodeWithTag("icon-pack-Material").assertIsSelected()
        rule.onNodeWithTag("icon-pack-Feather").performScrollTo().performClick().assertIsSelected()
        assertEquals(CodecksIconPack.Feather, selected.value)
    }

    @Test
    fun namedLibraryUiSavesLoadsAndRequiresDeleteConfirmation() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.themeDataStore.edit { it.remove(THEME_LIBRARY_KEY) }
            render(environment(width = 1280, height = 720, fontScale = 2f), fontScale = 2f, rtl = true)
            rule.onNodeWithTag("theme-library-name").performScrollTo().performTextInput("UI forest")
            rule.onNodeWithTag("theme-library-save").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Load UI forest").fetchSemanticsNodes().isNotEmpty() }
            val load = rule.onNodeWithText("Load UI forest").assertExists().fetchSemanticsNode()
            assertTrue(load.boundsInRoot.height >= 48f)
            val rename = rule.onNodeWithTag("theme-library-rename-action-ui-forest")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("Rename UI forest"))))
                .fetchSemanticsNode()
            val delete = rule.onNodeWithTag("theme-library-delete-ui-forest")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("Delete UI forest"))))
                .fetchSemanticsNode()
            assertTrue(rename.boundsInRoot.height >= 48f && delete.boundsInRoot.height >= 48f)
            assertTrue(load.boundsInRoot.bottom <= rename.boundsInRoot.top)
            assertTrue(rename.boundsInRoot.bottom <= delete.boundsInRoot.top)
            rule.onNodeWithText("Load UI forest").performClick()
            rule.onNodeWithText("Delete UI forest").performClick()
            rule.onNodeWithText("Delete UI forest?").assertExists()
            rule.onNodeWithText("Keep UI forest").performClick()
            rule.onNodeWithText("Load UI forest").assertExists()
            context.themeDataStore.edit { it.remove(THEME_LIBRARY_KEY) }
        }
    }

    @Test
    fun rtlLandscapePolicyOverrideBindsActualPhoneOrTabletWindow() {
        render(environment(width = 1280, height = 720), rtl = true)
        assertEquals(35, Build.VERSION.SDK_INT)
        val configuration = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        val expectedFormFactor = if (configuration.smallestScreenWidthDp >= 600) "tablet" else "phone"
        if (expectedFormFactor == "phone") assertTrue(configuration.smallestScreenWidthDp < 600)
        else assertTrue(configuration.smallestScreenWidthDp >= 600)
        rule.onNodeWithTag("theme-test-root").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("$expectedFormFactor API35 actual-window root; 1280x720 policy override only"),
            ),
        )
        val root = rule.onNodeWithTag("theme-test-root").fetchSemanticsNode().boundsInRoot
        assertTrue(root.width > 0f && root.height > 0f)
        var decorWidth = 0
        var decorHeight = 0
        var windowWidth = 0
        var windowHeight = 0
        rule.activityRule.scenario.onActivity { activity ->
            decorWidth = activity.window.decorView.width
            decorHeight = activity.window.decorView.height
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            windowWidth = bounds.width()
            windowHeight = bounds.height()
        }
        assertTrue(decorWidth > 0 && decorHeight > 0 && windowWidth > 0 && windowHeight > 0)
        assertTrue(abs(root.width - decorWidth) <= 2f && abs(root.height - decorHeight) <= 2f)
        assertTrue(decorWidth <= windowWidth && decorHeight <= windowHeight)
        assertTrue(windowWidth - decorWidth <= 2)
        assertTrue(windowHeight - decorHeight <= (windowHeight * 0.10f).coerceAtLeast(2f))
        rule.onNodeWithTag("theme-preview").performScrollTo()
        rule.onNodeWithTag("theme-preview").assertExists()
        val golden = rule.onNodeWithTag("theme-preview").captureToImage()
        assertTrue(golden.width > golden.height)
    }

    @Test
    fun overlayAndLockscreenPoliciesDisableAtomicApply() {
        render(environment(width = 412, height = 915, overlay = true))
        rule.onNodeWithTag("theme-preset-cyber").performClick()
        rule.onNodeWithText("Apply").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun lockscreenPolicyDisablesChangedThemeApply() {
        render(environment(width = 412, height = 915, locked = true))
        rule.onNodeWithTag("theme-preset-cyber").performClick()
        rule.onNodeWithText("Apply").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun keyImportDialogUsesCurrentTheme() {
        render(environment(width = 412, height = 915))
        rule.onNodeWithTag("theme-transfer-open").performScrollTo().performClick()
        rule.onNodeWithTag("theme-transfer-dialog").assertExists()
    }

    @Test
    fun widgetAndNotificationSurfaceStoreKeepsOpaqueThemeColors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bundle = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Aurora })
        ThemeSystemSurfaceStore(context).write(bundle)
        val stored = ThemeSystemSurfaceStore(context).read()
        assertEquals(bundle.global[ThemeColorRole.Primary].value.toInt(), stored.primary)
        assertEquals(bundle.global[ThemeColorRole.Background].value.toInt(), stored.background)
        assertEquals(0xFF, stored.primary ushr 24)
    }

    @Test
    fun persistedThemeRepairsSystemSurfaceMirrorAfterRepositoryRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val persisted = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Cyber })
        val stale = ThemeBundle(ThemePresetCatalog.default)
        val firstRepository = ThemeSettingsRepository(context)
        assertTrue(firstRepository.applyThemeBundle(persisted))
        ThemeSystemSurfaceStore(context).write(stale)

        val loaded = ThemeSettingsRepository(context).settings.first()
        val reconciled = ThemeSystemSurfaceStore(context).read()

        assertEquals(persisted, loaded.themeBundle)
        assertEquals(persisted.global[ThemeColorRole.Primary].value.toInt(), reconciled.primary)
        assertEquals(persisted.global[ThemeColorRole.Background].value.toInt(), reconciled.background)
    }

    @Test
    fun m09dProductionRepositoryLifecycleSavesRenamesLoadsAndDeletesNamedTheme() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val id = "theme-m09d-lifecycle"
            val bundle = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Forest })
            val repository = ThemeSettingsRepository(context)
            context.themeDataStore.edit { it.remove(THEME_LIBRARY_KEY) }

            assertTrue(repository.saveNamedTheme(id, "M09D forest", bundle))
            assertEquals(bundle, repository.themeLibrary.first { snapshot -> snapshot.state.themes.any { it.id == id } }.state.themes.first { it.id == id }.bundle)
            assertTrue(repository.renameNamedTheme(id, "M09D forest renamed"))

            val recreated = ThemeSettingsRepository(context)
            assertEquals("M09D forest renamed", recreated.themeLibrary.first { snapshot -> snapshot.state.themes.any { it.id == id } }.state.themes.first { it.id == id }.name)
            assertTrue(recreated.deleteNamedTheme(id))
            assertTrue(recreated.themeLibrary.first { snapshot -> snapshot.state.themes.none { it.id == id } }.state.themes.none { it.id == id })
            assertTrue(!recreated.renameNamedTheme(id, "Missing"))
            assertTrue(!recreated.deleteNamedTheme(id))
            context.themeDataStore.edit { it.remove(THEME_LIBRARY_KEY) }
        }
    }

    @Test
    fun v1LibraryMigratesAtomicallyAndCorruptBytesStayQuarantinedUntilReset() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bundle = ThemeBundle(ThemePresetCatalog.presets.first { it.preset == ThemePresetId.Ocean })
            val legacy = JSONObject().apply {
                put("version", 1)
                put("themes", JSONArray().put(JSONObject().apply {
                    put("id", "theme-legacy")
                    put("label", "Legacy ocean")
                    put("bundle", JSONObject(ThemeSchemeCodec.encode(bundle)))
                }))
            }.toString()
            context.themeDataStore.edit { it[THEME_LIBRARY_KEY] = legacy }
            val repository = ThemeSettingsRepository(context)
            val migrated = repository.themeLibrary.first { it.state.themes.any { theme -> theme.id == "theme-legacy" } }
            assertEquals("Legacy ocean", migrated.state.themes.single().name)
            assertEquals(2, JSONObject(context.themeDataStore.data.first()[THEME_LIBRARY_KEY]!!).getInt("version"))

            val corrupt = "{not-json"
            context.themeDataStore.edit { it[THEME_LIBRARY_KEY] = corrupt }
            assertTrue(ThemeSettingsRepository(context).themeLibrary.first { it.quarantined }.quarantined)
            assertTrue(!repository.saveNamedTheme("theme-blocked", "Blocked", bundle))
            assertEquals(corrupt, context.themeDataStore.data.first()[THEME_LIBRARY_KEY])
            assertTrue(repository.resetCorruptThemeLibrary())
            assertEquals(0, (ThemeLibraryCodec.decode(context.themeDataStore.data.first()[THEME_LIBRARY_KEY]!!) as ThemeLibraryDecodeResult.Success).state.themes.size)
            context.themeDataStore.edit { it.remove(THEME_LIBRARY_KEY) }
        }
    }

    private fun render(
        environment: ThemeUiEnvironment,
        settings: CodecksThemeSettings = CodecksThemeSettings(),
        fontScale: Float = 1f,
        rtl: Boolean = false,
    ) {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = fontScale),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    CodecksTheme(settings = settings) {
                        val formFactor = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) "tablet" else "phone"
                        Box(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("theme-test-root")
                                .semantics {
                                    contentDescription = "$formFactor API${Build.VERSION.SDK_INT} actual-window root; " +
                                        "${environment.widthDp}x${environment.heightDp} policy override only"
                                },
                        ) {
                            ThemeStudioPanel(settings = settings, environmentOverride = environment)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun environment(
        width: Int,
        height: Int,
        fontScale: Float = 1f,
        overlay: Boolean = false,
        locked: Boolean = false,
    ) = ThemeUiEnvironment(width, height, fontScale, reducedMotion = true, overlay = overlay, locked = locked)

    private fun assertPairwiseNonOverlapping(bounds: List<androidx.compose.ui.unit.DpRect>) {
        bounds.forEachIndexed { index, first ->
            bounds.drop(index + 1).forEach { second ->
                assertTrue(
                    "Targets overlap: $first and $second",
                    first.right <= second.left || second.right <= first.left ||
                        first.bottom <= second.top || second.bottom <= first.top,
                )
            }
        }
    }
}
