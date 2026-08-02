package io.codecks.ui.app

enum class ShellKey {
    Tab,
    Enter,
    Space,
    Back,
    Escape,
    Other,
}

enum class ShellKeyAction {
    FocusNext,
    FocusPrevious,
    ActivateFocused,
    NavigateBack,
    ExitFullscreen,
    PassThrough,
}

fun shellKeyAction(
    key: ShellKey,
    shiftPressed: Boolean,
    fullscreen: Boolean,
    canNavigateBack: Boolean,
): ShellKeyAction = when (key) {
    ShellKey.Tab -> if (shiftPressed) ShellKeyAction.FocusPrevious else ShellKeyAction.FocusNext
    ShellKey.Enter,
    ShellKey.Space,
    -> ShellKeyAction.ActivateFocused
    ShellKey.Back,
    ShellKey.Escape,
    -> when {
        fullscreen -> ShellKeyAction.ExitFullscreen
        canNavigateBack -> ShellKeyAction.NavigateBack
        else -> ShellKeyAction.PassThrough
    }
    ShellKey.Other -> ShellKeyAction.PassThrough
}

enum class ShellNavigationMode {
    BottomBar,
    Rail,
}

data class ShellAccessibilityLayout(
    val navigationMode: ShellNavigationMode,
    val navigationVisible: Boolean,
    val stopInputVisible: Boolean,
)

fun shellAccessibilityLayout(
    widthDp: Int,
    heightDp: Int,
    fontScale: Float,
    fullscreen: Boolean,
): ShellAccessibilityLayout {
    require(widthDp > 0 && heightDp > 0)
    require(fontScale.isFinite() && fontScale > 0f)
    return ShellAccessibilityLayout(
        navigationMode = if (widthDp >= 840 && !accessibilityReflowPolicy(fontScale).stackControls) {
            ShellNavigationMode.Rail
        } else {
            ShellNavigationMode.BottomBar
        },
        navigationVisible = !fullscreen,
        stopInputVisible = fullscreen,
    )
}
