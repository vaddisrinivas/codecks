package io.codecks.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class MdnsFirstSshDiscovery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lanSshDiscovery: LanSshDiscovery,
) : SshDiscovery {
    override suspend fun scan(port: Int): List<String> {
        val mdnsHosts = discoverMdnsHosts(port)
        return mdnsHosts.ifEmpty { lanSshDiscovery.scan(port) }
    }

    private suspend fun discoverMdnsHosts(port: Int): List<String> = withContext(Dispatchers.Main.immediate) {
        val manager = context.getSystemService(NsdManager::class.java) ?: return@withContext emptyList()
        val discovered = mutableListOf<NsdServiceInfo>()
        val stopped = CompletableDeferred<Unit>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.orEmpty().contains(SSH_SERVICE_TYPE, ignoreCase = true)) {
                    discovered += serviceInfo
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                stopped.complete(Unit)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopped.complete(Unit)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopped.complete(Unit)
            }
        }

        runCatching { manager.discoverServices(SSH_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { return@withContext emptyList() }
        delay(DISCOVERY_WINDOW_MS)
        runCatching { manager.stopServiceDiscovery(listener) }
        withTimeoutOrNull(STOP_TIMEOUT_MS) { stopped.await() }

        discovered
            .distinctBy { it.serviceName }
            .mapNotNull { service -> manager.resolveHost(service, port) }
            .distinct()
            .sorted()
    }

    private suspend fun NsdManager.resolveHost(service: NsdServiceInfo, requestedPort: Int): String? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress?.takeIf(String::isNotBlank)
                    val portMatches = requestedPort <= 0 || serviceInfo.port == 0 || serviceInfo.port == requestedPort
                    if (continuation.isActive) continuation.resume(host.takeIf { portMatches })
                }
            }
            runCatching { resolveService(service, listener) }
                .onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
        }

    private companion object {
        const val SSH_SERVICE_TYPE = "_ssh._tcp"
        const val DISCOVERY_WINDOW_MS = 2_200L
        const val STOP_TIMEOUT_MS = 500L
    }
}
