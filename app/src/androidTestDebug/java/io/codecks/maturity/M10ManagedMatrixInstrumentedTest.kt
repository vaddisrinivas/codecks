package io.codecks.maturity

import android.Manifest
import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.codecks.BuildConfig
import io.codecks.MainActivity
import io.codecks.data.ConnectionTarget
import io.codecks.data.migrateConnectionTargetIdentities
import java.io.FileInputStream
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M10ManagedMatrixInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName

    @After
    fun restoreMutableSystemState() {
        shell("svc wifi enable")
        shell("svc data enable")
        shell("cmd appops set $packageName RUN_ANY_IN_BACKGROUND default")
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = android.os.LocaleList.getEmptyLocaleList()
        }
        context.getSystemService(UiModeManager::class.java)
            .setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
    }

    @Test
    fun installArtifactAndIdentityStayInTheIsolatedDebugLane() {
        assertEquals("app.codecks.debug", BuildConfig.APPLICATION_ID)
        assertEquals(BuildConfig.APPLICATION_ID, packageName)
        assertFalse(packageName == "app.codecks")
        assertEquals("1", shell("getprop ro.kernel.qemu").trim())

        val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
        assertNotNull(applicationInfo.sourceDir)
        assertTrue(applicationInfo.sourceDir.endsWith(".apk"))
        assertTrue(context.packageManager.getPackageInfo(packageName, 0).longVersionCode > 0)
    }

    @Test
    fun startupRotationRecreationAndLargeWindowBasicsDoNotCrash() {
        val expectedShape = InstrumentationRegistry.getArguments().getString("m10Shape")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertTrue(activity.resources.configuration.screenWidthDp > 0)
                assertTrue(activity.resources.configuration.screenHeightDp > 0)
                when (expectedShape) {
                    "compact" -> assertTrue(activity.resources.configuration.smallestScreenWidthDp < 400)
                    "standard" -> assertTrue(activity.resources.configuration.smallestScreenWidthDp in 400..599)
                    "tablet" -> assertTrue(activity.resources.configuration.smallestScreenWidthDp >= 600)
                }
                assertTrue(activity.window.decorView.isAttachedToWindow)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            instrumentation.waitForIdleSync()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            waitForOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            instrumentation.waitForIdleSync()
            waitForOrientation(scenario, Configuration.ORIENTATION_PORTRAIT)
        }
    }

    @Test
    fun persistedInstallMarkerAndLegacyTargetMigrationSurviveRecreation() {
        val preferences = context.getSharedPreferences("m10_matrix", Context.MODE_PRIVATE)
        val marker = "api-${Build.VERSION.SDK_INT}-${System.nanoTime()}"
        assertTrue(preferences.edit().putString("update_marker", marker).commit())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals(marker, preferences.getString("update_marker", null))
        }

        val legacyId = "mac_matrix_matrix_host_local_22"
        val opaqueId = "6fdb6838-546f-4728-a577-f4afca7be242"
        val migrated = migrateConnectionTargetIdentities(
            storedTargets = listOf(
                ConnectionTarget(
                    id = legacyId,
                    host = "matrix-host.local",
                    port = 22,
                    user = "matrix",
                    hasKey = false,
                    hostKey = "",
                ),
            ),
            legacyTarget = null,
            currentTargetId = legacyId,
            newId = { opaqueId },
        )
        assertEquals(opaqueId, migrated.currentTargetId)
        assertEquals(opaqueId, migrated.targets.single().id)
    }

    @Test
    fun localeAndDarkLightThemeChangesRemainCrashFree() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val uiMode = context.getSystemService(UiModeManager::class.java)
            uiMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals(
                Configuration.UI_MODE_NIGHT_YES,
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            )

            uiMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals(
                Configuration.UI_MODE_NIGHT_NO,
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            )
            if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    android.os.LocaleList.forLanguageTags("fr-FR")
                assertEquals("fr-FR", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
            } else {
                val localized = Configuration(context.resources.configuration).apply { setLocale(Locale.FRANCE) }
                assertEquals("fr", context.createConfigurationContext(localized).resources.configuration.locales[0].language)
            }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun offlinePermissionAndBackgroundRestrictionTogglesAreRecoverable() {
        try {
            shell("svc wifi disable")
            shell("svc data disable")
            assertTrue(shell("cmd wifi status").contains("disabled", ignoreCase = true))
            assertEquals("0", shell("settings get global mobile_data").trim())
            shell("cmd appops set $packageName RUN_ANY_IN_BACKGROUND ignore")
            assertTrue(shell("cmd appops get $packageName RUN_ANY_IN_BACKGROUND").contains("ignore"))

            if (Build.VERSION.SDK_INT >= 31) {
                instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BLUETOOTH_CONNECT)
                try {
                    assertEquals(
                        android.content.pm.PackageManager.PERMISSION_GRANTED,
                        instrumentation.context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT),
                    )
                } finally {
                    instrumentation.uiAutomation.dropShellPermissionIdentity()
                }
            }

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        } finally {
            shell("svc wifi enable")
            shell("svc data enable")
            shell("cmd appops set $packageName RUN_ANY_IN_BACKGROUND default")
        }
        assertTrue(shell("cmd wifi status").contains("enabled", ignoreCase = true))
        assertEquals("1", shell("settings get global mobile_data").trim())
        assertFalse(shell("cmd appops get $packageName RUN_ANY_IN_BACKGROUND").contains("ignore"))
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return try {
            FileInputStream(descriptor.fileDescriptor).bufferedReader().readText()
        } finally {
            descriptor.close()
        }
    }

    private fun waitForOrientation(scenario: ActivityScenario<MainActivity>, expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var actual = Configuration.ORIENTATION_UNDEFINED
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { actual = it.resources.configuration.orientation }
            if (actual == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(expected, actual)
    }
}
