package io.codecks.domain.reactive

private const val MaxInlineIdBytes = 128

@JvmInline
value class MacId(val value: String) {
    init {
        require(value.isNotBlank()) { "MacId must not be blank." }
        require(value.toByteArray(Charsets.UTF_8).size <= MaxInlineIdBytes) {
            "MacId must be at most $MaxInlineIdBytes UTF-8 bytes."
        }
    }
}

@JvmInline
value class ControlId(val value: String) {
    init {
        require(value.isNotBlank()) { "ControlId must not be blank." }
        require(value.toByteArray(Charsets.UTF_8).size <= MaxInlineIdBytes) {
            "ControlId must be at most $MaxInlineIdBytes UTF-8 bytes."
        }
    }
}

@JvmInline
value class ActionRevision(val value: String) {
    init {
        require(value.isNotBlank()) { "ActionRevision must not be blank." }
        require(value.toByteArray(Charsets.UTF_8).size <= MaxInlineIdBytes) {
            "ActionRevision must be at most $MaxInlineIdBytes UTF-8 bytes."
        }
    }
}

@JvmInline
value class ReceiptId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReceiptId must not be blank." }
        require(value.toByteArray(Charsets.UTF_8).size <= MaxInlineIdBytes) {
            "ReceiptId must be at most $MaxInlineIdBytes UTF-8 bytes."
        }
    }
}

enum class CodecksCapability {
    PointerInput,
    KeyboardInput,
    MediaInput,
    MacCommand,
    FrontAppRead,
    ActiveWindowRead,
    WindowWrite,
    DisplayRead,
    CursorRead,
    CursorWrite,
    ClipboardRead,
    ClipboardWrite,
    SelectionRead,
    ScreenshotCapture,
    ScreenshotManage,
    MeetingControl,
}

enum class CapabilityAvailability {
    Available,
    PermissionNeeded,
    Unsupported,
    Offline,
    Unknown,
}

data class CapabilityState(
    val capability: CodecksCapability,
    val availability: CapabilityAvailability,
    val reasonCode: String? = null,
)

enum class StateSource {
    Helper,
    SshProbe,
    LocalCache,
}

enum class ObservationStatus {
    Fresh,
    Stale,
    PermissionNeeded,
    Unsupported,
    Unavailable,
}

data class Observed<T>(
    val value: T?,
    val status: ObservationStatus,
    val observedAtMillis: Long?,
    val source: StateSource?,
    val warningCode: String? = null,
)

enum class MacAppKind {
    Terminal,
    Browser,
    Finder,
    CodeEditor,
    Meeting,
    Calendar,
    Media,
    Mail,
    Messages,
    Generic,
}

data class MacApplication(
    val bundleId: String,
    val displayName: String,
    val kind: MacAppKind,
) {
    init {
        require(bundleId.isNotBlank()) { "MacApplication bundleId must not be blank." }
        require(displayName.isNotBlank()) { "MacApplication displayName must not be blank." }
    }
}

data class MacWindow(
    val app: MacApplication?,
    val title: String?,
    val role: String?,
    val tabTitle: String? = null,
)

data class MacDisplay(
    val id: String,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val isPrimary: Boolean,
    val isBuiltIn: Boolean,
) {
    init {
        require(id.isNotBlank()) { "MacDisplay id must not be blank." }
        require(name.isNotBlank()) { "MacDisplay name must not be blank." }
        require(widthPx > 0) { "MacDisplay widthPx must be positive." }
        require(heightPx > 0) { "MacDisplay heightPx must be positive." }
    }
}

data class MacCursorState(
    val xPx: Int,
    val yPx: Int,
    val displayId: String? = null,
    val visible: Boolean = true,
)

sealed interface MacSelection {
    data object None : MacSelection
    data class Text(val preview: String?, val private: Boolean) : MacSelection
    data class File(val path: String?, val name: String?, val isDirectory: Boolean) : MacSelection
    data class Url(val url: String) : MacSelection {
        init {
            require(url.isNotBlank()) { "MacSelection.Url url must not be blank." }
        }
    }

    data class Image(val path: String?) : MacSelection
    data class Unknown(val role: String?) : MacSelection
}

enum class MacClipboardKind {
    Text,
    File,
    Image,
    Url,
    Unknown,
}

data class MacClipboardMetadata(
    val kind: MacClipboardKind,
    val byteSizeBucket: String?,
    val safePreview: String?,
    val changedAtMillis: Long?,
)

data class MacMediaState(
    val app: MacApplication?,
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val muted: Boolean? = null,
)

data class MacSystemState(
    val focusedSpaceLabel: String?,
    val volumePercent: Int?,
    val muted: Boolean?,
)

data class MacMeetingState(
    val app: MacApplication?,
    val inMeeting: Boolean,
    val microphoneMuted: Boolean?,
    val cameraOn: Boolean?,
)

data class MacScreenshotState(
    val path: String?,
    val capturedAtMillis: Long?,
)

data class MacStateSnapshot(
    val macId: MacId,
    val snapshotRevision: Long,
    val capturedAtMillis: Long,
    val frontApp: Observed<MacApplication>,
    val activeWindow: Observed<MacWindow>,
    val displays: Observed<List<MacDisplay>>,
    val cursor: Observed<MacCursorState>,
    val selection: Observed<MacSelection>,
    val clipboard: Observed<MacClipboardMetadata>,
    val media: Observed<MacMediaState>,
    val system: Observed<MacSystemState>,
    val meeting: Observed<MacMeetingState>,
    val latestScreenshot: Observed<MacScreenshotState>,
    val capabilities: Set<CapabilityState>,
) {
    fun isBasicStateExpired(nowMillis: Long, maxAgeMillis: Long = 3_000): Boolean =
        nowMillis - capturedAtMillis > maxAgeMillis
}

enum class TrackpadVisibility {
    Hidden,
    Visible,
}

enum class MacStateField {
    FrontApp,
    ActiveWindow,
    Displays,
    Cursor,
    Selection,
    Clipboard,
    Media,
    System,
    Meeting,
    LatestScreenshot,
    Capabilities,
}

sealed interface MacStateConnectionState {
    data object Idle : MacStateConnectionState
    data class Connecting(val preferredSource: StateSource? = null) : MacStateConnectionState
    data class Connected(val source: StateSource) : MacStateConnectionState
    data class Degraded(val source: StateSource?, val reasonCode: String? = null) : MacStateConnectionState
    data class Disconnected(val reasonCode: String? = null) : MacStateConnectionState
}

sealed interface MacStateRefreshResult {
    data class Succeeded(
        val source: StateSource,
        val updatedFields: Set<MacStateField>,
        val snapshotRevision: Long? = null,
    ) : MacStateRefreshResult {
        init {
            require(updatedFields.isNotEmpty()) { "MacStateRefreshResult.Succeeded updatedFields must not be empty." }
        }
    }

    data class Skipped(val reasonCode: String) : MacStateRefreshResult {
        init {
            require(reasonCode.isNotBlank()) { "MacStateRefreshResult.Skipped reasonCode must not be blank." }
        }
    }

    data class Failed(
        val reasonCode: String,
        val retryable: Boolean,
    ) : MacStateRefreshResult {
        init {
            require(reasonCode.isNotBlank()) { "MacStateRefreshResult.Failed reasonCode must not be blank." }
        }
    }
}
