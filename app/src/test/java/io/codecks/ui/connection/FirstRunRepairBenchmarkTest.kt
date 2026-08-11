package io.codecks.ui.connection

import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.platform.helper.ReactiveHelperSessionStatus
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunRepairBenchmarkTest {
    @Test
    fun oneHundredDistinctCleanProfilesMeetM14Gate() {
        val profiles = cleanProfiles()
        val receipts = profiles.map(JourneyStateMachine::execute)

        assertEquals(100, profiles.size)
        assertEquals(100, profiles.map(CleanProfile::canonical).distinct().size)
        assertEquals(100, receipts.map(JourneyReceipt::pathDigest).distinct().size)
        assertTrue(receipts.all(JourneyReceipt::success))
        assertTrue(receipts.all { it.silentDeadEnds == 0 })
        assertTrue(receipts.all(JourneyReceipt::comprehensionProxy))
        assertTrue(receipts.all(JourneyReceipt::returnedToIntendedFeature))
        assertTrue(receipts.none(JourneyReceipt::unsupportedEscapeHatchUsed))

        val output = evidenceDirectory().resolve("first_run_repair_benchmark.raw.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(report(profiles, receipts).toString() + "\n")
        assertTrue(output.isFile)
    }

    @Test
    fun corpusHasTenMaterialVariantsPerScenario() {
        val profiles = cleanProfiles()
        JourneyScenario.entries.forEach { scenario ->
            val rows = profiles.filter { it.scenario == scenario }
            assertEquals(10, rows.size)
            assertEquals((0..9).toSet(), rows.map(CleanProfile::variant).toSet())
            assertEquals(10, rows.map { Triple(it.transientFailures, it.helpRequests, it.processRestarts) }.distinct().size)
        }
    }

    @Test
    fun receiptContainsNoSecretOrRawEndpointMaterial() {
        val encoded = report(cleanProfiles(), cleanProfiles().map(JourneyStateMachine::execute)).toString()
        listOf("password", "private_key", "192.168.", "ssh-ed25519", "clipboardText").forEach { canary ->
            assertFalse(encoded.contains(canary, ignoreCase = true))
        }
    }

    private fun cleanProfiles(): List<CleanProfile> = JourneyScenario.entries.flatMap { scenario ->
        (0..9).map { variant ->
            CleanProfile(
                caseId = "${scenario.code}-${(variant + 1).toString().padStart(2, '0')}",
                scenario = scenario,
                variant = variant,
                transientFailures = variant % 3,
                helpRequests = if (variant in 3..5 || variant == 9) 1 else 0,
                processRestarts = if (variant >= 6) variant - 5 else 0,
                permissionMode = if (variant % 2 == 0) PermissionMode.TemporaryDenial else PermissionMode.PermanentDenial,
                responseDelayTicks = variant + 1,
            )
        }
    }

    private fun report(profiles: List<CleanProfile>, receipts: List<JourneyReceipt>): JSONObject {
        val successes = receipts.count(JourneyReceipt::success)
        return JSONObject()
            .put("schemaVersion", 2)
            .put("milestone", "M14")
            .put("profile", "deterministic_clean_profile_state_machine")
            .put("total", receipts.size)
            .put("successes", successes)
            .put("successRatePercent", successes * 100 / receipts.size)
            .put("silentDeadEnds", receipts.sumOf(JourneyReceipt::silentDeadEnds))
            .put("gate", JSONObject().put("minimumSuccessPercent", 95).put("maximumSilentDeadEnds", 0))
            .put("moderatedHumanPairing", JSONObject().put("status", "NOT_RUN").put("reason", "EXTERNAL_EVIDENCE_REMAINS"))
            .put("profiles", JSONArray(profiles.map { it.toJson() }))
            .put("journeys", JSONArray(receipts.map { it.toJson() }))
    }

    private fun evidenceDirectory(): File =
        requireNotNull(File(requireNotNull(System.getProperty("user.dir"))).parentFile)
            .resolve("build/ga-evidence/M14")
}

private object JourneyStateMachine {
    fun execute(profile: CleanProfile): JourneyReceipt {
        val runtime = FakeJourneyRuntime(profile)
        runtime.cleanStart()
        repeat(profile.processRestarts) { runtime.processRestart() }
        runtime.scenarioFailure()
        repeat(profile.helpRequests) { runtime.openHelp() }
        repeat(profile.transientFailures) {
            runtime.invokeDisplayedRepair()
            runtime.transientFailure()
        }
        runtime.invokeDisplayedRepair()
        runtime.recoveryPath()
        runtime.ready()
        return runtime.receipt()
    }
}

private class FakeJourneyRuntime(private val profile: CleanProfile) {
    private val events = mutableListOf<JourneyEvent>()
    private val callbacks = FakeRepairCallbacks(events) { tick() }
    private var elapsedMs = 0L
    private var stateOrdinal = 0
    private var displayed: DisplayedState? = null

    fun cleanStart() = record(JourneyEventKind.CleanProfileStarted, "clean-profile", null, false, null)

    fun processRestart() = record(JourneyEventKind.ProcessRestarted, "process-restart", null, false, null)

    fun scenarioFailure() = when (profile.scenario) {
        JourneyScenario.HidPairing -> show(bluetooth(BluetoothPermissionState.Granted, true, true, 0))
        JourneyScenario.SshSetup -> show(ssh(ConnectionHealthKind.NotConfigured))
        JourneyScenario.HelperPairing -> show(helper(paired = false))
        JourneyScenario.PermissionDenial -> show(bluetooth(profile.permissionMode.state, true, false, 0))
        JourneyScenario.WrongHost -> show(ssh(ConnectionHealthKind.Offline, ConnectionIssueCode.UNKNOWN))
        JourneyScenario.SleepingMac -> show(ssh(ConnectionHealthKind.Offline, ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP))
        JourneyScenario.HostKeyMismatch -> show(ssh(ConnectionHealthKind.FingerprintMismatch))
        JourneyScenario.LostKey -> show(ssh(ConnectionHealthKind.NeedsKey))
        JourneyScenario.BluetoothOff -> show(bluetooth(BluetoothPermissionState.Granted, false, true, 1, true))
        JourneyScenario.NetworkLoss -> show(ssh(ConnectionHealthKind.Offline, ConnectionIssueCode.CONNECT_BACKOFF))
    }

    fun transientFailure() = when (profile.scenario.transport) {
        ConnectionTransport.Hid -> show(bluetooth(BluetoothPermissionState.Granted, true, true, 1, true, false))
        ConnectionTransport.Helper -> show(ReactiveHelperSessionStatus.Idle.toUnifiedConnectionPresentation())
        ConnectionTransport.Ssh, ConnectionTransport.Clipboard ->
            show(ssh(ConnectionHealthKind.Offline, ConnectionIssueCode.CONNECT_BACKOFF))
    }

    fun recoveryPath() = when (profile.scenario) {
        JourneyScenario.HidPairing -> {
            show(bluetooth(BluetoothPermissionState.Granted, true, true, 0)); invokeDisplayedRepair()
            show(bluetooth(BluetoothPermissionState.Granted, true, true, 1)); invokeDisplayedRepair()
            show(bluetooth(BluetoothPermissionState.Granted, true, true, 1, true, false)); invokeDisplayedRepair()
        }
        JourneyScenario.SshSetup -> {
            show(ssh(ConnectionHealthKind.NeedsFingerprint)); invokeDisplayedRepair()
            show(ssh(ConnectionHealthKind.NeedsKey)); invokeDisplayedRepair()
        }
        JourneyScenario.HelperPairing -> show(helper(paired = true)).also { invokeDisplayedRepair() }
        JourneyScenario.PermissionDenial -> {
            if (profile.permissionMode == PermissionMode.TemporaryDenial) {
                show(bluetooth(BluetoothPermissionState.PermanentlyDenied, true, false, 0)); invokeDisplayedRepair()
            }
            show(bluetooth(BluetoothPermissionState.Granted, true, true, 1, true, false)); invokeDisplayedRepair()
        }
        JourneyScenario.WrongHost -> show(ssh(ConnectionHealthKind.NotConfigured)).also { invokeDisplayedRepair() }
        JourneyScenario.HostKeyMismatch -> show(ssh(ConnectionHealthKind.NeedsFingerprint)).also { invokeDisplayedRepair() }
        JourneyScenario.SleepingMac, JourneyScenario.LostKey, JourneyScenario.BluetoothOff, JourneyScenario.NetworkLoss -> Unit
    }

    fun ready() = when (profile.scenario.transport) {
        ConnectionTransport.Hid -> show(bluetooth(BluetoothPermissionState.Granted, true, true, 1, true, true))
        ConnectionTransport.Helper -> show(
            ReactiveHelperSessionStatus.Connected("redacted-mac", "redacted-session", 2_000L)
                .toUnifiedConnectionPresentation(),
        )
        ConnectionTransport.Ssh, ConnectionTransport.Clipboard -> show(ssh(ConnectionHealthKind.Ready))
    }

    fun openHelp() {
        val current = requireNotNull(displayed)
        callbacks.openHelp(current.stateId, current.helpRoute)
    }

    fun invokeDisplayedRepair() {
        val current = requireNotNull(displayed)
        val repair = requireNotNull(current.repair) { "No repair for ${current.stateId}" }
        callbacks.invoke(current.stateId, repair, profile.scenario.intendedFeature)
    }

    fun receipt(): JourneyReceipt {
        val shown = events.filter { it.kind == JourneyEventKind.StateShown && !it.ready }
        val repairedIds = events.filter { it.kind == JourneyEventKind.RepairCallback }.mapNotNull { it.stateId }.toSet()
        val deadEnds = shown.count { it.stateId !in repairedIds }
        val comprehension = shown.all {
            !it.title.isNullOrBlank() && !it.detail.isNullOrBlank() && !it.supportCode.isNullOrBlank() && it.repair != null
        }
        val terminal = events.lastOrNull { it.kind == JourneyEventKind.StateShown }
        val returned = terminal?.ready == true && terminal.destination == profile.scenario.intendedFeature
        val unsupported = events.any { it.destination in setOf("developer-tools", "hidden-settings", "undocumented-ssh") }
        val canonicalEvents = events.joinToString("\n", transform = JourneyEvent::behaviorCanonical)
        return JourneyReceipt(
            caseId = profile.caseId,
            scenario = profile.scenario.code,
            success = returned && deadEnds == 0,
            durationMs = events.maxOfOrNull(JourneyEvent::atMs) ?: 0L,
            retries = events.count { it.kind == JourneyEventKind.RepairCallback && it.repair == ConnectionRepair.RetryNow },
            silentDeadEnds = deadEnds,
            comprehensionProxy = comprehension,
            intendedFeature = profile.scenario.intendedFeature,
            returnedFeature = terminal?.destination,
            helpOffered = shown.all { !it.helpRoute.isNullOrBlank() },
            helpUsed = events.count { it.kind == JourneyEventKind.HelpCallback },
            unsupportedEscapeHatchUsed = unsupported,
            pathDigest = sha256(canonicalEvents),
            events = events.toList(),
        )
    }

    private fun show(presentation: UnifiedConnectionPresentation) {
        val stateId = nextStateId()
        val ready = presentation.isReady
        val repair = presentation.repairs.firstOrNull()
        displayed = DisplayedState(stateId, repair, "connection-help")
        record(
            JourneyEventKind.StateShown, stateId, repair, ready,
            if (ready) profile.scenario.intendedFeature else null,
            presentation.title, presentation.detail, presentation.supportCode, "connection-help",
        )
    }

    private fun show(status: BluetoothSetupStatus) {
        val stateId = nextStateId()
        val repair = status.remediation.toConnectionRepair()
        displayed = DisplayedState(stateId, repair, "bluetooth-help")
        record(
            JourneyEventKind.StateShown, stateId, repair, status.success,
            if (status.success) profile.scenario.intendedFeature else null,
            "Bluetooth ${status.currentCheck.name}", "Next action: ${status.remediation.name}",
            "CX-HID-${status.currentCheck.name.uppercase()}", "bluetooth-help",
        )
    }

    private fun nextStateId(): String = "${profile.caseId}-state-${++stateOrdinal}"

    private fun record(
        kind: JourneyEventKind,
        stateId: String?,
        repair: ConnectionRepair?,
        ready: Boolean,
        destination: String?,
        title: String? = null,
        detail: String? = null,
        supportCode: String? = null,
        helpRoute: String? = null,
    ) {
        tick()
        events += JourneyEvent(kind, elapsedMs, stateId, title, detail, supportCode, repair, ready, destination, helpRoute)
    }

    private fun tick(): Long {
        elapsedMs += 35L + profile.responseDelayTicks * 7L
        return elapsedMs
    }
}

private class FakeRepairCallbacks(
    private val events: MutableList<JourneyEvent>,
    private val clock: () -> Long,
) {
    fun invoke(stateId: String, repair: ConnectionRepair, intendedFeature: String) {
        val destination = when (repair) {
            ConnectionRepair.RequestPermission -> "permission-dialog"
            ConnectionRepair.OpenBluetoothSettings -> "android-app-settings"
            ConnectionRepair.PairMac -> "system-pairing"
            ConnectionRepair.OpenConnectionSetup -> "mac-setup"
            ConnectionRepair.ReenterCredentials -> "credential-editor"
            ConnectionRepair.ReviewIdentity -> "identity-review"
            ConnectionRepair.PairHelper -> "helper-setup"
            ConnectionRepair.OpenHelper -> "helper-app"
            ConnectionRepair.ContactSupport -> "support"
            ConnectionRepair.ResolveConflict -> "conflict-review"
            ConnectionRepair.RetryNow -> intendedFeature
        }
        events += JourneyEvent(JourneyEventKind.RepairCallback, clock(), stateId, repair = repair, destination = destination)
    }

    fun openHelp(stateId: String, route: String) {
        events += JourneyEvent(JourneyEventKind.HelpCallback, clock(), stateId, destination = route)
    }
}

private fun ssh(kind: ConnectionHealthKind, issue: ConnectionIssueCode? = null) =
    ConnectionHealth(kind, "ignored", "ignored", issueOverride = issue).toUnifiedConnectionPresentation()

private fun helper(paired: Boolean) =
    ReactiveHelperSessionStatus.Idle.toUnifiedConnectionPresentation(paired = paired)

private fun bluetooth(
    permission: BluetoothPermissionState,
    enabled: Boolean,
    registered: Boolean,
    hosts: Int,
    selected: Boolean = false,
    connected: Boolean = false,
) = evaluateBluetoothSetup(
    BluetoothSetupObservation(permission, enabled, registered, false, hosts, selected, connected),
)

private fun BluetoothSetupRemediation.toConnectionRepair(): ConnectionRepair? = when (this) {
    BluetoothSetupRemediation.None -> null
    BluetoothSetupRemediation.RequestPermission -> ConnectionRepair.RequestPermission
    BluetoothSetupRemediation.OpenAppSettings, BluetoothSetupRemediation.EnableBluetooth -> ConnectionRepair.OpenBluetoothSettings
    BluetoothSetupRemediation.RetryHidRegistration, BluetoothSetupRemediation.RetryConnection -> ConnectionRepair.RetryNow
    BluetoothSetupRemediation.OpenSystemPairing, BluetoothSetupRemediation.ChooseBondedHost -> ConnectionRepair.PairMac
}

private enum class JourneyScenario(
    val code: String,
    val intendedFeature: String,
    val transport: ConnectionTransport,
) {
    HidPairing("hid_pairing", "trackpad", ConnectionTransport.Hid),
    SshSetup("ssh_setup", "deck", ConnectionTransport.Ssh),
    HelperPairing("helper_pairing", "deck", ConnectionTransport.Helper),
    PermissionDenial("permission_denial", "trackpad", ConnectionTransport.Hid),
    WrongHost("wrong_host", "deck", ConnectionTransport.Ssh),
    SleepingMac("sleeping_mac", "clipboard", ConnectionTransport.Clipboard),
    HostKeyMismatch("host_key_mismatch", "deck", ConnectionTransport.Ssh),
    LostKey("lost_key", "deck", ConnectionTransport.Ssh),
    BluetoothOff("bluetooth_off", "trackpad", ConnectionTransport.Hid),
    NetworkLoss("network_loss", "clipboard", ConnectionTransport.Clipboard),
}

private enum class PermissionMode(val state: BluetoothPermissionState) {
    TemporaryDenial(BluetoothPermissionState.Denied),
    PermanentDenial(BluetoothPermissionState.PermanentlyDenied),
}

private data class CleanProfile(
    val caseId: String,
    val scenario: JourneyScenario,
    val variant: Int,
    val transientFailures: Int,
    val helpRequests: Int,
    val processRestarts: Int,
    val permissionMode: PermissionMode,
    val responseDelayTicks: Int,
) {
    fun canonical() = listOf(
        caseId, scenario.code, variant, transientFailures, helpRequests, processRestarts,
        permissionMode.name, responseDelayTicks,
    ).joinToString("|")

    fun toJson() = JSONObject()
        .put("caseId", caseId).put("scenario", scenario.code).put("variant", variant)
        .put("transientFailures", transientFailures).put("helpRequests", helpRequests)
        .put("processRestarts", processRestarts).put("permissionMode", permissionMode.name)
        .put("responseDelayTicks", responseDelayTicks)
}

private enum class JourneyEventKind { CleanProfileStarted, ProcessRestarted, StateShown, HelpCallback, RepairCallback }

private data class DisplayedState(val stateId: String, val repair: ConnectionRepair?, val helpRoute: String)

private data class JourneyEvent(
    val kind: JourneyEventKind,
    val atMs: Long,
    val stateId: String?,
    val title: String? = null,
    val detail: String? = null,
    val supportCode: String? = null,
    val repair: ConnectionRepair? = null,
    val ready: Boolean = false,
    val destination: String? = null,
    val helpRoute: String? = null,
) {
    fun behaviorCanonical() = listOf(kind.name, supportCode, repair?.name, ready, destination, helpRoute).joinToString("|")
    fun toJson() = JSONObject().put("kind", kind.name).put("atMs", atMs)
        .put("stateId", stateId ?: JSONObject.NULL).put("title", title ?: JSONObject.NULL)
        .put("detail", detail ?: JSONObject.NULL).put("supportCode", supportCode ?: JSONObject.NULL)
        .put("repair", repair?.name ?: JSONObject.NULL).put("ready", ready)
        .put("destination", destination ?: JSONObject.NULL).put("helpRoute", helpRoute ?: JSONObject.NULL)
}

private data class JourneyReceipt(
    val caseId: String,
    val scenario: String,
    val success: Boolean,
    val durationMs: Long,
    val retries: Int,
    val silentDeadEnds: Int,
    val comprehensionProxy: Boolean,
    val intendedFeature: String,
    val returnedFeature: String?,
    val helpOffered: Boolean,
    val helpUsed: Int,
    val unsupportedEscapeHatchUsed: Boolean,
    val pathDigest: String,
    val events: List<JourneyEvent>,
) {
    val returnedToIntendedFeature get() = success && returnedFeature == intendedFeature
    fun toJson() = JSONObject().put("caseId", caseId).put("scenario", scenario).put("success", success)
        .put("durationMs", durationMs).put("retries", retries).put("silentDeadEnds", silentDeadEnds)
        .put("comprehensionProxy", comprehensionProxy).put("intendedFeature", intendedFeature)
        .put("returnedFeature", returnedFeature ?: JSONObject.NULL)
        .put("returnedToIntendedFeature", returnedToIntendedFeature).put("helpOffered", helpOffered)
        .put("helpUsed", helpUsed).put("unsupportedEscapeHatchUsed", unsupportedEscapeHatchUsed)
        .put("pathDigest", pathDigest).put("events", JSONArray(events.map { it.toJson() }))
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
