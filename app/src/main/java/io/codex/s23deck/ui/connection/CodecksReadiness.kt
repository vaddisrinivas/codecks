package io.codex.s23deck.ui.connection

data class CodecksReadiness(
    val macCommandsReady: Boolean,
    val trackpadReady: Boolean,
    val aiReady: Boolean,
    val title: String,
    val detail: String,
) {
    val coreReady: Boolean get() = macCommandsReady && trackpadReady
}

fun codecksReadiness(
    connectionHealth: ConnectionHealth,
    hidHealth: HidHealth,
    aiReady: Boolean,
): CodecksReadiness {
    val macReady = connectionHealth.isReady
    val trackpadReady = hidHealth.canSendInput
    val title = when {
        macReady && trackpadReady -> "Codecks ready"
        macReady -> "Deck ready · Trackpad offline"
        trackpadReady -> "Trackpad ready · Mac commands offline"
        else -> "Finish Codecks setup"
    }
    val detail = buildList {
        add(if (macReady) "Mac commands ready" else "Mac commands: ${connectionHealth.statusLabel()}")
        add(if (trackpadReady) "Trackpad ready" else "Trackpad: ${hidHealth.statusLabel()}")
        add(if (aiReady) "AI ready" else "AI optional")
    }.joinToString(" · ")
    return CodecksReadiness(macReady, trackpadReady, aiReady, title, detail)
}
