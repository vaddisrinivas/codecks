package io.codecks.domain.reactive

private const val MaxShortcutNameChars = 120
private const val MaxShortcutOutputBytes = 64 * 1024
private const val MaxShortcutTimeoutMillis = 30_000L
private const val MaxDisplayIdChars = 96
private const val MaxAccessibilityActions = 20

data class AppleShortcutsCliRequest(
    val shortcutName: String,
    val inputPath: String? = null,
    val timeoutMillis: Long = 10_000L,
    val maxOutputBytes: Int = MaxShortcutOutputBytes,
) {
    init {
        require(shortcutName.isSafeSingleLine(MaxShortcutNameChars)) { "Shortcut name is invalid." }
        require(inputPath == null || SpotlightSftpPolicy.isSafeAbsolutePath(inputPath)) {
            "Shortcut input path is invalid."
        }
        require(timeoutMillis in 500L..MaxShortcutTimeoutMillis) { "Shortcut timeout is invalid." }
        require(maxOutputBytes in 1..MaxShortcutOutputBytes) { "Shortcut output limit is invalid." }
    }

    fun argv(): List<String> = buildList {
        add("/usr/bin/shortcuts")
        add("run")
        add(shortcutName)
        if (inputPath != null) {
            add("--input-path")
            add(inputPath)
        }
    }
}

data class MonitorBrightnessRequest(
    val displayId: String,
    val percent: Int,
    val provenance: ReactiveRequestProvenance,
    val adapterId: String,
) {
    init {
        require(displayId.isSafeSingleLine(MaxDisplayIdChars)) { "Display id is invalid." }
        require(adapterId.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) { "Brightness adapter id is invalid." }
        require(percent in 0..100) { "Brightness percent must be 0..100." }
    }
}

data class AccessibilityDiscoveryRequest(
    val frontAppBundleId: String,
    val provenance: ReactiveRequestProvenance,
    val maxActions: Int = 8,
) {
    init {
        require(frontAppBundleId.isSafeBundleId()) { "Front app bundle id is invalid." }
        require(maxActions in 1..MaxAccessibilityActions) { "Accessibility action cap is invalid." }
    }
}

private fun String.isSafeSingleLine(maxChars: Int): Boolean =
    isNotBlank() &&
        length <= maxChars &&
        none { it.isISOControl() || it == '\u0000' }

private fun String.isSafeBundleId(): Boolean =
    matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{1,127}")) &&
        !contains("..")
