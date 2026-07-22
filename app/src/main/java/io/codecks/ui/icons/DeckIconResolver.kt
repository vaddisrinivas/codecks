package io.codecks.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Web
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FeatherIcons
import compose.icons.TablerIcons
import compose.icons.feathericons.*
import compose.icons.tablericons.*
import io.codecks.domain.ActionIcon
import io.codecks.domain.DeckAction
import io.codecks.ui.theme.CodecksIconPack
import io.codecks.ui.theme.LocalCodecksIconPack

@Composable
fun ActionIcon.imageVector(): ImageVector = imageVector(LocalCodecksIconPack.current)

fun ActionIcon.imageVector(iconPack: CodecksIconPack): ImageVector = resolveActionIcon(this, iconPack)

@Composable
fun DeckAction.deckImageVector(): ImageVector {
    val pack = LocalCodecksIconPack.current
    return when (id) {
        "space_left", "prev_app", "browser_back", "tab_left" -> pack.arrowLeft()
        "space_right", "next_app", "browser_forward", "tab_right" -> pack.arrowRight()
        "full_screen" -> pack.fullscreen()
        "automations" -> pack.automations()
        "mission" -> pack.missionControl()
        "new_tab" -> pack.newTab()
        "mute" -> pack.mute()
        "vol_down" -> pack.volumeDown()
        else -> resolveActionIcon(icon, pack)
    }
}

private fun resolveActionIcon(icon: ActionIcon, pack: CodecksIconPack): ImageVector =
    when (pack) {
        CodecksIconPack.Material -> when (icon) {
            ActionIcon.Add -> Icons.Outlined.Add
            ActionIcon.Apps -> Icons.Outlined.Apps
            ActionIcon.Browser -> Icons.Outlined.Web
            ActionIcon.Control -> Icons.Outlined.Tune
            ActionIcon.Finder -> Icons.Outlined.Computer
            ActionIcon.Github -> Icons.Outlined.Apps
            ActionIcon.Keyboard -> Icons.Outlined.Keyboard
            ActionIcon.Lock -> Icons.Outlined.Lock
            ActionIcon.Mouse -> Icons.Outlined.Mouse
            ActionIcon.Notifications -> Icons.Outlined.Notifications
            ActionIcon.Play -> Icons.Outlined.PlayArrow
            ActionIcon.Screenshot -> Icons.Outlined.PhotoCamera
            ActionIcon.Search -> Icons.Outlined.Search
            ActionIcon.Terminal -> Icons.Outlined.Terminal
            ActionIcon.Volume -> Icons.AutoMirrored.Outlined.VolumeUp
            ActionIcon.Party -> Icons.Outlined.AutoAwesome
            ActionIcon.Sparkle -> Icons.Outlined.AutoAwesome
            ActionIcon.Emoji -> Icons.Outlined.AutoAwesome
            ActionIcon.Empty -> Icons.Outlined.Add
        }

        CodecksIconPack.Feather -> when (icon) {
            ActionIcon.Add -> FeatherIcons.Plus
            ActionIcon.Apps -> FeatherIcons.Grid
            ActionIcon.Browser -> FeatherIcons.Globe
            ActionIcon.Control -> FeatherIcons.Sliders
            ActionIcon.Finder -> FeatherIcons.Monitor
            ActionIcon.Github -> FeatherIcons.Github
            ActionIcon.Keyboard -> FeatherIcons.Command
            ActionIcon.Lock -> FeatherIcons.Lock
            ActionIcon.Mouse -> FeatherIcons.MousePointer
            ActionIcon.Notifications -> FeatherIcons.Bell
            ActionIcon.Play -> FeatherIcons.Play
            ActionIcon.Screenshot -> FeatherIcons.Camera
            ActionIcon.Search -> FeatherIcons.Search
            ActionIcon.Terminal -> FeatherIcons.Terminal
            ActionIcon.Volume -> FeatherIcons.Volume2
            ActionIcon.Party -> FeatherIcons.Plus
            ActionIcon.Sparkle -> FeatherIcons.Plus
            ActionIcon.Emoji -> FeatherIcons.Plus
            ActionIcon.Empty -> FeatherIcons.Grid
        }

        CodecksIconPack.Tabler -> when (icon) {
            ActionIcon.Add -> TablerIcons.Plus
            ActionIcon.Apps -> TablerIcons.Apps
            ActionIcon.Browser -> TablerIcons.World
            ActionIcon.Control -> TablerIcons.Adjustments
            ActionIcon.Finder -> TablerIcons.DeviceDesktop
            ActionIcon.Github -> TablerIcons.BrandGithub
            ActionIcon.Keyboard -> TablerIcons.Keyboard
            ActionIcon.Lock -> TablerIcons.Lock
            ActionIcon.Mouse -> TablerIcons.Mouse
            ActionIcon.Notifications -> TablerIcons.Bell
            ActionIcon.Play -> TablerIcons.PlayerPlay
            ActionIcon.Screenshot -> TablerIcons.Camera
            ActionIcon.Search -> TablerIcons.Search
            ActionIcon.Terminal -> TablerIcons.Terminal2
            ActionIcon.Volume -> TablerIcons.Volume2
            ActionIcon.Party -> TablerIcons.Stars
            ActionIcon.Sparkle -> TablerIcons.Stars
            ActionIcon.Emoji -> TablerIcons.Stars
            ActionIcon.Empty -> TablerIcons.Plus
        }
    }

private fun CodecksIconPack.arrowLeft(): ImageVector = when (this) {
    CodecksIconPack.Material -> Icons.AutoMirrored.Outlined.ArrowBack
    CodecksIconPack.Feather -> FeatherIcons.ArrowLeft
    CodecksIconPack.Tabler -> TablerIcons.ArrowLeft
}

private fun CodecksIconPack.arrowRight(): ImageVector = when (this) {
    CodecksIconPack.Material -> Icons.AutoMirrored.Outlined.ArrowForward
    CodecksIconPack.Feather -> FeatherIcons.ArrowRight
    CodecksIconPack.Tabler -> TablerIcons.ArrowRight
}

private fun CodecksIconPack.fullscreen(): ImageVector = when (this) {
    CodecksIconPack.Material -> Icons.Outlined.Fullscreen
    CodecksIconPack.Feather -> FeatherIcons.Maximize
    CodecksIconPack.Tabler -> TablerIcons.Maximize
}

private fun CodecksIconPack.automations(): ImageVector = when (this) {
    CodecksIconPack.Material -> Icons.Outlined.AutoAwesome
    CodecksIconPack.Feather -> FeatherIcons.Zap
    CodecksIconPack.Tabler -> TablerIcons.Stars
}

private fun CodecksIconPack.missionControl(): ImageVector = when (this) {
    CodecksIconPack.Material -> Icons.Outlined.Apps
    CodecksIconPack.Feather -> FeatherIcons.Command
    CodecksIconPack.Tabler -> TablerIcons.LayoutGrid
}

private fun CodecksIconPack.newTab(): ImageVector = when (this) {
    CodecksIconPack.Material -> Icons.Outlined.Tab
    CodecksIconPack.Feather -> FeatherIcons.PlusSquare
    CodecksIconPack.Tabler -> TablerIcons.LayoutGridAdd
}

private fun CodecksIconPack.mute(): ImageVector = when (this) {
    CodecksIconPack.Material -> Icons.AutoMirrored.Outlined.VolumeOff
    CodecksIconPack.Feather -> FeatherIcons.VolumeX
    CodecksIconPack.Tabler -> TablerIcons.Volume3
}

private fun CodecksIconPack.volumeDown(): ImageVector = when (this) {
    CodecksIconPack.Material -> Icons.AutoMirrored.Outlined.VolumeDown
    CodecksIconPack.Feather -> FeatherIcons.Volume1
    CodecksIconPack.Tabler -> TablerIcons.Volume
}
