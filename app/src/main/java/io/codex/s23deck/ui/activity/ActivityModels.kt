package io.codex.s23deck.ui.activity

import java.time.Instant

enum class ActivityResult {
    Running,
    Succeeded,
    Failed,
}

data class ActivityItem(
    val id: String,
    val actionId: String,
    val actionLabel: String,
    val result: ActivityResult,
    val occurredAt: Instant,
    val message: String = "",
)

data class ActivityUiState(
    val items: List<ActivityItem> = emptyList(),
    val clearing: Boolean = false,
)
