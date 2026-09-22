package io.codecks.data.routines

import io.codecks.domain.routines.RoutineBank
import io.codecks.domain.routines.RoutineCategory
import io.codecks.domain.routines.RoutineDefinition
import io.codecks.domain.routines.RoutineId

object BundledRoutineBank {
    val bank = RoutineBank(
        listOf(
            routine("routine.developer", "Developer", "Code, Terminal, GitHub, browser tools, and capture.", RoutineCategory.DEVELOPER, "coding_start", "terminal", "github", "dev_tools", "chatgpt", "screenshot", "copy", "paste"),
            routine("routine.presentation", "Presentation", "Present, navigate, capture, and control audio.", RoutineCategory.PRESENTATION, "full_screen", "space_left", "space_right", "screenshot", "play_pause", "mute", "vol_up", "vol_down"),
            routine("routine.meeting", "Meeting", "Calendar, mail, notes, mute, capture, and focus.", RoutineCategory.MEETING, "calendar", "gmail", "meeting_start", "mute", "screenshot", "focus_1h", "copy", "paste"),
            routine("routine.media", "Media", "Playback, track, volume, and fullscreen controls.", RoutineCategory.MEDIA, "play_pause", "next_track", "prev_track", "mute", "vol_up", "vol_down", "full_screen"),
            routine("routine.focus", "Deep focus", "Start work, mute distractions, and protect a focus block.", RoutineCategory.FOCUS, "coding_start", "focus_1h", "mute", "screensaver", "lock_mac"),
            routine("routine.browser", "Browser", "Navigate tabs, reload, inspect, copy, and paste.", RoutineCategory.BROWSER, "new_tab", "tab_left", "tab_right", "reload", "browser_back", "browser_forward", "dev_tools", "copy", "paste"),
            routine("routine.finder", "Finder", "Finder, Spotlight, common folders, and screenshots.", RoutineCategory.FINDER, "finder", "spotlight", "downloads", "documents", "show_desktop", "screenshot"),
            routine("routine.accessibility", "Accessible controls", "Large familiar input, volume, search, and screen controls.", RoutineCategory.ACCESSIBILITY, "keyboard", "trackpad", "spotlight", "vol_up", "vol_down", "mute", "full_screen"),
            routine("routine.safety", "Safety", "Lock, screensaver, sleep display, and connection checks.", RoutineCategory.SAFETY, "lock_mac", "screensaver", "sleep_display", "detect_mac"),
        ),
    )

    private fun routine(
        id: String,
        title: String,
        summary: String,
        category: RoutineCategory,
        vararg actionIds: String,
    ) = RoutineDefinition(RoutineId(id), title, summary, category, actionIds.toList())
}
