package io.codecks.domain.actions

import io.codecks.domain.targets.TargetCapability

object CoreActionCatalog {
    val definitions: List<ActionDefinition> = listOf(
        ssh("mac.finder.open", "Finder", "Apps", TargetCapability.SSH_APP_CONTROL),
        ssh("mac.terminal.open", "Terminal", "Apps", TargetCapability.SSH_APP_CONTROL),
        ssh("mac.spotlight.open", "Spotlight", "System", TargetCapability.SSH_SYSTEM_CONTROL),
        ssh("mac.screenshot.capture", "Screenshot", "System", TargetCapability.SSH_SYSTEM_CONTROL, SafetyClass.CONFIRM),
        ssh("mac.mission-control.show", "Mission Control", "Window", TargetCapability.SSH_SYSTEM_CONTROL),
        ssh("mac.space.left", "Space Left", "Window", TargetCapability.SSH_SYSTEM_CONTROL),
        ssh("mac.space.right", "Space Right", "Window", TargetCapability.SSH_SYSTEM_CONTROL),
        ssh("mac.window.full-screen", "Full Screen", "Window", TargetCapability.SSH_SYSTEM_CONTROL),
        ssh("mac.app.previous", "Previous App", "Window", TargetCapability.SSH_SYSTEM_CONTROL),
        ssh("mac.app.next", "Next App", "Window", TargetCapability.SSH_SYSTEM_CONTROL),
        ssh("mac.browser.new-tab", "New Tab", "Browser", TargetCapability.SSH_APP_CONTROL),
        hid("mac.media.play-pause", "Play/Pause", "Media", TargetCapability.HID_CONSUMER_MEDIA),
        hid("mac.media.mute", "Mute", "Media", TargetCapability.HID_CONSUMER_MEDIA),
        hid("mac.media.volume-down", "Volume Down", "Media", TargetCapability.HID_CONSUMER_MEDIA),
        hid("mac.media.volume-up", "Volume Up", "Media", TargetCapability.HID_CONSUMER_MEDIA),
        ssh("mac.lock", "Lock Mac", "System", TargetCapability.SSH_SYSTEM_CONTROL, SafetyClass.CONFIRM),
        local("android.trackpad.open", "Trackpad", "Local"),
        local("android.keyboard.open", "Keyboard", "Local"),
        ssh(
            stableId = "mac.url.open",
            title = "Open URL",
            category = "Browser",
            capability = TargetCapability.OPEN_URL,
            safetyClass = SafetyClass.CONFIRM,
            inputs = listOf(ActionInput(name = "url", type = "uri", required = true)),
        ),
    )

    fun requireDefinition(actionId: String): ActionDefinition =
        definitions.firstOrNull { it.stableId == actionId }
            ?: error("Unknown action: $actionId")

    private fun ssh(
        stableId: String,
        title: String,
        category: String,
        capability: TargetCapability,
        safetyClass: SafetyClass = SafetyClass.SAFE,
        inputs: List<ActionInput> = emptyList(),
    ) = ActionDefinition(
        stableId = stableId,
        version = 1,
        title = title,
        category = category,
        inputs = inputs,
        capabilities = setOf(capability),
        safetyClass = safetyClass,
        executorKind = ExecutorKind.SSH_MAC,
    )

    private fun hid(
        stableId: String,
        title: String,
        category: String,
        capability: TargetCapability,
    ) = ActionDefinition(
        stableId = stableId,
        version = 1,
        title = title,
        category = category,
        capabilities = setOf(capability),
        safetyClass = SafetyClass.SAFE,
        executorKind = ExecutorKind.HID,
    )

    private fun local(
        stableId: String,
        title: String,
        category: String,
    ) = ActionDefinition(
        stableId = stableId,
        version = 1,
        title = title,
        category = category,
        capabilities = emptySet(),
        safetyClass = SafetyClass.SAFE,
        executorKind = ExecutorKind.LOCAL_ANDROID,
    )
}

