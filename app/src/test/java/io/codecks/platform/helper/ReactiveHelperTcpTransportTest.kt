package io.codecks.platform.helper

import io.codecks.shared.protocol.REACTIVE_MAX_BODY_BYTES
import io.codecks.shared.protocol.ReactiveFrameCodec
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.SocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveHelperTcpTransportTest {
    @Test
    fun exchangesSingleFramedRequestAndResponse() = runTest {
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            server.use {
                val socket = it.accept()
                socket.use {
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    val length = input.readInt()
                    val payload = ByteArray(length)
                    input.readFully(payload)
                    assertArrayEquals("request".encodeToByteArray(), payload)
                    output.write(ReactiveFrameCodec.encode("response".encodeToByteArray()))
                    output.flush()
                }
            }
        }

        val transport = TcpReactiveHelperTransportFactory()
            .connect(ReactiveHelperEndpoint("127.0.0.1", server.localPort))

        val response = transport.exchange(ReactiveFrameCodec.encode("request".encodeToByteArray()))

        assertArrayEquals("response".encodeToByteArray(), ReactiveFrameCodec.decode(response))
        transport.close()
        executor.shutdown()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
    }

    @Test
    fun rejectsOversizedResponseFrame() = runTest {
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            server.use {
                val socket = it.accept()
                socket.use {
                    DataInputStream(socket.getInputStream()).readInt()
                    DataOutputStream(socket.getOutputStream()).writeInt(REACTIVE_MAX_BODY_BYTES + 1)
                }
            }
        }

        val transport = TcpReactiveHelperTransportFactory()
            .connect(ReactiveHelperEndpoint("127.0.0.1", server.localPort))
        val result = runCatching {
            transport.exchange(ReactiveFrameCodec.encode("request".encodeToByteArray()))
        }

        assertTrue(result.isFailure)
        transport.close()
        executor.shutdown()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
    }

    @Test
    fun closesSocketWhenConnectFails() = runTest {
        val socket = TrackingSocket()
        val result = runCatching {
            TcpReactiveHelperTransportFactory(socketFactory = { socket })
                .connect(ReactiveHelperEndpoint("192.0.2.1", 9))
        }

        assertTrue(result.isFailure)
        assertTrue(socket.closed)
    }
}

private class TrackingSocket : Socket() {
    var closed = false
        private set

    override fun close() {
        closed = true
        super.close()
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        error("connect failed")
    }
}
