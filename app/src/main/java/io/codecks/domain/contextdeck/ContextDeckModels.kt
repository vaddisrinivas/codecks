package io.codecks.domain.contextdeck

import io.codecks.domain.DeckAction
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.StateSource

private const val MAX_LABEL_CHARS = 96
private const val MAX_TOKEN_CHARS = 128

enum class LiveSignalId {
    Mute,
    Camera,
    Music,
    Recording,
    Vpn,
}

enum class LiveSignalValue {
    Active,
    Inactive,
    Unknown,
}

data class LiveSignal(
    val id: LiveSignalId,
    val value: LiveSignalValue,
    val status: ObservationStatus,
    val observedAtMillis: Long?,
    val source: StateSource?,
    val detail: String? = null,
) {
    init {
        require(observedAtMillis == null || observedAtMillis >= 0) { "Live signal time must be non-negative." }
        require(detail == null || detail.isSafeLabel()) { "Live signal detail is invalid." }
        require(value == LiveSignalValue.Unknown || status != ObservationStatus.Unavailable) {
            "Unavailable live signals must not claim a value."
        }
    }
}

enum class AnalogControlKind {
    Volume,
    Brightness,
    Timeline,
}

data class AnalogControl(
    val kind: AnalogControlKind,
    val valuePercent: Int?,
    val status: ObservationStatus,
    val reasonUnavailable: String? = null,
) {
    init {
        require(valuePercent == null || valuePercent in 0..100) { "Analog value must be 0..100." }
        require(reasonUnavailable == null || reasonUnavailable.isSafeLabel()) { "Analog reason is invalid." }
        require(status != ObservationStatus.Unavailable || valuePercent == null) {
            "Unavailable analog controls must not expose a value."
        }
    }

    val enabled: Boolean
        get() = valuePercent != null && status in setOf(ObservationStatus.Fresh, ObservationStatus.Stale)
}

data class AppDeckOffer(
    val bundleId: String,
    val appName: String,
    val templateId: String,
    val templateTitle: String,
    val observedAtMillis: Long,
) {
    init {
        require(bundleId.isSafeToken()) { "App bundle id is invalid." }
        require(appName.isSafeLabel()) { "App name is invalid." }
        require(templateId.isSafeToken()) { "Template id is invalid." }
        require(templateTitle.isSafeLabel()) { "Template title is invalid." }
        require(observedAtMillis >= 0) { "App offer time must be non-negative." }
    }
}

data class ModifierLayer(
    val modifierActionId: String,
    val actions: List<DeckAction>,
) {
    init {
        require(modifierActionId.isSafeToken()) { "Modifier action id is invalid." }
        require(actions.size in 1..MAX_LAYER_ACTIONS) { "Modifier layer must contain 1..$MAX_LAYER_ACTIONS actions." }
        require(actions.map(DeckAction::id).distinct().size == actions.size) { "Modifier layer action ids must be unique." }
        require(actions.none { it.id == modifierActionId }) { "Modifier action cannot appear in its own layer." }
    }

    companion object {
        const val MAX_LAYER_ACTIONS = 12
    }
}

data class WindowCard(
    val id: String,
    val title: String,
    val appName: String,
    val bundleId: String,
    val spaceId: String?,
    val displayId: String?,
    val focused: Boolean,
) {
    init {
        require(id.isSafeToken()) { "Window id is invalid." }
        require(title.isSafeLabel()) { "Window title is invalid." }
        require(appName.isSafeLabel()) { "Window app name is invalid." }
        require(bundleId.isSafeToken()) { "Window bundle id is invalid." }
        require(spaceId == null || spaceId.isSafeToken()) { "Window space id is invalid." }
        require(displayId == null || displayId.isSafeToken()) { "Window display id is invalid." }
    }
}

data class SpaceCard(
    val id: String,
    val label: String,
    val displayId: String?,
    val focused: Boolean,
) {
    init {
        require(id.isSafeToken()) { "Space id is invalid." }
        require(label.isSafeLabel()) { "Space label is invalid." }
        require(displayId == null || displayId.isSafeToken()) { "Space display id is invalid." }
    }
}

data class WindowSpaceMap(
    val windows: List<WindowCard>,
    val spaces: List<SpaceCard>,
    val status: ObservationStatus,
    val observedAtMillis: Long?,
) {
    init {
        require(windows.size <= MAX_WINDOWS) { "Window map exceeds $MAX_WINDOWS windows." }
        require(spaces.size <= MAX_SPACES) { "Window map exceeds $MAX_SPACES spaces." }
        require(windows.map(WindowCard::id).distinct().size == windows.size) { "Window ids must be unique." }
        require(spaces.map(SpaceCard::id).distinct().size == spaces.size) { "Space ids must be unique." }
        require(windows.count(WindowCard::focused) <= 1) { "At most one window may be focused." }
        require(spaces.count(SpaceCard::focused) <= 1) { "At most one space may be focused." }
        require(observedAtMillis == null || observedAtMillis >= 0) { "Window map time must be non-negative." }
    }

    companion object {
        const val MAX_WINDOWS = 48
        const val MAX_SPACES = 16
    }
}

data class FileDropItem(
    val uriToken: String,
    val displayName: String,
    val byteCount: Long,
    val mimeType: String?,
) {
    init {
        require(uriToken.isSafeToken()) { "File Drop URI token is invalid." }
        require(displayName.isSafeFileName()) { "File Drop name is invalid." }
        require(byteCount in 0..MAX_FILE_BYTES) { "File Drop item exceeds the size limit." }
        require(mimeType == null || mimeType.matches(Regex("[A-Za-z0-9.+-]{1,64}/[A-Za-z0-9.+-]{1,64}"))) {
            "File Drop MIME type is invalid."
        }
    }

    companion object {
        const val MAX_FILE_BYTES = 100L * 1024L * 1024L
    }
}

data class FileDropShelf(
    val items: List<FileDropItem>,
    val targetId: DeviceId?,
    val remoteRootId: String?,
) {
    init {
        require(items.size <= MAX_ITEMS) { "File Drop shelf exceeds $MAX_ITEMS items." }
        require(items.map(FileDropItem::uriToken).distinct().size == items.size) { "File Drop items must be unique." }
        require(remoteRootId == null || remoteRootId.isSafeToken()) { "File Drop remote root is invalid." }
        require(items.sumOf(FileDropItem::byteCount) <= MAX_TOTAL_BYTES) { "File Drop shelf exceeds total size limit." }
    }

    companion object {
        const val MAX_ITEMS = 8
        const val MAX_TOTAL_BYTES = 250L * 1024L * 1024L
    }
}

data class RecordedWorkflowStep(
    val actionId: String,
    val targetSelector: TargetSelector,
) {
    init {
        require(actionId.isSafeToken()) { "Recorded workflow action id is invalid." }
    }
}

data class WorkflowRecording(
    val recording: Boolean = false,
    val steps: List<RecordedWorkflowStep> = emptyList(),
    val startedAtMillis: Long? = null,
) {
    init {
        require(steps.size <= MAX_STEPS) { "Workflow recording exceeds $MAX_STEPS steps." }
        require(startedAtMillis == null || startedAtMillis >= 0) { "Workflow start time must be non-negative." }
        require(recording == (startedAtMillis != null)) { "Workflow recording time and state disagree." }
    }

    companion object {
        const val MAX_STEPS = 24
    }
}

data class HandoffPlan(
    val actionId: String,
    val selector: TargetSelector,
    val resolvedTargetIds: List<DeviceId>,
    val explicitMultiTargetConfirmation: Boolean,
) {
    init {
        require(actionId.isSafeToken()) { "Handoff action id is invalid." }
        require(resolvedTargetIds.isNotEmpty()) { "Handoff needs at least one target." }
        require(resolvedTargetIds.distinct().size == resolvedTargetIds.size) { "Handoff targets must be unique." }
        require(resolvedTargetIds.size == 1 || explicitMultiTargetConfirmation) {
            "Multi-Mac handoff requires explicit confirmation."
        }
    }
}

data class ContextMacTarget(
    val id: DeviceId,
    val displayName: String,
    val ready: Boolean,
) {
    init {
        require(displayName.isSafeLabel()) { "Mac target name is invalid." }
    }
}

enum class MiniDeckCommand(val label: String) {
    PlayPause("Play / pause"),
    Mute("Mute"),
    VolumeDown("Volume down"),
    VolumeUp("Volume up"),
}

data class ContextSuggestion(
    val action: DeckAction,
    val score: Int,
    val reason: String,
) {
    init {
        require(score in 0..100) { "Context suggestion score must be 0..100." }
        require(reason.isSafeLabel()) { "Context suggestion reason is invalid." }
    }
}

internal fun String.isSafeToken(): Boolean =
    length in 1..MAX_TOKEN_CHARS && matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]*"))

internal fun String.isSafeLabel(): Boolean =
    isNotBlank() && length <= MAX_LABEL_CHARS && none { it.isISOControl() }

private fun String.isSafeFileName(): Boolean =
    isSafeLabel() && this !in setOf(".", "..") && '/' !in this && '\\' !in this
