package io.codecks

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidSessionServiceOwnershipTest {
    @Test
    fun serviceOwnsReceiversButRepositoryAloneOwnsKeepalive() {
        val service = File("src/main/java/io/codecks/HidSessionService.kt").readText()

        assertFalse(service.contains("keepAliveJob"))
        assertTrue(service.contains("if (!receiverRegistered)"))
        assertTrue(service.contains("if (!activityCallbacksRegistered)"))
        assertTrue(service.contains("unregisterReceiver(systemEventReceiver)"))
        assertTrue(service.contains("unregisterActivityLifecycleCallbacks(activityCallbacks)"))
    }

    @Test
    fun repositoryOwnsOneSerializedConsumerAndIdempotentReconnectLoop() {
        val repository = File("src/main/java/io/codecks/HidRepository.kt").readText()

        assertEquals(
            1,
            Regex(Regex.escape("for (event in controlEvents)")).findAll(repository).count(),
        )
        assertEquals(
            1,
            Regex(Regex.escape("reconnectJob = scope.launch")).findAll(repository).count(),
        )
        assertTrue(repository.contains("if (reconnectJob?.isActive == true) return"))
        assertTrue(repository.contains("Channel<HidControlEvent>(Channel.UNLIMITED)"))
    }

    @Test
    fun processRehydrationAddsNoBootReceiver() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val service = File("src/main/java/io/codecks/HidSessionService.kt").readText()
        val receiverBlocks = Regex("""<receiver\b[\s\S]*?</receiver>""")
            .findAll(manifest)
            .map { it.value }
            .toList()

        assertTrue(receiverBlocks.none { it.contains("BOOT_COMPLETED") })
        assertTrue(receiverBlocks.none { it.contains("LOCKED_BOOT_COMPLETED") })
        assertFalse(service.contains("ACTION_BOOT_COMPLETED"))
    }

    @Test
    fun rehydrationPreferencesContainOnlyVersionedDesiredState() {
        val repository = File("src/main/java/io/codecks/HidRepository.kt").readText()

        assertTrue(repository.contains("\"hid_rehydration\""))
        assertTrue(repository.contains("\"desired_connection_v1\""))
        assertFalse(repository.contains("rehydrationPrefs.edit().putString(PREF_SELECTED_HOST"))
        assertFalse(repository.contains("rehydrationPrefs.edit().putString(\"hostname\""))
        assertFalse(repository.contains("rehydrationPrefs.edit().putString(\"credential\""))
    }
}
