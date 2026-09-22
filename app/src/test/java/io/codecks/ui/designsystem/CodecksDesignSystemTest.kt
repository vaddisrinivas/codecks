package io.codecks.ui.designsystem

import io.codecks.core.design.CodecksDesignTokens
import io.codecks.ui.theme.ThemeColorRole
import io.codecks.ui.theme.ThemeContrast
import io.codecks.ui.theme.ThemePresetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecksDesignSystemTest {
    @Test
    fun `touch shape elevation grid and focus tokens are ordered and accessible`() {
        assertTrue(CodecksDesignTokens.Size.minTouchTarget.value >= 48f)
        assertTrue(CodecksDesignTokens.Size.controlTileMinHeight >= CodecksDesignTokens.Size.minTouchTarget)
        assertTrue(CodecksDesignTokens.Shape.extraSmall < CodecksDesignTokens.Shape.small)
        assertTrue(CodecksDesignTokens.Shape.small < CodecksDesignTokens.Shape.medium)
        assertTrue(CodecksDesignTokens.Shape.medium < CodecksDesignTokens.Shape.large)
        assertTrue(CodecksDesignTokens.Shape.large < CodecksDesignTokens.Shape.extraLarge)
        assertTrue(CodecksDesignTokens.Elevation.flat < CodecksDesignTokens.Elevation.low)
        assertTrue(CodecksDesignTokens.Grid.compactGap < CodecksDesignTokens.Grid.standardGap)
        assertTrue(CodecksDesignTokens.Focus.ringWidth >= CodecksDesignTokens.Stroke.focus)
    }

    @Test
    fun `reduced motion resolves transitions to instant and stops continuous motion`() {
        val normal = CodecksMotionPolicy(reducedMotion = false)
        val reduced = CodecksMotionPolicy(reducedMotion = true)

        assertEquals(CodecksDesignTokens.Motion.stateChangeMillis, normal.duration(CodecksDesignTokens.Motion.stateChangeMillis))
        assertEquals(CodecksDesignTokens.Motion.instantMillis, reduced.duration(CodecksDesignTokens.Motion.stateChangeMillis))
        assertTrue(normal.allowsContinuousMotion)
        assertTrue(!reduced.allowsContinuousMotion)
    }

    @Test
    fun `typography remains scalable with explicit readable line heights`() {
        listOf(
            CodecksMaterialTypography.headlineSmall,
            CodecksMaterialTypography.titleLarge,
            CodecksMaterialTypography.titleMedium,
            CodecksMaterialTypography.bodyLarge,
            CodecksMaterialTypography.bodyMedium,
            CodecksMaterialTypography.labelLarge,
        ).forEach { style ->
            assertTrue(style.fontSize.isSp)
            assertTrue(style.lineHeight.isSp)
            assertTrue(style.lineHeight.value >= style.fontSize.value)
        }
    }

    @Test
    fun `all offline themes preserve text and semantic feedback contrast`() {
        ThemePresetCatalog.presets.forEach { scheme ->
            assertTrue("${scheme.id} critical contrast", ThemeContrast.isCriticalReadable(scheme))
            listOf(
                ThemeColorRole.Background,
                ThemeColorRole.Surface,
                ThemeColorRole.Primary,
                ThemeColorRole.Success,
                ThemeColorRole.Warning,
                ThemeColorRole.Danger,
            ).forEach { role ->
                assertTrue(
                    "${scheme.id} $role foreground contrast",
                    ThemeContrast.ratio(ThemeContrast.readableForeground(scheme[role]), scheme[role]) >= 4.5,
                )
            }
        }
    }
}
