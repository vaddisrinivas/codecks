package io.codecks.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionTargetIdentityInstrumentedTest {
    @Test
    fun savePersistsStableOpaqueIdentityForEndpoint() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DefaultConnectionRepository(context)
        val unique = UUID.randomUUID().toString().take(8)
        val host = "identity-$unique.studio.local"
        val user = "user-$unique"

        try {
            repository.save(host, 2222, user)
            val first = repository.savedTargets().single { it.host == host }

            repository.save(host, 2222, user)
            val afterSecondSave = repository.savedTargets().single { it.host == host }
            val afterRecreation = DefaultConnectionRepository(context)
                .savedTargets()
                .single { it.host == host }

            assertEquals(first.id, afterSecondSave.id)
            assertEquals(first.id, afterRecreation.id)
            assertEquals(first.id, UUID.fromString(first.id).toString())
            assertFalse(first.id.contains(host, ignoreCase = true))
            assertFalse(first.id.contains(user, ignoreCase = true))
            assertFalse(first.id.contains("2222"))
            assertNotEquals(legacyId(host, user, 2222), first.id)

            assertTrue(repository.removeTarget(legacyId(host, user, 2222)).isFailure)
            repository.removeTarget(first.id).getOrThrow()
            assertTrue(repository.savedTargets().none { it.host == host })
        } finally {
            repository.savedTargets()
                .firstOrNull { it.host == host }
                ?.let { repository.removeTarget(it.id) }
        }
    }

    private fun legacyId(host: String, user: String, port: Int): String =
        "mac_${user}_${host}_${port}"
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
}
