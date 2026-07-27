package io.codecks.data.reactive

import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CapabilityState
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacApplication
import io.codecks.domain.reactive.MacClipboardKind
import io.codecks.domain.reactive.MacClipboardMetadata
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.MacMeetingState
import io.codecks.domain.reactive.MacMediaState
import io.codecks.domain.reactive.MacScreenshotState
import io.codecks.domain.reactive.MacSelection
import io.codecks.domain.reactive.MacStateConnectionState
import io.codecks.domain.reactive.MacStateField
import io.codecks.domain.reactive.MacStateRefreshResult
import io.codecks.domain.reactive.MacStateRepository
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.MacSystemState
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.StateSource
import io.codecks.domain.reactive.TrackpadVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveMacStateInputs(
    val selectedMacId: String?,
    val macCommandsReady: Boolean,
    val macInputConnected: Boolean,
    val activeMacApp: String?,
)

class LiveMacStateRepository(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : MacStateRepository {
    private val _state = MutableStateFlow<MacStateSnapshot?>(null)
    override val state: StateFlow<MacStateSnapshot?> = _state.asStateFlow()

    private val _connection = MutableStateFlow<MacStateConnectionState>(MacStateConnectionState.Idle)
    override val connection: StateFlow<MacStateConnectionState> = _connection.asStateFlow()

    private var inputs = LiveMacStateInputs(
        selectedMacId = null,
        macCommandsReady = false,
        macInputConnected = false,
        activeMacApp = null,
    )
    private var visibility: TrackpadVisibility = TrackpadVisibility.Hidden
    private var revision: Long = 0L
    private var lastFrontApp: MacApplication? = null
    private var lastFrontAppObservedAtMillis: Long? = null

    fun update(next: LiveMacStateInputs) {
        inputs = next
        if (next.selectedMacId.isNullOrBlank()) {
            lastFrontApp = null
            lastFrontAppObservedAtMillis = null
            revision = 0L
            _state.value = null
            _connection.value = MacStateConnectionState.Idle
            return
        }
        rebuild()
    }

    override suspend fun refreshBasic(): MacStateRefreshResult {
        if (inputs.selectedMacId.isNullOrBlank()) {
            return MacStateRefreshResult.Skipped("no_selected_mac")
        }
        rebuild()
        return MacStateRefreshResult.Succeeded(
            source = StateSource.LocalCache,
            updatedFields = setOf(MacStateField.FrontApp, MacStateField.Capabilities),
            snapshotRevision = _state.value?.snapshotRevision,
        )
    }

    override suspend fun refreshDisplays(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("displays_not_implemented")

    override suspend fun refreshClipboardMetadata(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("clipboard_not_implemented")

    override suspend fun refreshMedia(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("media_not_implemented")

    override suspend fun inspectSelection(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("selection_not_implemented")

    override fun start(visibility: TrackpadVisibility) {
        this.visibility = visibility
        if (!inputs.selectedMacId.isNullOrBlank()) rebuild()
    }

    override fun stop() {
        visibility = TrackpadVisibility.Hidden
    }

    private fun rebuild() {
        val selectedMacId = inputs.selectedMacId ?: return
        val now = nowMillis()
        val activeName = inputs.activeMacApp?.trim().orEmpty()
        val frontAppObserved: Observed<MacApplication> = when {
            activeName.isNotBlank() -> {
                val app = activeName.toMacApplication()
                lastFrontApp = app
                lastFrontAppObservedAtMillis = now
                Observed(
                    value = app,
                    status = ObservationStatus.Fresh,
                    observedAtMillis = now,
                    source = StateSource.LocalCache,
                )
            }
            lastFrontApp != null && (inputs.macCommandsReady || inputs.macInputConnected) -> Observed(
                value = lastFrontApp,
                status = ObservationStatus.Stale,
                observedAtMillis = lastFrontAppObservedAtMillis,
                source = StateSource.LocalCache,
                warningCode = "front_app_not_refreshed",
            )
            else -> Observed(
                value = null,
                status = ObservationStatus.Unavailable,
                observedAtMillis = null,
                source = null,
                warningCode = if (inputs.macCommandsReady) "front_app_unknown" else "mac_offline",
            )
        }

        revision += 1L
        _state.value = MacStateSnapshot(
            macId = MacId(selectedMacId),
            snapshotRevision = revision,
            capturedAtMillis = now,
            frontApp = frontAppObserved,
            activeWindow = unavailableObserved(),
            displays = unavailableObserved(emptyList()),
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
            capabilities = buildCapabilities(frontAppObserved),
        )
        _connection.value = when {
            inputs.macCommandsReady && inputs.macInputConnected -> MacStateConnectionState.Connected(StateSource.LocalCache)
            inputs.macCommandsReady || inputs.macInputConnected -> MacStateConnectionState.Degraded(
                source = StateSource.LocalCache,
                reasonCode = "partial_connectivity",
            )
            visibility == TrackpadVisibility.Visible -> MacStateConnectionState.Disconnected("mac_not_ready")
            else -> MacStateConnectionState.Disconnected("mac_not_ready")
        }
    }

    private fun buildCapabilities(frontApp: Observed<MacApplication>): Set<CapabilityState> = buildSet {
        add(
            CapabilityState(
                capability = CodecksCapability.PointerInput,
                availability = if (inputs.macInputConnected) CapabilityAvailability.Available else CapabilityAvailability.Offline,
            ),
        )
        add(
            CapabilityState(
                capability = CodecksCapability.MacCommand,
                availability = if (inputs.macCommandsReady) CapabilityAvailability.Available else CapabilityAvailability.Offline,
            ),
        )
        add(
            CapabilityState(
                capability = CodecksCapability.FrontAppRead,
                availability = when {
                    frontApp.value != null -> CapabilityAvailability.Available
                    inputs.macCommandsReady -> CapabilityAvailability.Unknown
                    else -> CapabilityAvailability.Offline
                },
                reasonCode = frontApp.warningCode,
            ),
        )
    }
}

private fun String.toMacApplication(): MacApplication {
    val trimmed = trim()
    val normalized = trimmed.lowercase()
    val bundleSuffix = normalized
        .replace(Regex("[^a-z0-9]+"), ".")
        .trim('.')
        .ifBlank { "app" }
    return MacApplication(
        bundleId = "local.$bundleSuffix",
        displayName = trimmed,
        kind = when {
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
        },
    )
}

private fun <T> unavailableObserved(value: T? = null): Observed<T> = Observed(
    value = value,
    status = ObservationStatus.Unavailable,
    observedAtMillis = null,
    source = null,
    warningCode = null,
)
