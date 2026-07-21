package io.codecks.data

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

fun interface SshDiscovery {
    suspend fun scan(port: Int): List<String>
}

@Singleton
class LanSshDiscovery @Inject constructor() : SshDiscovery {
    override suspend fun scan(port: Int): List<String> = withContext(Dispatchers.IO) {
        val prefixes = localPrefixes()
        if (prefixes.isEmpty()) return@withContext emptyList()
        val semaphore = Semaphore(48)
        coroutineScope {
            prefixes.flatMap { prefix ->
                (1..254).map { suffix ->
                    async {
                        semaphore.withPermit {
                            val host = "$prefix$suffix"
                            host.takeIf { hasPort(host, port) }
                        }
                    }
                }
            }.awaitAll().filterNotNull().distinct().sorted()
        }
    }

    private fun hasPort(host: String, port: Int): Boolean = Socket().use { socket ->
        runCatching {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            true
        }.getOrDefault(false)
    }

    private fun localPrefixes(): List<String> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return emptyList()
        return Collections.list(interfaces)
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .mapNotNull(Inet4Address::getHostAddress)
            .filterNot { it.startsWith("127.") || it.startsWith("169.254.") }
            .mapNotNull { address ->
                address.substringBeforeLast('.', missingDelimiterValue = "")
                    .takeIf(String::isNotBlank)
                    ?.plus('.')
            }
            .distinct()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 240
    }
}
