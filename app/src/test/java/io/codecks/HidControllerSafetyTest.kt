package io.codecks

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HidControllerSafetyTest {
    @Test
    fun teardownPathsReleaseInputsBeforeClosingTransport() {
        val source = File("src/main/java/io/codecks/HidController.java").readText()
        val closeIndex = source.indexOf("void close()")
        val disconnectIndex = source.indexOf("void disconnect()")
        val closeReleaseIndex = source.indexOf("releaseAllInputsNow();", closeIndex)
        val closeShutdownIndex = source.indexOf("tx.shutdownNow();", closeIndex)
        val disconnectReleaseIndex = source.indexOf("releaseAllInputsNow();", disconnectIndex)
        val disconnectTransportIndex = source.indexOf("hidDevice.disconnect(device);", disconnectIndex)

        assertTrue(closeIndex >= 0)
        assertTrue(disconnectIndex >= 0)
        assertTrue(source.contains("void releaseAllInputs()"))
        assertTrue(source.contains("private void releaseAllInputsNow()"))
        assertTrue(source.contains("private void invalidatePendingInputs()"))
        assertTrue(source.contains("generation != inputGeneration"))
        assertTrue(closeReleaseIndex >= 0)
        assertTrue(closeShutdownIndex >= 0)
        assertTrue(disconnectReleaseIndex >= 0)
        assertTrue(disconnectTransportIndex >= 0)
        assertTrue(closeReleaseIndex < closeShutdownIndex)
        assertTrue(disconnectReleaseIndex < disconnectTransportIndex)
    }
}
