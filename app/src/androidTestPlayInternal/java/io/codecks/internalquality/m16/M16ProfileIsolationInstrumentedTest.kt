package io.codecks.internalquality.m16

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M16ProfileIsolationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun exactTwentyImmutableIdentitiesAreDisjoint() {
        val identities = (1..4).flatMap { avd ->
            (1..5).map { slot -> M16Identities.resolve("m16Soak0${avd}Api35", slot) }
        }
        assertEquals(20, identities.map { it.profileId }.toSet().size)
        assertEquals(20, identities.map { it.seed }.toSet().size)
        assertEquals(20, identities.map { it.nonce }.toSet().size)
        assertEquals(5, identities.map { it.processName }.toSet().size)
    }

    @Test fun profileContextsCannotReadEachOthersStores() {
        val first = M16ProfileContext(context, "avd01-p01")
        val second = M16ProfileContext(context, "avd01-p02")
        first.getSharedPreferences("probe", Context.MODE_PRIVATE).edit().putString("owner", "avd01-p01").commit()
        assertEquals("avd01-p01", first.getSharedPreferences("probe", Context.MODE_PRIVATE).getString("owner", null))
        assertFalse(second.getSharedPreferences("probe", Context.MODE_PRIVATE).contains("owner"))
        assertNotEquals(first.filesDir.canonicalPath, second.filesDir.canonicalPath)
    }

    @Test fun manifestCarriesFiveExactNamedProcesses() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SERVICES)
        val expected = (1..5).associate { slot ->
            "io.codecks.internalquality.m16.M16ProfileService0$slot" to "app.codecks.internal:m16p0$slot"
        }
        val actual = packageInfo.services.orEmpty()
            .filter { it.name.startsWith("io.codecks.internalquality.m16.M16ProfileService") }
            .associate { it.name to it.processName }
        assertEquals(expected, actual)
        assertTrue(packageInfo.applicationInfo?.packageName == "app.codecks.internal")
    }

    @Test fun fiveLiveServicesOwnFivePidsLocksAndRealRepositoryStores(): Unit = runBlocking {
        println("M16_BINDING package=${context.packageName} flavor=playInternal project=:app api=${Build.VERSION.SDK_INT} fingerprintSha256=" +
            java.security.MessageDigest.getInstance("SHA-256").digest(Build.FINGERPRINT.toByteArray()).joinToString("") { "%02x".format(it) })
        val services = listOf(
            M16ProfileService01::class.java, M16ProfileService02::class.java, M16ProfileService03::class.java,
            M16ProfileService04::class.java, M16ProfileService05::class.java,
        )
        (1..5).forEach { M16ProfileContext(context, "avd01-p0$it").root().deleteRecursively() }
        try {
            services.forEach { service -> context.startForegroundService(Intent(context, service)
                .setAction(M16ProfileService.ACTION_START).putExtra(M16ProfileService.EXTRA_AVD_ID, "m16Soak01Api35")
                .putExtra(M16ProfileService.EXTRA_DURATION_HOURS, 2)) }
            val manager = context.getSystemService(ActivityManager::class.java)
            var probes = emptyList<org.json.JSONObject>()
            withTimeout(30_000) {
                while (probes.size != 5) {
                    val processes = manager.runningAppProcesses.orEmpty()
                        .filter { it.processName.matches(Regex("app\\.codecks\\.internal:m16p0[1-5]")) }
                    probes = (1..5).mapNotNull { slot -> runCatching {
                        context.contentResolver.openInputStream(Uri.parse(
                            "content://app.codecks.internal.m16evidence/profile/avd01-p0$slot/repo-probe.json"
                        ))!!.bufferedReader().use { org.json.JSONObject(it.readText()) }
                    }.getOrNull() }
                    if (processes.map { it.pid }.toSet().size != 5 || probes.size != 5) kotlinx.coroutines.delay(100)
                }
            }
            assertEquals((1..5).map { "avd01-p0$it" }, probes.map { it.getString("profileId") })
            assertEquals(5, probes.map { it.getString("originNonce") }.toSet().size)
            assertEquals(5, probes.map { it.getDouble("pointerSpeed") }.toSet().size)
            runCatching { M16ProfileLease.acquire(M16ProfileContext(context, "avd01-p01").root()) }
                .onSuccess { it.close(); throw AssertionError("live profile lock was not exclusive") }
        } finally {
            services.forEach { service -> runCatching { context.startForegroundService(Intent(context, service)
                .setAction(M16ProfileService.ACTION_STOP).putExtra(M16ProfileService.EXTRA_AVD_ID, "m16Soak01Api35")
                .putExtra(M16ProfileService.EXTRA_DURATION_HOURS, 2)) } }
            withTimeout(10_000) {
                val manager = context.getSystemService(ActivityManager::class.java)
                while (manager.runningAppProcesses.orEmpty().any { it.processName.matches(Regex("app\\.codecks\\.internal:m16p0[1-5]")) }) {
                    kotlinx.coroutines.delay(100)
                }
            }
            (1..5).forEach { M16ProfileContext(context, "avd01-p0$it").root().deleteRecursively() }
        }
    }
}
