package io.codecks.data.reactive.helper

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.platform.helper.DiscoveredReactiveHelper
import io.codecks.platform.helper.ReactiveHelperDiscovery
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class MdnsReactiveHelperDiscovery @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReactiveHelperDiscovery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _helpers = MutableStateFlow<List<DiscoveredReactiveHelper>>(emptyList())
    override val helpers: StateFlow<List<DiscoveredReactiveHelper>> = _helpers
    private var listener: NsdManager.DiscoveryListener? = null

    override fun start() {
        if (listener != null) return
        val manager = context.getSystemService(NsdManager::class.java) ?: return
        val nextListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.orEmpty().contains(SERVICE_TYPE, ignoreCase = true)) return
                scope.launch {
                    manager.resolveHelper(serviceInfo)?.let(::upsert)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _helpers.value = _helpers.value.filterNot { it.serviceName == serviceInfo.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stop()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener = null
            }
        }
        listener = nextListener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, nextListener) }
            .onFailure { listener = null }
    }

    override fun stop() {
        val manager = context.getSystemService(NsdManager::class.java) ?: return
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        listener = null
        _helpers.value = emptyList()
    }

    private fun upsert(helper: DiscoveredReactiveHelper) {
        _helpers.value = _helpers.value
            .filterNot { it.serviceName == helper.serviceName }
            .plus(helper)
            .sortedBy { it.serviceName }
    }

    private suspend fun NsdManager.resolveHelper(serviceInfo: NsdServiceInfo): DiscoveredReactiveHelper? =
        suspendCancellableCoroutine { continuation ->
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress?.takeIf(String::isNotBlank)
                    val port = serviceInfo.port
                    val helper = if (host != null && port in 1..65_535) {
                        DiscoveredReactiveHelper(
                            serviceName = serviceInfo.serviceName,
                            host = host,
                            port = port,
                            protocolSchema = "reactive.v1",
                        )
                    } else {
                        null
                    }
                    if (continuation.isActive) continuation.resume(helper)
                }
            }
            runCatching { resolveService(serviceInfo, resolveListener) }
                .onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
        }

    private companion object {
        const val SERVICE_TYPE = "_codecks-reactive._tcp"
    }
}
