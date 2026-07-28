package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.ControlId
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveActionReceipt
import io.codecks.domain.reactive.ReactiveCatalogControlSpec
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveControlProvider
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveStaleBehavior
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.SharedHidCommand
import io.codecks.domain.reactive.reactiveControlId
import io.codecks.domain.reactive.sha256Hex

class MediaReactiveControlProvider : ReactiveControlProvider {
    override val id: String = "media"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> {
        val media = state.media.value ?: return emptyList()
        if (state.media.status !in observableStatuses) return emptyList()

        val mediaLabel = listOfNotNull(
            media.app?.displayName,
            media.title,
            media.artist,
        ).joinToString(" · ").ifBlank { "media" }
        val confidence = observedConfidence(state.media.status, 72)
        val controls = mutableListOf<ReactiveControl>()

        controls += ReactiveControl(
            id = reactiveControlId(id, mediaLabel, "play_pause"),
            providerId = id,
            actionId = "play_pause",
            title = if (media.playing) "Pause" else "Play",
            subtitle = media.title,
            icon = ReactiveIcon.Generic,
            action = ReactiveAction.Hid(SharedHidCommand.MediaPlayPause),
            source = ReactiveControlSource.MediaState,
            basePriority = if (media.playing) 76 else 68,
            confidence = confidence,
            reason = if (media.playing) "media_playing" else "media_paused",
            explanation = "Media signal from $mediaLabel",
            requiredCapabilities = setOf(CodecksCapability.MediaInput),
            risk = ReactiveRisk.Safe,
            staleBehavior = ReactiveStaleBehavior.Downgrade,
            reversible = false,
            stateRevision = state.snapshotRevision,
            actionRevision = ActionRevision("media-${sha256Hex("play_pause|$mediaLabel|${media.playing}").take(64)}"),
            expiresAtMillis = state.capturedAtMillis + context.controlTtlMillis,
        )

        val muted = media.muted ?: state.system.value?.muted
        if (muted != null) {
            controls += catalogControl(
                providerId = id,
                appIdentity = mediaLabel,
                spec = ReactiveCatalogControlSpec(
                    actionId = "mute",
                    title = if (muted) "Unmute" else "Mute",
                    subtitle = "Toggle Mac audio",
                    icon = ReactiveIcon.Generic,
                    requiredCapabilities = setOf(CodecksCapability.MacCommand),
                ),
                source = ReactiveControlSource.MediaState,
                basePriority = 62,
                confidence = confidence,
                reason = if (muted) "mac_muted" else "mac_unmuted",
                explanation = "Audio mute state is known",
                state = state,
                context = context,
            )
        }

        return controls
    }
}

class WindowReactiveControlProvider : ReactiveControlProvider {
    override val id: String = "window"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> {
        val window = state.activeWindow.value ?: return emptyList()
        if (state.activeWindow.status !in observableStatuses) return emptyList()

        val app = window.app ?: state.frontApp.value
        val identity = listOfNotNull(app?.bundleId, app?.displayName, window.title, window.role)
            .joinToString(" ")
            .ifBlank { "window" }
        val confidence = observedConfidence(state.activeWindow.status, 64)
        val specs = listOf(
            ReactiveCatalogControlSpec(
                actionId = "mission",
                title = "Mission Control",
                subtitle = "See open windows",
                icon = ReactiveIcon.Generic,
                requiredCapabilities = setOf(CodecksCapability.WindowWrite),
            ),
            ReactiveCatalogControlSpec(
                actionId = "full_screen",
                title = "Full Screen",
                subtitle = "Toggle focused window",
                icon = ReactiveIcon.Generic,
                requiredCapabilities = setOf(CodecksCapability.WindowWrite),
            ),
        )

        return specs.map { spec ->
            catalogControl(
                providerId = id,
                appIdentity = identity,
                spec = spec,
                source = ReactiveControlSource.ConnectionState,
                basePriority = 58,
                confidence = confidence,
                reason = "active_window_available",
                explanation = "Focused window${window.title?.let { " “$it”" }.orEmpty()} is known",
                state = state,
                context = context,
            )
        }
    }
}

class UndoReceiptReactiveControlProvider(
    private val receipts: () -> List<ReactiveActionReceipt> = { emptyList() },
) : ReactiveControlProvider {
    override val id: String = "undo"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> = receipts()
        .asSequence()
        .mapNotNull { receipt ->
            val undo = receipt.undo ?: return@mapNotNull null
            if (undo.expiresAtMillis < nowMillis) return@mapNotNull null
            ReactiveControl(
                id = ControlId("reactive-undo-${receipt.id.value.take(64)}"),
                providerId = id,
                actionId = "undo_${receipt.controlId.value}",
                title = undo.label,
                subtitle = "Undo last completed control",
                icon = ReactiveIcon.Generic,
                action = undo.action,
                source = ReactiveControlSource.UndoReceipt,
                basePriority = 120,
                confidence = 100,
                reason = "undo_receipt_available",
                explanation = "Undo is tied to receipt ${receipt.id.value}",
                requiredCapabilities = emptySet(),
                risk = ReactiveRisk.Safe,
                staleBehavior = ReactiveStaleBehavior.Allow,
                reversible = false,
                stateRevision = state.snapshotRevision,
                actionRevision = ActionRevision("undo-${sha256Hex("${receipt.id.value}|${undo.token.value}").take(64)}"),
                expiresAtMillis = undo.expiresAtMillis,
            )
        }
        .toList()
}

private val observableStatuses = setOf(ObservationStatus.Fresh, ObservationStatus.Stale)

private fun observedConfidence(status: ObservationStatus, freshConfidence: Int): Int = when (status) {
    ObservationStatus.Fresh -> freshConfidence
    ObservationStatus.Stale -> (freshConfidence - 25).coerceAtLeast(0)
    ObservationStatus.PermissionNeeded,
    ObservationStatus.Unsupported,
    ObservationStatus.Unavailable,
    -> 0
}

private fun catalogControl(
    providerId: String,
    appIdentity: String,
    spec: ReactiveCatalogControlSpec,
    source: ReactiveControlSource,
    basePriority: Int,
    confidence: Int,
    reason: String,
    explanation: String,
    state: MacStateSnapshot,
    context: ReactiveTrackpadContext,
): ReactiveControl = ReactiveControl(
    id = reactiveControlId(providerId, appIdentity, spec.actionId),
    providerId = providerId,
    actionId = spec.actionId,
    title = spec.title,
    subtitle = spec.subtitle,
    icon = spec.icon,
    action = ReactiveAction.ExistingCatalog(spec.actionId),
    source = source,
    basePriority = basePriority,
    confidence = confidence,
    reason = reason,
    explanation = explanation,
    requiredCapabilities = spec.requiredCapabilities,
    risk = spec.risk,
    staleBehavior = ReactiveStaleBehavior.Downgrade,
    reversible = spec.reversible,
    stateRevision = state.snapshotRevision,
    actionRevision = spec.actionRevision(),
    expiresAtMillis = state.capturedAtMillis + context.controlTtlMillis,
)
