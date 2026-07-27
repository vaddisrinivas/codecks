package io.codecks.domain.reactive

import kotlinx.coroutines.flow.StateFlow

interface MacStateRepository {
    val state: StateFlow<MacStateSnapshot?>
    val connection: StateFlow<MacStateConnectionState>

    suspend fun refreshBasic(): MacStateRefreshResult
    suspend fun refreshDisplays(): MacStateRefreshResult
    suspend fun refreshClipboardMetadata(): MacStateRefreshResult
    suspend fun refreshMedia(): MacStateRefreshResult
    suspend fun inspectSelection(): MacStateRefreshResult

    fun start(visibility: TrackpadVisibility)
    fun stop()
}
