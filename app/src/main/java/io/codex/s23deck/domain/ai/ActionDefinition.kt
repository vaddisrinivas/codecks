package io.codex.s23deck.domain.ai

import kotlinx.serialization.Serializable

const val CURRENT_ACTION_SCHEMA_VERSION = 1

@Serializable
data class ActionDefinition(
    val schemaVersion: Int = CURRENT_ACTION_SCHEMA_VERSION,
    val id: String,
    val title: String,
    val description: String = "",
    val variables: List<ActionVariable> = emptyList(),
    val templates: List<ActionTemplate> = emptyList(),
    val requiredCapabilities: List<ActionCapability> = emptyList(),
    val target: TargetSelector = TargetSelector.AnyConnected,
    val safety: SafetyMetadata = SafetyMetadata(),
    val steps: List<ActionStep> = emptyList(),
)

@Serializable
data class ActionDraft(
    val schemaVersion: Int = CURRENT_ACTION_SCHEMA_VERSION,
    val prompt: String,
    val definition: ActionDefinition,
    val providerModel: String? = null,
)

@Serializable
data class DeckDraft(
    val schemaVersion: Int = CURRENT_ACTION_SCHEMA_VERSION,
    val prompt: String,
    val id: String,
    val title: String,
    val description: String = "",
    val actions: List<ActionDefinition>,
)

@Serializable
data class ActionStep(
    val id: String,
    val type: String,
    val label: String = "",
    val value: String? = null,
    val url: String? = null,
    val delayMs: Long? = null,
    val retry: RetryPolicy = RetryPolicy(),
    val requiredCapabilities: List<ActionCapability> = emptyList(),
    val confirmedDangerous: Boolean = false,
)

@Serializable
data class RetryPolicy(
    val maxAttempts: Int = 1,
    val delayMs: Long = 0,
)

@Serializable
data class ActionVariable(
    val name: String,
    val label: String,
    val required: Boolean = false,
    val defaultValue: String? = null,
)

@Serializable
data class ActionTemplate(
    val id: String,
    val body: String,
)

@Serializable
data class SafetyMetadata(
    val level: SafetyLevel = SafetyLevel.Normal,
    val requiresConfirmation: Boolean = false,
    val confirmationTitle: String? = null,
    val confirmationBody: String? = null,
)

@Serializable
enum class SafetyLevel {
    Normal,
    Dangerous,
}

@Serializable
enum class ActionCapability {
    Advanced,
    Browser,
    Clipboard,
    HidKeyboard,
    HidMouse,
    Media,
    Shell,
    Ssh,
}

@Serializable
sealed interface TargetSelector {
    @Serializable
    data object AnyConnected : TargetSelector

    @Serializable
    data object ActiveDevice : TargetSelector

    @Serializable
    data class DeviceId(val id: String) : TargetSelector

    @Serializable
    data class GroupId(val id: String) : TargetSelector
}

object ActionStepTypes {
    const val OpenUrl = "open_url"
    const val Delay = "delay"
    const val HidKey = "hid_key"
    const val ClipboardText = "clipboard_text"
    const val SshAction = "ssh_action"
    const val Shell = "shell"

    val supported: Set<String> = setOf(OpenUrl, Delay, HidKey, ClipboardText, SshAction, Shell)
}
