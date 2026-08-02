package io.codecks.ui.connection

import io.codecks.domain.connection.ConnectionIssueCode

data class CodecksReadiness(
    val macCommandsReady: Boolean,
    val trackpadReady: Boolean,
    val aiReady: Boolean,
    val title: String,
    val detail: String,
    val issueCodes: Set<ConnectionIssueCode> = emptySet(),
    val setupRepairTarget: SetupRepairTarget? = null,
    val setupCompletionConfirmed: Boolean = false,
) {
    val coreReady: Boolean
        get() = macCommandsReady && trackpadReady && setupCompletionConfirmed
}

fun codecksReadiness(
    connectionHealth: ConnectionHealth,
    hidHealth: HidHealth,
    aiReady: Boolean,
    setupCompletion: SetupCompletionEvaluation,
): CodecksReadiness {
    val macReady = connectionHealth.isReady
    val trackpadReady = hidHealth.canSendInput
    val repairTarget = (setupCompletion as? SetupCompletionEvaluation.REPAIR_REQUIRED)?.target
    val title = when {
        repairTarget != null -> "Resume setup · ${repairTarget.label}"
        macReady && trackpadReady -> "Codecks ready"
        macReady -> "Deck ready · Trackpad offline"
        trackpadReady -> "Trackpad ready · Mac controls offline"
        else -> "Finish Codecks setup"
    }
    val detail = buildList {
        repairTarget?.let { add("Next: ${it.label}") }
        add(if (macReady) "Mac controls ready" else "Mac controls: ${connectionHealth.statusLabel()}")
        add(if (trackpadReady) "Trackpad ready" else "Trackpad: ${hidHealth.statusLabel()}")
        add(if (aiReady) "AI ready" else "AI optional")
    }.joinToString(" · ")
    return CodecksReadiness(
        macCommandsReady = macReady,
        trackpadReady = trackpadReady,
        aiReady = aiReady,
        title = title,
        detail = detail,
        issueCodes = setOfNotNull(connectionHealth.issueCode, hidHealth.issueCode),
        setupRepairTarget = repairTarget,
        setupCompletionConfirmed = setupCompletion is SetupCompletionEvaluation.COMPLETE,
    )
}
