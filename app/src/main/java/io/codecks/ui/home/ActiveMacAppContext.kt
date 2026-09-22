package io.codecks.ui.home

internal data class ActiveMacAppContext(
    val bundleId: String,
    val appName: String,
)

internal fun parseActiveMacAppContext(output: String): ActiveMacAppContext? {
    val lines = output.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    if (lines.size != 2) return null
    val bundleId = lines[0]
    val appName = lines[1]
    if (!bundleId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{1,127}")) || ".." in bundleId) return null
    if (appName.isBlank() || appName.length > 96 || appName.any { it.isISOControl() }) return null
    return ActiveMacAppContext(bundleId, appName)
}

internal const val ACTIVE_MAC_APP_CONTEXT_COMMAND: String =
    "/usr/bin/osascript -l JavaScript -e 'const se=Application(\"System Events\"); const p=se.applicationProcesses.whose({frontmost:true})[0]; [p.bundleIdentifier(),p.name()].join(\"\\n\")'"
