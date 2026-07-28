package io.codecks.platform.helper

import io.codecks.shared.protocol.HelperTrustState
import io.codecks.shared.protocol.ReactiveHelperRequest
import io.codecks.shared.protocol.ReceiptStatus
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ReactiveHelperLiveSmokeTest {
    @Test
    fun kotlinClientCanAuthenticateWithInstalledMacHelper() = runTest {
        assumeTrue(System.getenv("CODECKS_LIVE_HELPER_SMOKE") == "true")
        val configFile = File(
            System.getenv("CODECKS_HELPER_CONFIG")
                ?: "${System.getProperty("user.home")}/Library/Application Support/CodecksMacHelper/helper.json",
        )
        assumeTrue(configFile.exists())
        val config = JSONObject(configFile.readText())
        val secret = config.getString("sharedSecretHex").hexToBytes()
        val macId = config.getString("macId")
        val helperId = config.getString("helperId")
        val fingerprint = config.getString("publicKeyFingerprint")
        val port = config.optInt("port", 47321)
        val client = ReactiveHelperClient(
            transport = TcpReactiveHelperTransportFactory().connect(ReactiveHelperEndpoint("127.0.0.1", port)),
            deviceId = "android-jvm-live-smoke",
            credentials = ReactiveHelperCredentials(
                expectedMacId = macId,
                pinnedHelperIdentity = io.codecks.shared.protocol.HelperIdentityPin(
                    helperId = helperId,
                    publicKeyFingerprint = fingerprint,
                    issuedAtMillis = 0L,
                    trustState = HelperTrustState.Verified,
                ),
                sharedSecret = secret,
            ),
        )

        client.open("nonce-live-smoke")
        val response = client.request(ReactiveHelperRequest.BasicState)

        assertEquals(ReceiptStatus.Completed, response.status)
    }
}

private fun String.hexToBytes(): ByteArray =
    ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
