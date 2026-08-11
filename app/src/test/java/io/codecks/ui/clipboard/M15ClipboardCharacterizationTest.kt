package io.codecks.ui.clipboard

import io.codecks.data.clipboard.ClipboardLastSyncCodec
import io.codecks.domain.clipboard.ClipboardBatteryPolicy
import io.codecks.domain.clipboard.ClipboardDirection
import io.codecks.domain.clipboard.ClipboardEndpoint
import io.codecks.domain.clipboard.ClipboardFailureCode
import io.codecks.domain.clipboard.ClipboardHash
import io.codecks.domain.clipboard.ClipboardReceipt
import io.codecks.domain.clipboard.ClipboardSessionPhase
import io.codecks.domain.clipboard.ClipboardSessionState
import io.codecks.domain.clipboard.ClipboardSourceId
import io.codecks.domain.clipboard.ClipboardSyncAction
import io.codecks.domain.clipboard.ClipboardSyncEngine
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.clipboard.ClipboardTerminalResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M15ClipboardCharacterizationTest {
    @Test
    fun visibleUnlockedSessionIsTheOnlyAutomaticReadAuthority() {
        val active = ClipboardSessionState(
            appForeground = true,
            surfaceVisible = true,
            deviceUnlocked = true,
        ).start(nowMillis = 1_000L, durationMillis = 10_000L)

        assertTrue(active.canReadPhoneClipboard)
        assertTrue(ClipboardBatteryPolicy.automaticPollingAllowed(active.phase, batterySaverActive = false))
        assertFalse(active.withEnvironment(appForeground = false, nowMillis = 2_000L).canReadPhoneClipboard)
        assertFalse(active.withEnvironment(surfaceVisible = false, nowMillis = 2_000L).canReadPhoneClipboard)
        assertFalse(active.withEnvironment(deviceUnlocked = false, nowMillis = 2_000L).canReadPhoneClipboard)
        assertEquals(ClipboardSessionPhase.Expired, active.evaluate(nowMillis = 11_001L).phase)
    }

    @Test
    fun batterySaverStopsAutomaticPollingWithoutChangingSessionAuthority() {
        val phase = ClipboardSessionPhase.ActiveVisible
        assertFalse(ClipboardBatteryPolicy.automaticPollingAllowed(phase, batterySaverActive = true))
        assertTrue(ClipboardBatteryPolicy.automaticPollingAllowed(phase, batterySaverActive = false))
    }

    @Test
    fun duplicateAndSelfEchoDoNotCreateSyncLoop() {
        val engine = ClipboardSyncEngine()
        val source = ClipboardSourceId("m15-source")
        val phone = engine.observe(ClipboardEndpoint.Phone, "same", source, 1_000L)
        val action = engine.decide(ClipboardSyncMode.PhoneToMac, 1_001L)
        engine.markApplied(action, 1_002L)
        val echo = engine.observe(ClipboardEndpoint.Mac, "same", source, 1_003L)
        val duplicate = engine.observe(ClipboardEndpoint.Mac, "same", source, 1_004L)

        assertTrue(phone.changed)
        assertTrue(echo.loopEcho)
        assertFalse(duplicate.changed)
        assertEquals(ClipboardSyncAction.None, engine.decide(ClipboardSyncMode.Bidirectional, 1_005L))
    }

    @Test
    fun simultaneousDifferentEditsProduceConflictInsteadOfLastWriterWin() {
        val engine = ClipboardSyncEngine()
        val phone = ClipboardSourceId("phone")
        val mac = ClipboardSourceId("mac")
        engine.observe(ClipboardEndpoint.Phone, "phone edit", phone, 2_000L)
        engine.observe(ClipboardEndpoint.Mac, "mac edit", mac, 2_001L)

        assertTrue(engine.decide(ClipboardSyncMode.Bidirectional, 2_002L) is ClipboardSyncAction.Conflict)
    }

    @Test
    fun largePayloadIsRepresentedByHashNotClipboardContent() {
        val payload = "x".repeat(1_000_000)
        val engine = ClipboardSyncEngine(maxHistory = 2)
        val observation = engine.observe(ClipboardEndpoint.Phone, payload, ClipboardSourceId("large"), 3_000L)

        assertEquals(64, observation.revision.hash.length)
        assertNotEquals(payload, observation.revision.hash)
        assertFalse(observation.snapshot.toString().contains(payload))
    }

    @Test
    fun lastSyncReceiptPersistsOnlyClosedMetadata() {
        val encoded = ClipboardLastSyncCodec.encode(
            ClipboardReceipt(
                direction = ClipboardDirection.PhoneToMac,
                terminalResult = ClipboardTerminalResult.Failure,
                failureCode = ClipboardFailureCode.Timeout,
                startedAtMillis = 4_000L,
                completedAtMillis = 4_100L,
            ),
        )

        assertEquals(ClipboardLastSyncCodec.ALLOWED_FIELDS, org.json.JSONObject(encoded).keys().asSequence().toSet())
        assertFalse(encoded.contains("clipboard", ignoreCase = true))
        assertFalse(encoded.contains("content", ignoreCase = true))
    }

    @Test
    fun ordinarySynchronizedTextRequestsHiddenSystemPreview() {
        assertTrue(clipboardSystemPreviewMustBeHidden())
        assertEquals(
            LEGACY_CLIP_DESCRIPTION_IS_SENSITIVE,
            clipboardSensitiveExtrasKey(28) { error("API 33 field must not resolve on API 28") },
        )
        assertEquals(
            LEGACY_CLIP_DESCRIPTION_IS_SENSITIVE,
            clipboardSensitiveExtrasKey(32) { error("API 33 field must not resolve on API 32") },
        )
        assertEquals(
            "api33-platform-key",
            clipboardSensitiveExtrasKey(33) { "api33-platform-key" },
        )
    }

    @Test
    fun retryBackoffIsBounded() {
        val delays = (1..20).map(ClipboardBatteryPolicy::retryDelayMillis)
        assertEquals(delays.sorted(), delays)
        assertEquals(120_000L, delays.last())
        assertTrue(delays.toSet().size < delays.size)
    }

    @Test
    fun processRecreationDoesNotRestoreClipboardReadAuthority() {
        val recreated = ClipboardSessionState().withEnvironment(
            appForeground = true,
            surfaceVisible = true,
            deviceUnlocked = true,
            nowMillis = 5_000L,
        )

        assertFalse(recreated.canReadPhoneClipboard)
        assertEquals(ClipboardSessionPhase.Inactive, recreated.phase)
    }

    @Test
    fun clipboardImplementationHasNoWorkerWakeLockOrBackgroundService() {
        val source = listOf(
            File("src/main/java/io/codecks/data/clipboard"),
            File("src/main/java/io/codecks/domain/clipboard"),
            File("src/main/java/io/codecks/ui/clipboard"),
            File("src/main/java/io/codecks/AppCompositionRoot.kt"),
        ).flatMap { root ->
            if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }.joinToString("\n") { it.readText() }

        listOf("WorkManager", "androidx.work", "WakeLock", "newWakeLock", "startForegroundService")
            .forEach { forbidden -> assertFalse("forbidden background primitive: $forbidden", source.contains(forbidden)) }
        assertTrue(source.contains("ACTION_POWER_SAVE_MODE_CHANGED"))
        assertTrue(source.contains("ACTION_BATTERY_SAVER_SETTINGS"))
        assertTrue(source.contains("ClipDescription.EXTRA_IS_SENSITIVE"))
        assertTrue(source.contains("android.content.extra.IS_SENSITIVE"))
        assertTrue(source.contains("clipboardSensitiveExtrasKey(Build.VERSION.SDK_INT)"))
        assertNull(ClipboardContentCanary.findIn(source))
    }

    private object ClipboardContentCanary {
        private const val VALUE = "M15_CLIPBOARD_CONTENT_CANARY"
        fun findIn(value: String): String? = VALUE.takeIf(value::contains)
    }
}
