package io.codecks.ui.mouse

internal enum class TrackpadQuickTray {
    Custom,
    Dynamic,
    Settings,
}

internal enum class MouseInputMode(val label: String) {
    Trackpad("Trackpad"),
    AirMouse("Air"),
    AirTouch("Air Touch"),
}

internal enum class ScrollRailDirection(val label: String, val sign: Int) {
    Direct("Direct", 1),
    Inverted("Invert", -1),
}

internal enum class ScrollRailOrientation {
    Vertical,
    Horizontal,
}
