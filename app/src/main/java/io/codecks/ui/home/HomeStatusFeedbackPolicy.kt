package io.codecks.ui.home

import io.codecks.domain.ActionStatus

internal sealed interface HomeStatusFeedback {
    data object None : HomeStatusFeedback

    data class TileOnly(
        val lingerMillis: Long = DEFAULT_TILE_LINGER_MILLIS,
    ) : HomeStatusFeedback

    data class Snackbar(
        val message: String,
        val actionLabel: String? = null,
    ) : HomeStatusFeedback
}

internal fun homeStatusFeedback(status: ActionStatus): HomeStatusFeedback =
    when (status) {
        ActionStatus.Idle,
        is ActionStatus.Running -> HomeStatusFeedback.None

        is ActionStatus.Failed -> HomeStatusFeedback.Snackbar(
            message = status.message,
            actionLabel = if (status.message == CONNECT_MAC_FIRST_MESSAGE) "Set up" else null,
        )

        is ActionStatus.Succeeded -> when (status.actionId) {
            "deck_remove" -> HomeStatusFeedback.Snackbar(
                message = status.message,
                actionLabel = "Undo",
            )

            "deck",
            "deck_undo",
            "ai_deck",
            "ai_draft" -> HomeStatusFeedback.Snackbar(message = status.message)

            else -> HomeStatusFeedback.TileOnly()
        }
    }

internal const val CONNECT_MAC_FIRST_MESSAGE = "Connect your Mac first"
private const val DEFAULT_TILE_LINGER_MILLIS = 1100L
