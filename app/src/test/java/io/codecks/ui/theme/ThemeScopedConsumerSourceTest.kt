package io.codecks.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeScopedConsumerSourceTest {
    @Test
    fun `deck and trackpad render through actual scoped theme consumers`() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java/io/codecks/ui/home/HomeScreen.kt").isFile }
        val deck = File(root, "app/src/main/java/io/codecks/ui/home/HomeScreen.kt").readText()
        val trackpad = File(root, "app/src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()
        assertTrue(deck.contains("CodecksScopedTheme(ThemeTarget.Deck)"))
        assertTrue(trackpad.contains("ThemeTarget.Trackpad else ThemeTarget.Global"))
    }

    @Test
    fun `opacity semantics and environment policy are consumed by runtime UI`() {
        val root = sourceRoot()
        val theme = File(root, "app/src/main/java/io/codecks/ui/theme/CodecksTheme.kt").readText()
        val studio = File(root, "app/src/main/java/io/codecks/ui/theme/ThemeStudioPanel.kt").readText()
        assertTrue(theme.contains("scheme.opacity.grid else scheme.opacity.surface"))
        assertTrue(studio.contains("TYPE_APPLICATION_OVERLAY"))
        assertTrue(studio.contains("adaptation.animationsEnabled"))
        assertTrue(studio.contains("themePreviewWidthFraction(adaptation)"))
    }

    @Test
    fun `success warning and glow roles have concrete consumers`() {
        val root = sourceRoot()
        val home = File(root, "app/src/main/java/io/codecks/ui/home/HomeScreen.kt").readText()
        val surface = File(root, "app/src/main/java/io/codecks/ui/designsystem/CodecksDeckSurface.kt").readText()
        assertTrue(home.contains("LocalCodecksSemanticColors.current.success"))
        assertTrue(home.contains("LocalCodecksSemanticColors.current.warning"))
        assertTrue(surface.contains("LocalCodecksSemanticColors.current.glow"))
        assertTrue(surface.contains("sideGlowAlpha * glowStrength"))
    }

    @Test
    fun `system widget notification rtl and observed guards consume theme state`() {
        val root = sourceRoot()
        val widget = File(root, "app/src/main/java/io/codecks/widget/TrackpadWidgetProvider.kt").readText()
        val notification = File(root, "app/src/main/java/io/codecks/HidSessionService.kt").readText()
        val studio = File(root, "app/src/main/java/io/codecks/ui/theme/ThemeStudioPanel.kt").readText()
        assertTrue(widget.contains("ThemeSystemSurfaceStore(context).read()"))
        assertTrue(widget.contains("setBackgroundColor"))
        assertTrue(notification.contains("setColor(ThemeSystemSurfaceStore(this).read().primary)"))
        assertTrue(notification.contains("ThemeActiveNotificationRegistry.registerAndRefresh(themeNotificationRefresher)"))
        assertTrue(notification.contains("ThemeActiveNotificationRegistry.unregister(themeNotificationRefresher)"))
        assertTrue(notification.contains("notify(NOTIFICATION_ID, buildNotification())"))
        val repository = File(root, "app/src/main/java/io/codecks/ui/theme/ThemeSettingsRepository.kt").readText()
        assertTrue(repository.contains("systemSurfaces.reconcilePersisted(loaded.themeBundle)"))
        assertTrue(repository.contains("systemSurfaces.propagateApplied(bundle)"))
        val propagation = File(root, "app/src/main/java/io/codecks/ui/theme/ThemeSystemSurfaceStore.kt").readText()
        assertTrue(propagation.contains("ThemeActiveNotificationRegistry::refreshIfActive"))
        assertTrue(propagation.contains("startService(").not())
        assertTrue(studio.contains("LifecycleEventObserver"))
        assertTrue(studio.contains("LocalLayoutDirection.current == LayoutDirection.Rtl"))
    }

    private fun sourceRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/java/io/codecks/ui/home/HomeScreen.kt").isFile }
}
