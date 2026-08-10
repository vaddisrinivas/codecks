package io.codecks.data.icons

import io.codecks.domain.ActionIcon
import io.codecks.domain.icons.DeckIconCatalog
import io.codecks.domain.icons.IconCategory
import io.codecks.domain.icons.IconLicense
import io.codecks.domain.icons.IconPackDefinition
import io.codecks.domain.icons.IconPackId
import io.codecks.domain.icons.SemanticIconDefinition
import io.codecks.domain.icons.SemanticIconId

object BundledDeckIconCatalog {
    val catalog = DeckIconCatalog(
        packs = listOf(
            pack("pack.tabler", "Tabler", "MIT", "Tabler Icons contributors", "https://github.com/tabler/tabler-icons", "br.com.devsrsouza.compose.icons:tabler-icons:1.1.1"),
            pack("pack.feather", "Feather", "MIT", "Cole Bemis and Feather contributors", "https://github.com/feathericons/feather", "br.com.devsrsouza.compose.icons:feather:1.1.1"),
            pack("pack.material", "Material", "Apache-2.0", "Google LLC", "https://github.com/google/material-design-icons", "androidx.compose.material:material-icons-extended"),
            pack("pack.rounded", "Rounded", "Apache-2.0", "Google LLC", "https://github.com/google/material-design-icons", "androidx.compose.material:material-icons-extended", rounded = true),
        ),
        icons = listOf(
            icon("icon.fallback", ActionIcon.Apps, "Fallback", IconCategory.SYSTEM, "unknown", "missing"),
            icon("icon.add", ActionIcon.Add, "Add", IconCategory.APPS, "new", "create", "plus"),
            icon("icon.apps", ActionIcon.Apps, "Apps", IconCategory.APPS, "grid", "launcher"),
            icon("icon.browser", ActionIcon.Browser, "Browser", IconCategory.APPS, "web", "globe"),
            icon("icon.control", ActionIcon.Control, "Controls", IconCategory.SYSTEM, "settings", "adjust"),
            icon("icon.finder", ActionIcon.Finder, "Finder", IconCategory.APPS, "files", "desktop"),
            icon("icon.github", ActionIcon.Github, "Source control", IconCategory.APPS, "git", "code"),
            icon("icon.keyboard", ActionIcon.Keyboard, "Keyboard", IconCategory.SYSTEM, "type", "clipboard"),
            icon("icon.lock", ActionIcon.Lock, "Lock", IconCategory.SAFETY, "secure", "privacy"),
            icon("icon.mouse", ActionIcon.Mouse, "Trackpad", IconCategory.SYSTEM, "pointer", "cursor"),
            icon("icon.notifications", ActionIcon.Notifications, "Notifications", IconCategory.SYSTEM, "bell", "alerts"),
            icon("icon.play", ActionIcon.Play, "Play", IconCategory.MEDIA, "pause", "music", "video"),
            icon("icon.screenshot", ActionIcon.Screenshot, "Screenshot", IconCategory.CREATIVE, "camera", "capture"),
            icon("icon.search", ActionIcon.Search, "Search", IconCategory.NAVIGATION, "find", "spotlight"),
            icon("icon.terminal", ActionIcon.Terminal, "Terminal", IconCategory.APPS, "shell", "developer"),
            icon("icon.volume", ActionIcon.Volume, "Volume", IconCategory.MEDIA, "audio", "mute"),
            icon("icon.party", ActionIcon.Party, "Celebrate", IconCategory.CREATIVE, "confetti", "party"),
            icon("icon.sparkle", ActionIcon.Sparkle, "Magic", IconCategory.CREATIVE, "sparkle", "effect"),
            icon("icon.emoji", ActionIcon.Emoji, "Emoji", IconCategory.CREATIVE, "face", "reaction"),
            icon("icon.empty", ActionIcon.Empty, "Blank", IconCategory.CREATIVE, "empty", "color", "spacer"),
        ),
    )

    private fun pack(
        id: String,
        title: String,
        spdx: String,
        copyright: String,
        upstream: String,
        coordinate: String,
        rounded: Boolean = false,
    ) = IconPackDefinition(IconPackId(id), title, IconLicense(spdx, copyright, upstream), coordinate, rounded)

    private fun icon(
        id: String,
        actionIcon: ActionIcon,
        label: String,
        category: IconCategory,
        vararg terms: String,
    ) = SemanticIconDefinition(SemanticIconId(id), actionIcon, label, category, terms.toSet())
}
