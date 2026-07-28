package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.AccessibilityDiscoveryRequest
import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.MonitorBrightnessRequest
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveControlProvider
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRequestProvenance
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveStaleBehavior
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.StateSource
import io.codecks.domain.reactive.reactiveControlId
import io.codecks.domain.reactive.sha256Hex

class MonitorBrightnessReactiveControlProvider(
    private val requests: () -> List<MonitorBrightnessRequest> = { emptyList() },
) : ReactiveControlProvider {
    override val id: String = "monitor_brightness"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> {
        if (!state.hasAvailable(CodecksCapability.MonitorBrightness)) return emptyList()
        val provenance = state.provenanceOrNull() ?: return emptyList()
        return requests()
            .filter { it.provenance == provenance }
            .take(2)
            .mapIndexed { index, request -> request.toControl(state, context, index) }
    }

    private fun MonitorBrightnessRequest.toControl(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        index: Int,
    ): ReactiveControl = ReactiveControl(
        id = reactiveControlId(id, "${state.macId.value}|$displayId|$percent", "brightness_$index"),
        providerId = id,
        actionId = "monitor_brightness_set",
        title = "Brightness $percent%",
        subtitle = "Supported display only",
        icon = ReactiveIcon.Generic,
        action = ReactiveAction.Helper(
            actionId = "monitor_brightness.set",
            arguments = mapOf(
                "displayId" to displayId,
                "percent" to percent.toString(),
                "adapterId" to adapterId,
            ),
        ),
        source = ReactiveControlSource.ConnectionState,
        basePriority = 38,
        confidence = 65,
        reason = "monitor_brightness_available",
        explanation = "Brightness adapter $adapterId advertised support for display $displayId",
        requiredCapabilities = setOf(CodecksCapability.MonitorBrightness),
        risk = ReactiveRisk.Review,
        staleBehavior = ReactiveStaleBehavior.Deny,
        reversible = false,
        stateRevision = state.snapshotRevision,
        actionRevision = ActionRevision("brightness-${sha256Hex("$displayId|$percent|$adapterId").take(64)}"),
        expiresAtMillis = state.capturedAtMillis + context.controlTtlMillis,
    )
}

class AccessibilityDiscoveryReactiveControlProvider(
    private val requests: () -> List<AccessibilityDiscoveryRequest> = { emptyList() },
) : ReactiveControlProvider {
    override val id: String = "accessibility_discovery"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> {
        if (!state.hasAvailable(CodecksCapability.AccessibilityDiscovery)) return emptyList()
        val provenance = state.provenanceOrNull() ?: return emptyList()
        return requests()
            .filter { it.provenance == provenance }
            .take(1)
            .map { request -> request.toControl(state, context) }
    }

    private fun AccessibilityDiscoveryRequest.toControl(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
    ): ReactiveControl = ReactiveControl(
        id = reactiveControlId(id, "${state.macId.value}|$frontAppBundleId", "discover_actions"),
        providerId = id,
        actionId = "accessibility_discover_actions",
        title = "Discover actions",
        subtitle = frontAppBundleId,
        icon = ReactiveIcon.Generic,
        action = ReactiveAction.Helper(
            actionId = "accessibility.discover",
            arguments = mapOf(
                "frontAppBundleId" to frontAppBundleId,
                "maxActions" to maxActions.toString(),
            ),
        ),
        source = ReactiveControlSource.ConnectionState,
        basePriority = 24,
        confidence = 45,
        reason = "accessibility_discovery_available",
        explanation = "Accessibility discovery is available but remains review-gated",
        requiredCapabilities = setOf(CodecksCapability.AccessibilityDiscovery),
        risk = ReactiveRisk.Private,
        staleBehavior = ReactiveStaleBehavior.Deny,
        reversible = false,
        stateRevision = state.snapshotRevision,
        actionRevision = ActionRevision("accessibility-${sha256Hex("$frontAppBundleId|$maxActions").take(64)}"),
        expiresAtMillis = state.capturedAtMillis + context.controlTtlMillis,
    )
}

private fun MacStateSnapshot.hasAvailable(capability: CodecksCapability): Boolean =
    !stale && source == StateSource.Helper &&
        capabilities.any { it.capability == capability && it.availability == CapabilityAvailability.Available }

private fun MacStateSnapshot.provenanceOrNull(): ReactiveRequestProvenance? {
    if (source != StateSource.Helper || stale) return null
    return ReactiveRequestProvenance(
        macId = macId,
        snapshotRevision = snapshotRevision,
        source = source,
        observedAtMillis = capturedAtMillis,
    )
}
