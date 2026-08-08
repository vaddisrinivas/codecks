package io.codecks.commercialproof

import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.ArrayDeque

@RunWith(AndroidJUnit4::class)
class ManagedCommercialDarkInstrumentedTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation = instrumentation.uiAutomation
    private val targetPackage = "app.codecks"
    private val receipt = ManagedCommercialProofReceipt(instrumentation)

    private data class ShellResult(val output: String)

    private fun shell(command: String): ShellResult {
        val descriptor: ParcelFileDescriptor = uiAutomation.executeShellCommand(command)
        val output = descriptor.use {
            FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
        return ShellResult(output.trim())
    }

    private fun unsupported(result: ShellResult): Boolean =
        listOf("Unknown command", "unknown option", "Permission Denial", "can't find service").any {
            result.output.contains(it, ignoreCase = true)
        }

    private fun addNoCommercialMarkers(id: String, label: String, output: String) {
        val markers = listOf(
            "CommercialAuth",
            "CommercialSync",
            "CloudSync",
            "RemoteConfig",
            "app.codecks.test_backend",
            "codecks.invalid",
            "BillingClient",
            "com.android.vending.billing",
            "IntegrityService",
            "IntegrityManager",
            "UserMessagingPlatform",
            "MobileAds",
            "AdRequest",
            "FirebaseAuth",
            "CommercialTestOverrideMarker",
            "io.codecks.internalcommercial",
        )
        val hits = markers.filter { output.contains(it, ignoreCase = true) }
        receipt.add(
            id,
            if (hits.isEmpty()) ProofStatus.PASS else ProofStatus.FAIL,
            evidence(label, output),
            hits,
        )
    }

    private fun assertNoResolvedComponent(id: String, result: ShellResult) {
        val resolved = Regex("(?:^|\\s)app\\.codecks/[A-Za-z0-9_.$]+", RegexOption.IGNORE_CASE)
            .findAll(result.output)
            .map { it.value.trim() }
            .toList()
        receipt.add(
            id,
            when {
                unsupported(result) -> ProofStatus.NOT_RUN
                resolved.isNotEmpty() -> ProofStatus.FAIL
                else -> ProofStatus.PASS
            },
            evidence("package resolution", result.output),
            resolved,
        )
    }

    private fun visibleUiText(): String? {
        runCatching { uiAutomation.waitForIdle(200, 2_000) }
        val root = uiAutomation.rootInActiveWindow ?: return null
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        return buildString {
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                node.text?.let { append(it).append('\n') }
                node.contentDescription?.let { append(it).append('\n') }
                repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
            }
        }
    }

    @Test
    fun productionPlayArtifactRemainsCommerciallyDarkOnManagedEmulator() {
        try {
            assertEquals(targetPackage, instrumentation.targetContext.packageName)
            receipt.add("target.package", ProofStatus.PASS, "instrumentation target=$targetPackage")

            shell("logcat -c")
            addNoCommercialMarkers("runtime.logcat.before", "logcat baseline", shell("logcat -d -v threadtime").output)
            val packageBefore = shell("dumpsys package $targetPackage")
            if (packageBefore.output.contains("Package [$targetPackage]")) {
                addNoCommercialMarkers("package.before", "dumpsys package before", packageBefore.output)
            } else {
                receipt.add("package.before", ProofStatus.FAIL, evidence("dumpsys package", packageBefore.output))
            }

            val firstLaunch = shell(
                "am start -W -n $targetPackage/io.codecks.MainActivity " +
                    "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER",
            )
            val firstLaunchOk = firstLaunch.output.contains("Status: ok", ignoreCase = true)
            receipt.add(
                "lifecycle.first_launch",
                if (firstLaunchOk) ProofStatus.PASS else ProofStatus.FAIL,
                evidence("am start -W", firstLaunch.output),
            )

            val foreground = shell("dumpsys activity activities")
            val foregroundOk = foreground.output.lineSequence().any {
                (it.contains("mResumedActivity") || it.contains("topResumedActivity") ||
                    it.contains("ResumedActivity")) && it.contains("$targetPackage/")
            }
            receipt.add(
                "lifecycle.foreground",
                if (foregroundOk) ProofStatus.PASS else ProofStatus.FAIL,
                evidence("resumed activity", foreground.output.lineSequence().filter { it.contains("Resumed", true) }.joinToString("\n")),
            )

            shell("input keyevent KEYCODE_HOME")
            Thread.sleep(750)
            val background = shell("dumpsys activity activities")
            val remainsResumed = background.output.lineSequence().any {
                (it.contains("mResumedActivity") || it.contains("topResumedActivity") ||
                    it.contains("ResumedActivity")) && it.contains("$targetPackage/")
            }
            receipt.add(
                "lifecycle.background",
                if (remainsResumed) ProofStatus.FAIL else ProofStatus.PASS,
                evidence("resumed after HOME", background.output.lineSequence().filter { it.contains("Resumed", true) }.joinToString("\n")),
            )

            val relaunch = shell("am start -W -n $targetPackage/io.codecks.MainActivity")
            receipt.add(
                "lifecycle.relaunch",
                if (relaunch.output.contains("Status: ok", true)) ProofStatus.PASS else ProofStatus.FAIL,
                evidence("relaunch", relaunch.output),
            )
            receipt.add(
                "lifecycle.force_stop_cold_launch",
                ProofStatus.NOT_RUN,
                "Android instrumentation executes in the target process; force-stopping app.codecks would kill the proof runner. Host-side admission must supply this datum.",
            )

            val componentNames = listOf(
                "io.codecks.commercial.CommercialActivity",
                "io.codecks.billing.BillingActivity",
                "io.codecks.account.SignInActivity",
                "io.codecks.sync.CloudSyncActivity",
                "io.codecks.ads.AdActivity",
                "io.codecks.paywall.PaywallActivity",
                "io.codecks.internalcommercial.labui.CommercialLabActivity",
            )
            componentNames.forEachIndexed { index, component ->
                val attack = shell("am start -W -n $targetPackage/$component")
                val unexpectedlyStarted = attack.output.contains("Status: ok", true)
                receipt.add(
                    "attack.component.$index",
                    if (!unexpectedlyStarted && attack.output.isNotBlank()) ProofStatus.PASS else ProofStatus.FAIL,
                    evidence(component, attack.output),
                    if (unexpectedlyStarted) listOf(component) else emptyList(),
                )
            }

            val destinations = listOf(
                "account",
                "cloud-sync",
                "sync",
                "billing",
                "purchase",
                "subscription",
                "entitlement",
                "ads",
                "paywall",
                "sign-in",
                "privacy",
                "consent",
            )
            destinations.forEachIndexed { index, destination ->
                val resolve = shell(
                    "cmd package resolve-activity --brief --components " +
                        "-a android.intent.action.VIEW -c android.intent.category.BROWSABLE " +
                        "-d codecks://$destination -p $targetPackage",
                )
                assertNoResolvedComponent("attack.deeplink.$index", resolve)
            }

            val actions = listOf(
                "app.codecks.action.PURCHASE",
                "app.codecks.action.RESTORE_PURCHASES",
                "app.codecks.action.SIGN_IN",
                "app.codecks.action.CLOUD_SYNC",
            )
            actions.forEachIndexed { index, action ->
                val query = shell("cmd package query-receivers --brief -a $action -p $targetPackage")
                assertNoResolvedComponent("attack.broadcast.resolve.$index", query)
                val broadcast = shell("am broadcast -a $action -p $targetPackage")
                receipt.add(
                    "attack.broadcast.dispatch.$index",
                    if (broadcast.output.contains("Broadcast completed")) ProofStatus.PASS else ProofStatus.FAIL,
                    evidence(action, broadcast.output),
                )
            }

            val serviceActions = listOf(
                "app.codecks.action.BILLING_SERVICE",
                "app.codecks.action.COMMERCIAL_SYNC_SERVICE",
            )
            serviceActions.forEachIndexed { index, action ->
                val query = shell("cmd package query-services --brief -a $action -p $targetPackage")
                assertNoResolvedComponent("attack.service.resolve.$index", query)
                val start = shell("am startservice -a $action -p $targetPackage")
                val servicesAfter = shell("dumpsys activity services $targetPackage")
                val commercialService = Regex(
                    "(?:billing|commercial|cloud.?sync|purchase|subscription|entitlement|ads?)",
                    RegexOption.IGNORE_CASE,
                ).findAll(servicesAfter.output).map { it.value }.toList()
                receipt.add(
                    "attack.service.dispatch.$index",
                    when {
                        unsupported(query) || unsupported(start) || unsupported(servicesAfter) -> ProofStatus.NOT_RUN
                        commercialService.isNotEmpty() -> ProofStatus.FAIL
                        query.output.contains("No services found", true) -> ProofStatus.PASS
                        else -> ProofStatus.NOT_RUN
                    },
                    evidence(action, "query=${query.output}\nstart=${start.output}\nafter=${servicesAfter.output}"),
                    commercialService,
                )
            }

            val savedRouteAttacks = listOf(
                "--es route purchase --es source shortcut",
                "--es destination billing --es source notification",
                "--es commercial_destination account --es source saved_state",
            )
            savedRouteAttacks.forEachIndexed { index, extras ->
                val launch = shell("am start -W -n $targetPackage/io.codecks.MainActivity $extras")
                val hierarchy = visibleUiText()
                val forbiddenUi = listOf(
                    "purchase now",
                    "restore purchases",
                    "subscription",
                    "sign in",
                    "cloud sync",
                    "advertisement",
                ).filter { hierarchy?.contains(it, ignoreCase = true) == true }
                receipt.add(
                    "attack.saved_route.$index",
                    when {
                        !launch.output.contains("Status: ok", true) -> ProofStatus.FAIL
                        hierarchy == null -> ProofStatus.NOT_RUN
                        forbiddenUi.isNotEmpty() -> ProofStatus.FAIL
                        else -> ProofStatus.PASS
                    },
                    evidence("saved route UI", hierarchy.orEmpty()),
                    forbiddenUi,
                )
            }

            val evidenceCommands = linkedMapOf(
                "jobscheduler" to "dumpsys jobscheduler $targetPackage",
                "alarm" to "dumpsys alarm $targetPackage",
                "activity_services" to "dumpsys activity services $targetPackage",
                "activity_providers" to "dumpsys activity providers $targetPackage",
                "package.after" to "dumpsys package $targetPackage",
            )
            evidenceCommands.forEach { (id, command) ->
                val result = shell(command)
                if (!unsupported(result) && result.output.isNotBlank() &&
                    !result.output.contains("Invalid package", true)
                ) {
                    addNoCommercialMarkers("runtime.$id", command, result.output)
                } else {
                    receipt.add("runtime.$id", ProofStatus.NOT_RUN, evidence(command, result.output))
                }
            }

            val workManager = shell("dumpsys jobscheduler $targetPackage")
            receipt.add(
                "runtime.workmanager",
                if (!unsupported(workManager) && !workManager.output.contains("Invalid package", true)) {
                    ProofStatus.PASS
                } else {
                    ProofStatus.NOT_RUN
                },
                evidence("WorkManager/SystemJobService evidence", workManager.output),
            )
            addNoCommercialMarkers("runtime.processes", "ps -A", shell("ps -A").output)

            val packageFlags = packageBefore.output
            val allowsBackup = Regex("\\bALLOW_BACKUP\\b").containsMatchIn(packageFlags)
            receipt.add(
                "backup.installed.allow_backup",
                if (packageBefore.output.contains("Package [$targetPackage]") && !allowsBackup) {
                    ProofStatus.PASS
                } else {
                    ProofStatus.FAIL
                },
                evidence("installed package flags", packageFlags),
                if (allowsBackup) listOf("ALLOW_BACKUP") else emptyList(),
            )
            receipt.add(
                "backup.installed.data_transfer_rules",
                ProofStatus.NOT_RUN,
                "Binary installed resource decoding is unavailable inside the managed-emulator shell; static artifact proof remains authoritative.",
            )

            val uidResult = shell("cmd package list packages -U $targetPackage")
            val uid = Regex("uid:(\\d+)").find(uidResult.output)?.groupValues?.get(1)
            if (uid == null) {
                receipt.add("network.uid_netstats", ProofStatus.NOT_RUN, evidence("package UID", uidResult.output))
            } else {
                val netstats = shell("dumpsys netstats detail")
                val uidNetstats = netstats.output.lineSequence()
                    .filter { it.contains("uid=$uid") || it.contains("uid $uid") }
                    .joinToString("\n")
                if (!unsupported(netstats)) {
                    addNoCommercialMarkers("network.uid_netstats", "UID $uid netstats", uidNetstats)
                } else {
                    receipt.add("network.uid_netstats", ProofStatus.NOT_RUN, evidence("UID $uid netstats", netstats.output))
                }
            }
            receipt.add(
                "network.packet_capture",
                ProofStatus.NOT_RUN,
                "No packet-capture facility is available in the managed AOSP image; UID netstats and process log evidence are narrower evidence only.",
            )

            val pid = shell("pidof $targetPackage").output.trim().substringBefore(' ')
            val logcat = if (pid.matches(Regex("\\d+"))) {
                shell("logcat -d --pid=$pid -v threadtime")
            } else {
                ShellResult("target pid unavailable: $pid")
            }
            if (pid.matches(Regex("\\d+")) && !unsupported(logcat)) {
                addNoCommercialMarkers("runtime.target_logcat", "target PID logcat", logcat.output)
                val fatal = listOf("FATAL EXCEPTION", "ANR in $targetPackage").filter {
                    logcat.output.contains(it, ignoreCase = true)
                }
                receipt.add(
                    "runtime.crash_anr",
                    if (fatal.isEmpty()) ProofStatus.PASS else ProofStatus.FAIL,
                    evidence("target PID crash/ANR log", logcat.output),
                    fatal,
                )
            } else {
                receipt.add("runtime.target_logcat", ProofStatus.NOT_RUN, evidence("target PID logcat", logcat.output))
                receipt.add("runtime.crash_anr", ProofStatus.NOT_RUN, "target PID logcat unavailable")
            }
        } finally {
            val rendered = receipt.emit()
            val failures = receipt.failures()
            assertTrue(
                "Commercial managed proof failures: ${failures.joinToString { it.id }} receipt=$rendered",
                failures.isEmpty(),
            )
        }
    }
}
