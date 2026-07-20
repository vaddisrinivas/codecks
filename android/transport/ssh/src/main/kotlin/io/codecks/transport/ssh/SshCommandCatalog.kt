package io.codecks.transport.ssh

import io.codecks.domain.actions.ActionPlan
import java.net.URI

interface SshCommandCatalog {
    fun commandFor(plan: ActionPlan, parameters: Map<String, String> = emptyMap()): String?
}

object CoreSshCommandCatalog : SshCommandCatalog {
    override fun commandFor(plan: ActionPlan, parameters: Map<String, String>): String? =
        when (plan.steps.singleOrNull()?.stableId) {
            "mac.finder.open" -> "open -a Finder"
            "mac.terminal.open" -> "open -a Terminal"
            "mac.spotlight.open" -> "osascript -e 'tell application \"System Events\" to key code 49 using command down'"
            "mac.mission-control.show" -> "open -b com.apple.exposelauncher"
            "mac.space.left" -> "osascript -e 'tell application \"System Events\" to key code 123 using control down'"
            "mac.space.right" -> "osascript -e 'tell application \"System Events\" to key code 124 using control down'"
            "mac.window.full-screen" -> "osascript -e 'tell application \"System Events\" to key code 3 using {control down, command down}'"
            "mac.app.previous" -> "osascript -e 'tell application \"System Events\" to key code 48 using {command down, shift down}'"
            "mac.app.next" -> "osascript -e 'tell application \"System Events\" to key code 48 using command down'"
            "mac.browser.new-tab" -> "osascript -e 'tell application \"System Events\" to key code 17 using command down'"
            "mac.lock" -> "/System/Library/CoreServices/Menu\\ Extras/User.menu/Contents/Resources/CGSession -suspend"
            "mac.url.open" -> openUrlCommand(parameters["url"])
            else -> null
        }

    private fun openUrlCommand(rawUrl: String?): String? {
        val uri = rawUrl?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        return "open ${ShellQuoter.single(rawUrl)}"
    }
}

object ShellQuoter {
    fun single(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}
