package io.codecks

import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class HidControllerSafetyTest {
    @Test
    fun confirmedStrokeAlwaysAttemptsReleaseWhenPressReportsFailure() {
        var releaseCalls = 0

        val accepted = HidController.executeConfirmedStroke(
            { false },
            {},
            {
                releaseCalls += 1
                true
            },
        )

        assertFalse(accepted)
        assertTrue(releaseCalls == 1)
    }

    @Test
    fun confirmedStrokeAlwaysReleasesAndPreservesPrimaryFailureWhenPressThrows() {
        var releaseCalls = 0
        val primary = IllegalStateException("press failed")

        val thrown = assertThrows(IllegalStateException::class.java) {
            HidController.executeConfirmedStroke(
                { throw primary },
                {},
                {
                    releaseCalls += 1
                    throw IllegalArgumentException("release failed")
                },
            )
        }

        assertTrue(thrown === primary)
        assertEquals(1, releaseCalls)
        assertEquals("release failed", thrown.suppressed.single().message)
    }

    @Test
    fun confirmedDeliveryRetriesReleaseWhenStrokeReleaseReportsFailure() = runTest {
        var fallbackReleaseCalls = 0

        val thrown = try {
            CompletableFuture.completedFuture(false).awaitConfirmedDelivery(
                failureMessage = "release was rejected",
                releaseAllInputs = { fallbackReleaseCalls += 1 },
            )
            fail("Expected confirmed delivery failure")
            error("unreachable")
        } catch (failure: IllegalStateException) {
            failure
        }

        assertEquals("release was rejected", thrown.message)
        assertEquals(1, fallbackReleaseCalls)
    }

    @Test
    fun confirmedDeliveryRetriesReleaseAndPreservesReleaseException() = runTest {
        var fallbackReleaseCalls = 0
        val releaseFailure = object : RuntimeException("release failed") {}
        val cleanupFailure = IllegalStateException("fallback release failed")
        val future = CompletableFuture<Boolean>().apply {
            completeExceptionally(releaseFailure)
        }

        val thrown = try {
            future.awaitConfirmedDelivery(
                failureMessage = "unused",
                releaseAllInputs = {
                    fallbackReleaseCalls += 1
                    throw cleanupFailure
                },
            )
            fail("Expected confirmed delivery failure")
            error("unreachable")
        } catch (failure: RuntimeException) {
            failure
        }

        assertTrue(thrown === releaseFailure)
        assertEquals(1, fallbackReleaseCalls)
        assertTrue(thrown.suppressed.single() === cleanupFailure)
    }

    @Test
    fun sourceContractPlacesInputReleaseBeforeTransportClose() {
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
