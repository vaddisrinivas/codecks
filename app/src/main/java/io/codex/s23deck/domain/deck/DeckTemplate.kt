package io.codex.s23deck.domain.deck

import io.codex.s23deck.domain.ActionIcon

data class DeckTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ActionIcon,
    val actionIds: List<String>,
    val appMatchers: List<String> = emptyList(),
)

object DeckTemplateCatalog {
    val templates: List<DeckTemplate> = listOf(
        DeckTemplate(
            id = "browser",
            title = "Browser",
            subtitle = "Tabs, navigation, reload, developer tools",
            icon = ActionIcon.Browser,
            actionIds = listOf(
                "new_tab", "tab_left", "tab_right", "reload",
                "browser_back", "browser_forward", "dev_tools", "chatgpt",
                "copy", "paste", "screenshot", "add_button",
            ),
            appMatchers = listOf("Safari", "Chrome", "Arc", "Firefox", "Brave", "Edge"),
        ),
        DeckTemplate(
            id = "media",
            title = "Media",
            subtitle = "Playback, volume, screen and session control",
            icon = ActionIcon.Play,
            actionIds = listOf(
                "play_pause", "next_track", "prev_track", "mute",
                "vol_up", "vol_down", "screensaver", "sleep_display",
                "control_center", "notification_center", "full_screen", "add_button",
            ),
            appMatchers = listOf("Music", "Spotify", "YouTube", "QuickTime", "TV"),
        ),
        DeckTemplate(
            id = "meetings",
            title = "Meetings",
            subtitle = "Calendar, notes, mute, screenshots, focus",
            icon = ActionIcon.Apps,
            actionIds = listOf(
                "calendar", "gmail", "meeting_start", "mute",
                "screenshot", "focus_1h", "copy", "paste",
                "add_button",
            ),
            appMatchers = listOf("zoom", "meet", "teams", "webex", "facetime"),
        ),
        DeckTemplate(
            id = "window_manager",
            title = "Window Manager",
            subtitle = "Mission Control, Spaces, desktop, app switching",
            icon = ActionIcon.Apps,
            actionIds = listOf(
                "mission", "space_left", "space_right", "show_desktop",
                "full_screen", "next_app", "prev_app", "control_center",
                "notification_center", "menu_bar_focus", "finder",
                "add_button",
            ),
            appMatchers = listOf("Finder", "System Settings"),
        ),
        DeckTemplate(
            id = "developer",
            title = "Developer",
            subtitle = "Terminal, GitHub, AI, browser and diagnostics",
            icon = ActionIcon.Terminal,
            actionIds = listOf(
                "terminal", "github", "dev_tools", "chatgpt",
                "claude", "browser_deck", "coding_start", "screenshot",
                "copy", "paste", "add_button",
            ),
            appMatchers = listOf("Code", "Cursor", "Xcode", "Terminal", "iTerm", "Android Studio"),
        ),
        DeckTemplate(
            id = "presentation",
            title = "Presentation",
            subtitle = "Fullscreen, screenshots, media and navigation",
            icon = ActionIcon.Screenshot,
            actionIds = listOf(
                "full_screen", "play_pause", "space_left", "space_right",
                "screenshot", "mute", "vol_up", "vol_down",
                "add_button",
            ),
            appMatchers = listOf("Keynote", "PowerPoint", "Preview", "Slides"),
        ),
    )

    fun matchActiveApp(activeApp: String): DeckTemplate? {
        if (activeApp.isBlank()) return null
        return templates.firstOrNull { template ->
            template.appMatchers.any { matcher -> activeApp.contains(matcher, ignoreCase = true) }
        }
    }
}
