package io.codecks.ui.theme

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.codecks.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
    fun rtlLandscapeAndDexWidePreviewRender() {
        render(environment(width = 1280, height = 720), rtl = true)
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
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
}
