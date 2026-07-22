package io.codecks.domain

import io.codecks.domain.device.TargetSelector

enum class ActionKind {
    Local,
    Ssh,
}

data class DeckAction(
    val id: String,
    val label: String,
    val kind: ActionKind,
    val icon: ActionIcon,
    val description: String = "",
    val route: String? = null,
    val command: String? = null,
    val testCommand: String? = null,
    val dangerous: Boolean = false,
    val liveSafe: Boolean = false,
    val requiresTest: Boolean = false,
    val targetSelector: TargetSelector = TargetSelector.CurrentDevice,
    val commandOrigin: CommandOrigin = CommandOrigin.Bundled,
    val commandReview: CommandReview = CommandReview(),
    val confirmationTitle: String? = null,
    val confirmationBody: String? = null,
    val riskReason: String? = null,
    val executionAuthorization: ExecutionAuthorization = ExecutionAuthorization(),
)

enum class ActionIcon {
    Add,
    Apps,
    Browser,
    Control,
    Finder,
    Github,
    Keyboard,
    Lock,
    Mouse,
    Notifications,
    Play,
    Screenshot,
    Search,
    Terminal,
    Volume,
    Party,
    Sparkle,
    Emoji,
    Empty,
}


sealed interface ActionStatus {
    data object Idle : ActionStatus
    data class Running(val actionId: String) : ActionStatus
    data class Succeeded(val actionId: String, val message: String) : ActionStatus
    data class Failed(val actionId: String, val message: String) : ActionStatus
}
