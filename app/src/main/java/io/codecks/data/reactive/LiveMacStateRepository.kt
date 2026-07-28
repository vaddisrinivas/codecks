package io.codecks.data.reactive

import io.codecks.data.reactive.state.HelperMacStateSource
import io.codecks.data.reactive.state.SshMacStateSource
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
import io.codecks.domain.reactive.markStale
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.StateSource
import io.codecks.domain.reactive.TrackpadVisibility
import io.codecks.domain.reactive.toMacStateSnapshot
import io.codecks.shared.protocol.ReactiveHelperBasicState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

data class LiveMacStateInputs(
    val selectedMacId: String?,
    val macCommandsReady: Boolean,
    val macInputConnected: Boolean,
    val activeMacApp: String?,
)

class LiveMacStateRepository(
    private val helperSource: HelperMacStateSource? = null,
    private val sshSource: SshMacStateSource? = null,
    private val refreshTimeoutMillis: Long = 750L,
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
        val selectedMacId = inputs.selectedMacId
        if (selectedMacId.isNullOrBlank()) {
            return MacStateRefreshResult.Skipped("no_selected_mac")
        }
        _connection.value = MacStateConnectionState.Connecting(preferredSource())
        return refreshFromSources(selectedMacId).getOrElse { error ->
            val reasonCode = error.toReasonCode()
            markCurrentStaleOrOffline(selectedMacId, reasonCode)
            MacStateRefreshResult.Failed(
                reasonCode = reasonCode,
                retryable = true,
            )
        }
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
            source = StateSource.LocalCache,
            freshnessMillis = 3_000L,
            stale = frontAppObserved.status == ObservationStatus.Stale,
            warningCode = frontAppObserved.warningCode,
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

    private suspend fun refreshFromSources(selectedMacId: String): Result<MacStateRefreshResult> {
        val helper = helperSource
        if (helper?.connected == true) {
            val helperResult = runCatching {
                withTimeout(refreshTimeoutMillis) {
                    helper.refreshBasicState(nowMillis() + refreshTimeoutMillis)
                }
            }
            helperResult.getOrNull()?.let { basicState ->
                return Result.success(applyBasicState(basicState, StateSource.Helper))
            }
        }

        val ssh = sshSource
        if (ssh != null) {
            val sshResult = runCatching {
                withTimeout(refreshTimeoutMillis) {
                    ssh.refreshBasicState(selectedMacId)
                }
            }
            sshResult.getOrNull()?.let { basicState ->
                return Result.success(applyBasicState(basicState, StateSource.SshProbe))
            }
            if (helper?.connected == true) {
                return Result.failure(sshResult.exceptionOrNull() ?: helperFailure())
            }
        }

        if (helper?.connected == true) {
            return Result.failure(helperFailure())
        }

        rebuild()
        return Result.success(
            MacStateRefreshResult.Succeeded(
                source = StateSource.LocalCache,
                updatedFields = setOf(MacStateField.FrontApp, MacStateField.Capabilities),
                snapshotRevision = _state.value?.snapshotRevision,
            ),
        )
    }

    private fun applyBasicState(
        basicState: ReactiveHelperBasicState,
        source: StateSource,
    ): MacStateRefreshResult {
        val nextRevision = maxOf(revision + 1L, basicState.snapshotRevision)
        revision = nextRevision
        _state.value = basicState
            .copy(snapshotRevision = nextRevision)
            .toMacStateSnapshot(nowMillis(), source)
        _connection.value = if (_state.value?.stale == true) {
            MacStateConnectionState.Degraded(source, "stale_state")
        } else {
            MacStateConnectionState.Connected(source)
        }
        return MacStateRefreshResult.Succeeded(
            source = source,
            updatedFields = setOf(
                MacStateField.FrontApp,
                MacStateField.ActiveWindow,
                MacStateField.Displays,
                MacStateField.Capabilities,
            ),
            snapshotRevision = nextRevision,
        )
    }

    private fun markCurrentStaleOrOffline(
        selectedMacId: String,
        reasonCode: String,
    ) {
        val now = nowMillis()
        val current = _state.value
        if (current != null) {
            revision = maxOf(revision + 1L, current.snapshotRevision + 1L)
            _state.value = current.markStale(now, reasonCode).copy(snapshotRevision = revision)
            _connection.value = MacStateConnectionState.Degraded(current.source, reasonCode)
            return
        }
        revision += 1L
        _state.value = offlineSnapshot(selectedMacId, revision, now, reasonCode)
        _connection.value = MacStateConnectionState.Disconnected(reasonCode)
    }

    private fun preferredSource(): StateSource? = when {
        helperSource?.connected == true -> StateSource.Helper
        sshSource != null -> StateSource.SshProbe
        else -> StateSource.LocalCache
    }

    private fun offlineSnapshot(
        selectedMacId: String,
        snapshotRevision: Long,
        now: Long,
        reasonCode: String,
    ): MacStateSnapshot = MacStateSnapshot(
        macId = MacId(selectedMacId),
        snapshotRevision = snapshotRevision,
        capturedAtMillis = now,
        frontApp = unavailableObserved(warningCode = reasonCode),
        activeWindow = unavailableObserved(warningCode = reasonCode),
        displays = unavailableObserved(emptyList(), reasonCode),
        cursor = unavailableObserved(warningCode = reasonCode),
        selection = unavailableObserved(MacSelection.None, reasonCode),
        clipboard = unavailableObserved(
            MacClipboardMetadata(
                kind = MacClipboardKind.Unknown,
                byteSizeBucket = null,
                safePreview = null,
                changedAtMillis = null,
            ),
            reasonCode,
        ),
        media = unavailableObserved(
            MacMediaState(
                app = null,
                title = null,
                artist = null,
                playing = false,
                muted = null,
            ),
            reasonCode,
        ),
        system = unavailableObserved(
            MacSystemState(
                focusedSpaceLabel = null,
                volumePercent = null,
                muted = null,
            ),
            reasonCode,
        ),
        meeting = unavailableObserved(
            MacMeetingState(
                app = null,
                inMeeting = false,
                microphoneMuted = null,
                cameraOn = null,
            ),
            reasonCode,
        ),
        latestScreenshot = unavailableObserved(
            MacScreenshotState(
                path = null,
                capturedAtMillis = null,
            ),
            reasonCode,
        ),
        capabilities = buildCapabilities(null),
        source = StateSource.LocalCache,
        freshnessMillis = 0L,
        stale = true,
        warningCode = reasonCode,
    )

    private fun buildCapabilities(frontApp: Observed<MacApplication>?): Set<CapabilityState> = buildSet {
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
                    frontApp?.value != null -> CapabilityAvailability.Available
                    inputs.macCommandsReady -> CapabilityAvailability.Unknown
                    else -> CapabilityAvailability.Offline
                },
                reasonCode = frontApp?.warningCode,
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

private fun <T> unavailableObserved(
    value: T? = null,
    warningCode: String? = null,
): Observed<T> = Observed(
    value = value,
    status = ObservationStatus.Unavailable,
    observedAtMillis = null,
    source = null,
    warningCode = warningCode,
)

private fun Throwable.toReasonCode(): String = when (this) {
    is kotlinx.coroutines.TimeoutCancellationException -> "state_refresh_timeout"
    else -> message?.takeIf { it.isNotBlank() }?.toReasonToken() ?: "state_refresh_failed"
}

private fun String.toReasonToken(): String =
    lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "state_refresh_failed" }

private fun helperFailure(): Throwable = IllegalStateException("helper_basic_state_failed")
