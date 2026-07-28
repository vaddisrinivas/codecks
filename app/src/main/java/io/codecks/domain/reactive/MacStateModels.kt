package io.codecks.domain.reactive

import io.codecks.shared.protocol.DisplayState
import io.codecks.shared.protocol.ReactiveCapabilityId
import io.codecks.shared.protocol.ReactiveHelperBasicState
import io.codecks.shared.protocol.StateProvenance
import io.codecks.shared.protocol.WindowState

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
    SpotlightSearch,
    SftpTransfer,
    MonitorBrightness,
    AccessibilityDiscovery,
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
    val widthPx: Int = 1,
    val heightPx: Int = 1,
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
    val source: StateSource = StateSource.LocalCache,
    val freshnessMillis: Long = 3_000L,
    val stale: Boolean = false,
    val warningCode: String? = null,
) {
    fun isBasicStateExpired(nowMillis: Long, maxAgeMillis: Long = 3_000): Boolean =
        stale || nowMillis - capturedAtMillis > maxAgeMillis
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

fun ReactiveHelperBasicState.toMacStateSnapshot(
    nowMillis: Long,
    sourceOverride: StateSource? = null,
): MacStateSnapshot {
    val source = sourceOverride ?: provenance.toStateSource()
    val frontApp = toFrontApp()
    val activeWindowApp = activeWindow?.toMacApplication() ?: frontApp
    val status = if (stale || nowMillis - capturedAtMillis > freshnessMillis) {
        ObservationStatus.Stale
    } else {
        ObservationStatus.Fresh
    }
    val warningCode = if (status == ObservationStatus.Stale) "stale_${source.name.lowercase()}" else null
    return MacStateSnapshot(
        macId = MacId(macId),
        snapshotRevision = snapshotRevision,
        capturedAtMillis = capturedAtMillis,
        frontApp = Observed(
            value = frontApp,
            status = if (frontApp == null) ObservationStatus.Unavailable else status,
            observedAtMillis = capturedAtMillis,
            source = source,
            warningCode = warningCode,
        ),
        activeWindow = Observed(
            value = activeWindow?.toMacWindow(activeWindowApp),
            status = if (activeWindow == null) ObservationStatus.Unavailable else status,
            observedAtMillis = capturedAtMillis,
            source = source,
            warningCode = warningCode,
        ),
        displays = Observed(
            value = displays.map { it.toMacDisplay() },
            status = if (displays.isEmpty()) ObservationStatus.Unavailable else status,
            observedAtMillis = capturedAtMillis,
            source = source,
            warningCode = warningCode,
        ),
        cursor = unavailableObserved(),
        selection = unavailableObserved(MacSelection.None),
        clipboard = unavailableObserved(
            MacClipboardMetadata(
                kind = MacClipboardKind.Unknown,
                byteSizeBucket = null,
                safePreview = null,
                changedAtMillis = null,
            ),
        ),
        media = unavailableObserved(
            MacMediaState(
                app = null,
                title = null,
                artist = null,
                playing = false,
                muted = null,
            ),
        ),
        system = unavailableObserved(
            MacSystemState(
                focusedSpaceLabel = null,
                volumePercent = null,
                muted = null,
            ),
        ),
        meeting = unavailableObserved(
            MacMeetingState(
                app = null,
                inMeeting = false,
                microphoneMuted = null,
                cameraOn = null,
            ),
        ),
        latestScreenshot = unavailableObserved(
            MacScreenshotState(
                path = null,
                capturedAtMillis = null,
            ),
        ),
        capabilities = capabilities.mapToCapabilityStates(source).toSet(),
        source = source,
        freshnessMillis = freshnessMillis,
        stale = status == ObservationStatus.Stale,
        warningCode = warningCode,
    )
}

fun MacStateSnapshot.markStale(
    nowMillis: Long,
    warningCode: String,
): MacStateSnapshot = copy(
    snapshotRevision = snapshotRevision + 1L,
    capturedAtMillis = nowMillis,
    frontApp = frontApp.asStale(warningCode),
    activeWindow = activeWindow.asStale(warningCode),
    displays = displays.asStale(warningCode),
    cursor = cursor.asStale(warningCode),
    selection = selection.asStale(warningCode),
    clipboard = clipboard.asStale(warningCode),
    media = media.asStale(warningCode),
    system = system.asStale(warningCode),
    meeting = meeting.asStale(warningCode),
    latestScreenshot = latestScreenshot.asStale(warningCode),
    stale = true,
    warningCode = warningCode,
)

private fun StateProvenance.toStateSource(): StateSource = when (this) {
    StateProvenance.Helper -> StateSource.Helper
    StateProvenance.Ssh -> StateSource.SshProbe
    StateProvenance.Cached,
    StateProvenance.TestFixture -> StateSource.LocalCache
}

private fun ReactiveHelperBasicState.toFrontApp(): MacApplication? {
    val bundleId = frontAppBundleId?.takeIf { it.isNotBlank() } ?: return null
    val name = frontAppName?.takeIf { it.isNotBlank() } ?: bundleId.substringAfterLast('.')
    return MacApplication(
        bundleId = bundleId,
        displayName = name,
        kind = inferMacAppKind(bundleId, name),
    )
}

private fun WindowState.toMacApplication(): MacApplication = MacApplication(
    bundleId = bundleId,
    displayName = bundleId.substringAfterLast('.').ifBlank { bundleId },
    kind = inferMacAppKind(bundleId, title),
)

private fun WindowState.toMacWindow(app: MacApplication?): MacWindow = MacWindow(
    app = app,
    title = title,
    role = if (focused) "focused_window" else "window",
)

private fun DisplayState.toMacDisplay(): MacDisplay = MacDisplay(
    id = displayId,
    name = name,
    isPrimary = false,
    isBuiltIn = false,
)

private fun Set<ReactiveCapabilityId>.mapToCapabilityStates(source: StateSource): List<CapabilityState> = map {
    CapabilityState(
        capability = when (it) {
            ReactiveCapabilityId.FrontAppState -> CodecksCapability.FrontAppRead
            ReactiveCapabilityId.WindowState -> CodecksCapability.ActiveWindowRead
            ReactiveCapabilityId.ActionExecute -> CodecksCapability.MacCommand
            ReactiveCapabilityId.ActionUndo -> CodecksCapability.MacCommand
            ReactiveCapabilityId.ClipboardSelectedText -> CodecksCapability.SelectionRead
            ReactiveCapabilityId.SpotlightSearch -> CodecksCapability.SpotlightSearch
            ReactiveCapabilityId.TransferSftp -> CodecksCapability.SftpTransfer
            ReactiveCapabilityId.AppleShortcuts -> CodecksCapability.MacCommand
            ReactiveCapabilityId.MonitorBrightness -> CodecksCapability.MonitorBrightness
            ReactiveCapabilityId.AccessibilityDiscovery -> CodecksCapability.AccessibilityDiscovery
        },
        availability = if (source == StateSource.Helper || source == StateSource.SshProbe) {
            CapabilityAvailability.Available
        } else {
            CapabilityAvailability.Unknown
        },
    )
}

private fun inferMacAppKind(bundleId: String, name: String): MacAppKind {
    val normalized = "$bundleId $name".lowercase()
    return when {
        normalized.contains("chrome") ||
            normalized.contains("safari") ||
            normalized.contains("firefox") ||
            normalized.contains("brave") ||
            normalized.contains("arc") ||
            normalized.contains("edge") -> MacAppKind.Browser
        normalized.contains("finder") -> MacAppKind.Finder
        normalized.contains("terminal") ||
            normalized.contains("iterm") ||
            normalized.contains("warp") ||
            normalized.contains("kitty") ||
            normalized.contains("alacritty") -> MacAppKind.Terminal
        normalized.contains("cursor") ||
            normalized.contains("code") ||
            normalized.contains("studio") ||
            normalized.contains("xcode") -> MacAppKind.CodeEditor
        normalized.contains("zoom") ||
            normalized.contains("meet") ||
            normalized.contains("teams") -> MacAppKind.Meeting
        normalized.contains("calendar") -> MacAppKind.Calendar
        normalized.contains("mail") || normalized.contains("gmail") || normalized.contains("outlook") -> MacAppKind.Mail
        normalized.contains("music") || normalized.contains("spotify") || normalized.contains("video") -> MacAppKind.Media
        normalized.contains("messages") || normalized.contains("slack") || normalized.contains("discord") -> MacAppKind.Messages
        else -> MacAppKind.Generic
    }
}

private fun <T> unavailableObserved(value: T? = null): Observed<T> = Observed(
    value = value,
    status = ObservationStatus.Unavailable,
    observedAtMillis = null,
    source = null,
)

private fun <T> Observed<T>.asStale(warningCode: String): Observed<T> = copy(
    status = if (value == null) status else ObservationStatus.Stale,
    warningCode = warningCode,
)
