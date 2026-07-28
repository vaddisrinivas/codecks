package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveControlProvider
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRequestProvenance
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveStaleBehavior
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.SafeSftpTransferRequest
import io.codecks.domain.reactive.SpotlightSearchRequest
import io.codecks.domain.reactive.StateSource
import io.codecks.domain.reactive.reactiveControlId
import io.codecks.domain.reactive.sha256Hex

class SpotlightSftpReactiveControlProvider(
    private val spotlightRequests: () -> List<SpotlightSearchRequest> = { emptyList() },
    private val transferRequests: () -> List<SafeSftpTransferRequest> = { emptyList() },
) : ReactiveControlProvider {
    override val id: String = "spotlight_sftp"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> {
        if (state.source !in setOf(StateSource.Helper, StateSource.SshProbe) || state.stale) return emptyList()
        val provenance = ReactiveRequestProvenance(
            macId = state.macId,
            snapshotRevision = state.snapshotRevision,
            source = state.source,
            observedAtMillis = state.capturedAtMillis,
        )
        return buildList {
            if (state.hasAvailable(CodecksCapability.SpotlightSearch)) {
                spotlightRequests()
                    .filter { it.provenance == provenance }
                    .take(3)
                    .forEachIndexed { index, request ->
                        add(request.toControl(state, context, index))
                    }
            }
            if (state.hasAvailable(CodecksCapability.SftpTransfer)) {
                transferRequests()
                    .filter { it.provenance == provenance }
                    .take(2)
                    .forEachIndexed { index, request ->
                        add(request.toControl(state, context, index))
                    }
            }
        }
    }

    private fun SpotlightSearchRequest.toControl(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        index: Int,
    ): ReactiveControl = ReactiveControl(
        id = reactiveControlId(id, "${state.macId.value}|spotlight|${query.fingerprint()}", "spotlight_$index"),
        providerId = id,
        actionId = "spotlight_preview",
        title = "Search Mac",
        subtitle = "Preview up to $maxResults Spotlight results",
        icon = ReactiveIcon.Finder,
        action = ReactiveAction.SpotlightPreview(this),
        source = ReactiveControlSource.ConnectionState,
        basePriority = 54,
        confidence = 74,
        reason = "spotlight_available",
        explanation = "Spotlight search is advertised by ${provenance.source}",
        requiredCapabilities = setOf(CodecksCapability.SpotlightSearch),
        risk = ReactiveRisk.Private,
        staleBehavior = ReactiveStaleBehavior.Deny,
        reversible = false,
        stateRevision = state.snapshotRevision,
        actionRevision = ActionRevision("spotlight-${query.fingerprint().take(64)}"),
        expiresAtMillis = state.capturedAtMillis + context.controlTtlMillis,
    )

    private fun SafeSftpTransferRequest.toControl(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        index: Int,
    ): ReactiveControl = ReactiveControl(
        id = reactiveControlId(
            id,
            "${state.macId.value}|sftp|${localPath.fingerprint()}|${remotePath.fingerprint()}",
            "sftp_$index",
        ),
        providerId = id,
        actionId = "sftp_transfer_request",
        title = "Transfer file",
        subtitle = "${roots.localRootId} ↔ ${roots.remoteRootId}",
        icon = ReactiveIcon.Finder,
        action = ReactiveAction.SftpTransferRequest(this),
        source = ReactiveControlSource.ConnectionState,
        basePriority = 46,
        confidence = 70,
        reason = "sftp_available",
        explanation = "SFTP transfer is allowlist-bounded and advertised by ${provenance.source}",
        requiredCapabilities = setOf(CodecksCapability.SftpTransfer),
        risk = ReactiveRisk.Review,
        staleBehavior = ReactiveStaleBehavior.Deny,
        reversible = false,
        stateRevision = state.snapshotRevision,
        actionRevision = ActionRevision("sftp-${localPath.fingerprint().take(32)}-${remotePath.fingerprint().take(31)}"),
        expiresAtMillis = state.capturedAtMillis + context.controlTtlMillis,
    )
}

private fun MacStateSnapshot.hasAvailable(capability: CodecksCapability): Boolean =
    capabilities.any { it.capability == capability && it.availability == CapabilityAvailability.Available }

private fun String.fingerprint(): String = sha256Hex(this)
