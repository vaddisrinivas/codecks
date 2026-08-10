package io.codecks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.codecks.core.reactive.ReactiveHelperActionExecution
import io.codecks.domain.smart.SmartMacId
import io.codecks.platform.helper.ReactiveHelperEndpoint
import io.codecks.platform.helper.ReactiveHelperSessionStatus
import io.codecks.platform.helper.StoredReactiveHelperIdentity
import io.codecks.shared.protocol.ReactiveHelperRequest
import io.codecks.ui.settings.CodecksHelperConnectionKind
import io.codecks.ui.settings.CodecksHelperUiState
import io.codecks.ui.settings.codecksHelperUiState
import io.codecks.ui.connection.toUnifiedConnectionPresentation
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class HelperRuntime(
    val binding: HelperFeatureBinding?,
    val uiState: CodecksHelperUiState,
    val importPairing: (String) -> Unit,
    val connect: () -> Unit,
    val runSpotlight: (String) -> Unit,
)

@Composable
internal fun rememberHelperRuntime(
    appContext: Context,
    bindings: AppFeatureBindings,
    currentRoute: NavKey,
    pendingPairingJson: String?,
    reactiveEnabled: Boolean,
    selectedMacId: SmartMacId?,
    scope: CoroutineScope,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onPendingPairingConsumed: () -> Unit,
): HelperRuntime {
    val helperRequired = AppBootstrapCoordinator.needsHelper(
        route = currentRoute,
        pendingPairing = pendingPairingJson != null,
        reactiveEnabled = reactiveEnabled,
    )
    val binding = remember(helperRequired, appContext, bindings.optional) {
        bindings.optional.bindForStartup(helperRequired) { appContext }
    }
    var identities by remember { mutableStateOf<List<StoredReactiveHelperIdentity>>(emptyList()) }

    fun refreshIdentities() {
        val identityStore = binding?.identityStore ?: return
        scope.launch {
            identities = withContext(Dispatchers.IO) { identityStore.identities() }
        }
    }

    val importPairing: (String) -> Unit = { payload ->
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { requireNotNull(binding).pairingImporter.importJson(payload) }
            }
            if (result.isSuccess) refreshIdentities()
            snackbarHostState.showSnackbar(
                result.fold(
                    onSuccess = { "Codecks helper paired: ${it.displayName}" },
                    onFailure = { "Helper pairing failed (CX-HLP-FAIL)" },
                ),
            )
        }
    }

    LaunchedEffect(binding) {
        val identityStore = binding?.identityStore ?: return@LaunchedEffect
        identities = withContext(Dispatchers.IO) { identityStore.identities() }
    }
    LaunchedEffect(pendingPairingJson, binding) {
        val payload = pendingPairingJson ?: return@LaunchedEffect
        val pairingImporter = binding?.pairingImporter ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { runCatching { pairingImporter.importJson(payload) } }
        if (result.isSuccess) refreshIdentities()
        snackbarHostState.showSnackbar(
            result.fold(
                onSuccess = { "Codecks helper paired: ${it.displayName}" },
                onFailure = { "Helper pairing failed (CX-HLP-FAIL)" },
            ),
        )
        onPendingPairingConsumed()
    }

    val sessionManager = binding?.sessionManager
    val discoveredHelpers by (binding?.discovery?.helpers ?: flowOf(emptyList()))
        .collectAsStateWithLifecycle(emptyList())
    val status by (sessionManager?.status ?: flowOf(ReactiveHelperSessionStatus.Idle))
        .collectAsStateWithLifecycle(ReactiveHelperSessionStatus.Idle)
    DisposableEffect(binding?.discovery) {
        val discovery = binding?.discovery
        discovery?.start()
        onDispose { discovery?.stop() }
    }

    fun endpoint(): ReactiveHelperEndpoint? {
        val stored = identities.firstOrNull()
        val storedHost = stored?.host
        val storedPort = stored?.port
        if (!storedHost.isNullOrBlank() && storedPort != null) {
            return ReactiveHelperEndpoint(storedHost, storedPort)
        }
        val discovered = discoveredHelpers.firstOrNull()
        return discovered?.let { ReactiveHelperEndpoint(it.host, it.port) }
    }

    LaunchedEffect(discoveredHelpers, selectedMacId, identities, status) {
        val macId = selectedMacId?.value ?: identities.firstOrNull()?.macId ?: return@LaunchedEffect
        when (status) {
            is ReactiveHelperSessionStatus.Connected,
            is ReactiveHelperSessionStatus.Connecting,
            is ReactiveHelperSessionStatus.Failed,
            -> return@LaunchedEffect
            else -> Unit
        }
        val helperEndpoint = endpoint() ?: return@LaunchedEffect
        val manager = sessionManager ?: return@LaunchedEffect
        runCatching { manager.connect(endpoint = helperEndpoint, macId = macId) }
    }

    val hasSavedEndpoint = identities.firstOrNull()?.let { identity ->
        !identity.host.isNullOrBlank() && identity.port != null
    } == true
    val helperPresentation = status.toUnifiedConnectionPresentation(
        paired = identities.isNotEmpty(),
        endpointAvailable = discoveredHelpers.isNotEmpty() || hasSavedEndpoint,
    )
    val uiState = codecksHelperUiState(
        pairedDisplayName = identities.firstOrNull()?.displayName,
        connectionKind = when (status) {
            is ReactiveHelperSessionStatus.Connected -> CodecksHelperConnectionKind.Connected
            is ReactiveHelperSessionStatus.Connecting -> CodecksHelperConnectionKind.Connecting
            is ReactiveHelperSessionStatus.Failed -> CodecksHelperConnectionKind.Failed
            ReactiveHelperSessionStatus.Idle -> CodecksHelperConnectionKind.Idle
        },
        discoveredCount = discoveredHelpers.size,
        hasSavedEndpoint = hasSavedEndpoint,
        presentation = helperPresentation,
    )
    val connect: () -> Unit = {
        scope.launch {
            val helperEndpoint = endpoint()
            val macId = selectedMacId?.value ?: identities.firstOrNull()?.macId
            when {
                macId == null -> snackbarHostState.showSnackbar("Pair Codecks helper first")
                helperEndpoint == null -> snackbarHostState.showSnackbar("Open Codecks Mac helper on your Mac")
                else -> {
                    val result = withContext(Dispatchers.IO) {
                        requireNotNull(sessionManager).connect(endpoint = helperEndpoint, macId = macId)
                    }
                    snackbarHostState.showSnackbar(
                        when (result) {
                            is ReactiveHelperSessionStatus.Connected -> "Codecks helper connected"
                            is ReactiveHelperSessionStatus.Connecting -> "Codecks helper connecting"
                            is ReactiveHelperSessionStatus.Failed -> {
                                val diagnostic = result.toUnifiedConnectionPresentation()
                                "${diagnostic.title} (${diagnostic.supportCode})"
                            }
                            ReactiveHelperSessionStatus.Idle -> "Codecks helper idle"
                        },
                    )
                }
            }
        }
    }
    val runSpotlight: (String) -> Unit = { query ->
        scope.launch {
            if (query.isBlank()) {
                snackbarHostState.showSnackbar("Enter a Mac search query")
                return@launch
            }
            val sanitizedQuery = query.take(120)
            val operationId = "codecks-spotlight-${UUID.randomUUID()}"
            val execution = withContext(Dispatchers.IO) {
                val actionClient = sessionManager?.actionClient?.value
                    ?: return@withContext ReactiveHelperActionExecution.Unsupported("helper_not_bound")
                actionClient.execute(
                    request = ReactiveHelperRequest.Execute(
                        actionId = "spotlight.search",
                        actionRevision = codecksSpotlightActionRevision(sanitizedQuery),
                        operationId = operationId,
                        idempotencyKey = operationId,
                        timeoutMillis = 10_000L,
                        cancellationToken = operationId,
                        arguments = mapOf("query" to sanitizedQuery, "maxResults" to "8"),
                    ),
                    deadlineMillis = System.currentTimeMillis() + 10_000L,
                )
            }
            snackbarHostState.showSnackbar(
                when (execution) {
                    is ReactiveHelperActionExecution.Succeeded -> {
                        val count = Regex("""^spotlight_results_(\d+)$""")
                            .matchEntire(execution.resultCode)?.groupValues?.get(1) ?: "?"
                        "Codecks found $count Mac matches"
                    }
                    is ReactiveHelperActionExecution.Failed -> "Codecks search failed: ${execution.errorCode}"
                    is ReactiveHelperActionExecution.Unsupported -> "Codecks helper unavailable: ${execution.reasonCode}"
                    is ReactiveHelperActionExecution.RequiresReview -> "Codecks search needs review: ${execution.reason}"
                    ReactiveHelperActionExecution.Expired -> "Codecks search timed out"
                },
            )
        }
    }

    return HelperRuntime(
        binding = binding,
        uiState = uiState,
        importPairing = importPairing,
        connect = connect,
        runSpotlight = runSpotlight,
    )
}
