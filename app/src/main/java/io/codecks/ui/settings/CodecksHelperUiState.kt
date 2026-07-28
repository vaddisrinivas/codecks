package io.codecks.ui.settings

enum class CodecksHelperConnectionKind {
    Idle,
    Connecting,
    Connected,
    Failed,
}

data class CodecksHelperUiState(
    val pairedDisplayName: String? = null,
    val statusLabel: String = "Not paired",
    val statusDetail: String = "Install Codecks Mac helper, then import its pairing JSON.",
    val discoveredCount: Int = 0,
    val canConnect: Boolean = false,
    val canRunActions: Boolean = false,
) {
    val hasPairing: Boolean = pairedDisplayName != null
}

fun codecksHelperUiState(
    pairedDisplayName: String?,
    connectionKind: CodecksHelperConnectionKind,
    discoveredCount: Int,
    hasSavedEndpoint: Boolean = false,
    failureCode: String? = null,
): CodecksHelperUiState {
    val cleanName = pairedDisplayName?.takeIf { it.isNotBlank() }
    val hasConnectionTarget = discoveredCount > 0 || hasSavedEndpoint
    return when {
        cleanName == null -> CodecksHelperUiState(
            pairedDisplayName = null,
            statusLabel = "Not paired",
            statusDetail = "Install Codecks Mac helper, then import its pairing JSON.",
            discoveredCount = discoveredCount,
            canConnect = false,
            canRunActions = false,
        )

        connectionKind == CodecksHelperConnectionKind.Connected -> CodecksHelperUiState(
            pairedDisplayName = cleanName,
            statusLabel = "Connected",
            statusDetail = "Ready for Codecks helper actions on $cleanName.",
            discoveredCount = discoveredCount,
            canConnect = false,
            canRunActions = true,
        )

        connectionKind == CodecksHelperConnectionKind.Connecting -> CodecksHelperUiState(
            pairedDisplayName = cleanName,
            statusLabel = "Connecting",
            statusDetail = "Opening a secure Codecks helper session with $cleanName.",
            discoveredCount = discoveredCount,
            canConnect = false,
            canRunActions = false,
        )

        connectionKind == CodecksHelperConnectionKind.Failed -> CodecksHelperUiState(
            pairedDisplayName = cleanName,
            statusLabel = "Needs attention",
            statusDetail = "Codecks helper could not connect: ${failureCode ?: "unknown error"}.",
            discoveredCount = discoveredCount,
            canConnect = hasConnectionTarget,
            canRunActions = false,
        )

        hasConnectionTarget -> CodecksHelperUiState(
            pairedDisplayName = cleanName,
            statusLabel = "Ready to connect",
            statusDetail = if (discoveredCount > 0) {
                "Nearby Codecks helper found for $cleanName."
            } else {
                "Saved Codecks helper endpoint found for $cleanName."
            },
            discoveredCount = discoveredCount,
            canConnect = true,
            canRunActions = false,
        )

        else -> CodecksHelperUiState(
            pairedDisplayName = cleanName,
            statusLabel = "Paired",
            statusDetail = "Open Codecks Mac helper on your Mac; Codecks will connect when it appears.",
            discoveredCount = discoveredCount,
            canConnect = false,
            canRunActions = false,
        )
    }
}
