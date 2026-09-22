package io.codecks.ui.theme

data class ThemeUiEnvironment(
    val widthDp: Int,
    val heightDp: Int,
    val fontScale: Float,
    val reducedMotion: Boolean,
    val overlay: Boolean,
    val locked: Boolean,
) {
    init {
        require(widthDp > 0 && heightDp > 0)
        require(fontScale > 0f)
    }
}

data class ThemeUiAdaptation(
    val editorColumns: Int,
    val animationsEnabled: Boolean,
    val editingAllowed: Boolean,
    val wideLayout: Boolean,
)

object ThemeUiPolicy {
    fun resolve(environment: ThemeUiEnvironment): ThemeUiAdaptation {
        val wide = environment.widthDp >= 600
        return ThemeUiAdaptation(
            editorColumns = if (environment.fontScale >= 2f || environment.widthDp < 480) 1 else 2,
            animationsEnabled = !environment.reducedMotion,
            editingAllowed = !environment.overlay && !environment.locked,
            wideLayout = wide,
        )
    }
}

internal fun themeAnimationDurationMillis(adaptation: ThemeUiAdaptation): Int =
    if (adaptation.animationsEnabled) 180 else 0

internal fun themePreviewWidthFraction(adaptation: ThemeUiAdaptation): Float =
    if (adaptation.wideLayout) 0.72f else 1f
