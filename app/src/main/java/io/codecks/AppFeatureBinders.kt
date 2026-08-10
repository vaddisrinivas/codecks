package io.codecks

import android.content.Context
import android.provider.Settings
import dagger.Lazy
import io.codecks.core.actions.ActionRunner
import io.codecks.core.reactive.ConnectionRepositoryReactiveSftpTransferClient
import io.codecks.core.reactive.DefaultReactiveActionExecutor
import io.codecks.core.reactive.StateFlowReactiveHelperActionClient
import io.codecks.core.reactive.defaultReactiveTrackpadEngine
import io.codecks.core.reactive.reactiveActionRevision
import io.codecks.data.ActionRepository
import io.codecks.data.CodecksBackupRepository
import io.codecks.data.ConnectionRepository
import io.codecks.data.reactive.helper.ReactiveHelperPairingImporter
import io.codecks.data.reactive.LiveMacStateRepository
import io.codecks.data.reactive.state.ConnectionRepositorySshMacStateSource
import io.codecks.data.reactive.state.StateFlowReactiveHelperClientMacStateSource
import io.codecks.data.ai.AndroidSecureApiKeyStore
import io.codecks.domain.ai.AiProviderCatalog
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.reactive.InMemoryReactiveReceiptStore
import io.codecks.domain.reactive.ReactiveEngine
import io.codecks.platform.helper.ReactiveHelperDiscovery
import io.codecks.platform.helper.ReactiveHelperIdentityStore
import io.codecks.platform.helper.ReactiveHelperSecretStore
import io.codecks.platform.helper.ReactiveHelperSessionManager
import io.codecks.platform.helper.TcpReactiveHelperTransportFactory

internal data class CoreFeatureBindings(
    val hidRepository: HidRepository,
    val actionRunner: ActionRunner,
    val actionRepository: ActionRepository,
    val connectionRepository: ConnectionRepository,
    val deviceRepository: DeviceRepository,
    val backupRepository: CodecksBackupRepository,
)

internal class OptionalFeatureBinders(
    private val helperDiscovery: Lazy<ReactiveHelperDiscovery>,
    private val helperIdentityStore: Lazy<ReactiveHelperIdentityStore>,
    private val helperSecretStore: Lazy<ReactiveHelperSecretStore>,
    private val helperPairingImporter: Lazy<ReactiveHelperPairingImporter>,
) {
    fun bindForStartup(
        helperRequired: Boolean,
        context: () -> Context,
    ): HelperFeatureBinding? = if (helperRequired) bindHelper(context()) else null

    fun bindHelper(context: Context): HelperFeatureBinding {
        val identityStore = helperIdentityStore.get()
        val secretStore = helperSecretStore.get()
        val androidId = Settings.Secure
            .getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty()
            .ifBlank { "unknown" }
        return HelperFeatureBinding(
            discovery = helperDiscovery.get(),
            identityStore = identityStore,
            pairingImporter = helperPairingImporter.get(),
            sessionManager = ReactiveHelperSessionManager(
                identityStore = identityStore,
                secretStore = secretStore,
                transportFactory = TcpReactiveHelperTransportFactory(),
                deviceId = "android-$androidId",
            ),
        )
    }

    fun bindReactive(core: CoreFeatureBindings, helper: HelperFeatureBinding): ReactiveFeatureBinding {
        val receipts = InMemoryReactiveReceiptStore()
        return ReactiveFeatureBinding(
            macStateRepository = LiveMacStateRepository(
                helperSource = StateFlowReactiveHelperClientMacStateSource(helper.sessionManager.client),
                sshSource = ConnectionRepositorySshMacStateSource(core.connectionRepository),
            ),
            engine = defaultReactiveTrackpadEngine(
                actionRevisions = core.actionRepository.allActions().associate { action ->
                    action.id to action.reactiveActionRevision()
                },
                receipts = receipts::all,
            ),
            executor = DefaultReactiveActionExecutor(
                actionRepository = core.actionRepository,
                actionRunner = core.actionRunner,
                hidRepository = core.hidRepository,
                receiptStore = receipts,
                helperActionClient = StateFlowReactiveHelperActionClient(helper.sessionManager.actionClient),
                sftpTransferClient = ConnectionRepositoryReactiveSftpTransferClient(core.connectionRepository),
            ),
        )
    }

    suspend fun isAiProviderReady(context: Context): Boolean {
        val keyStore = AndroidSecureApiKeyStore(context)
        return AiProviderCatalog.all.any { spec -> keyStore.hasKey(spec.providerId) }
    }
}

internal data class HelperFeatureBinding(
    val discovery: ReactiveHelperDiscovery,
    val identityStore: ReactiveHelperIdentityStore,
    val pairingImporter: ReactiveHelperPairingImporter,
    val sessionManager: ReactiveHelperSessionManager,
)

internal data class ReactiveFeatureBinding(
    val macStateRepository: LiveMacStateRepository,
    val engine: ReactiveEngine,
    val executor: DefaultReactiveActionExecutor,
)

internal data class AppFeatureBindings(
    val core: CoreFeatureBindings,
    val optional: OptionalFeatureBinders,
)
