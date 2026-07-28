package io.codecks.platform.helper

import io.codecks.shared.protocol.REACTIVE_MAX_BODY_BYTES
import io.codecks.shared.protocol.ReactiveFrameCodec
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TcpReactiveHelperTransportFactory(
    private val connectTimeoutMillis: Int = 1_500,
    private val readTimeoutMillis: Int = 3_000,
    private val socketFactory: () -> Socket = { Socket() },
) : ReactiveHelperTransportFactory {
    override suspend fun connect(endpoint: ReactiveHelperEndpoint): ReactiveHelperTransport =
        withContext(Dispatchers.IO) {
            val socket = socketFactory()
            runCatching {
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMillis)
                socket.soTimeout = readTimeoutMillis
                TcpReactiveHelperTransport(socket)
            }.getOrElse { error ->
                runCatching { socket.close() }
                throw error
            }
        }
}

class TcpReactiveHelperTransport(
    private val socket: Socket,
) : ReactiveHelperTransport {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    override suspend fun exchange(frame: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        ReactiveFrameCodec.decode(frame)
        output.write(frame)
        output.flush()
        readFrame()
    }

    private fun readFrame(): ByteArray {
        val length = input.readInt()
        require(length in 1..REACTIVE_MAX_BODY_BYTES) { "invalid helper frame length" }
        val payload = ByteArray(length)
        input.readFully(payload)
        return ReactiveFrameCodec.encode(payload)
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            runCatching { socket.close() }
        }
    }
}
