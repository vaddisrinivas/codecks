package io.codecks.internalquality.m16

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.BatteryManager
import android.os.Debug
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.compose.ui.geometry.Offset
import io.codecks.core.trackpad.TrackpadGestureEngine
import io.codecks.core.trackpad.TrackpadSettingsRepository
import io.codecks.data.ActionCatalog
import io.codecks.data.DefaultActionRepository
import io.codecks.data.DefaultConnectionRepository
import io.codecks.data.automation.DefaultAutomationRepository
import io.codecks.data.clipboard.ClipboardSettingsRepository
import io.codecks.domain.clipboard.ClipboardEndpoint
import io.codecks.domain.clipboard.ClipboardSourceId
import io.codecks.domain.clipboard.ClipboardSyncEngine
import io.codecks.domain.clipboard.ClipboardSyncMode
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.Closeable
import java.nio.channels.FileLock
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import kotlin.coroutines.coroutineContext

private const val PACKAGE = "app.codecks.internal"
private const val WINDOW_MILLIS = 60L * 60L * 1_000L
private const val MIN_ACTIVE_MILLIS = 50L * 60L * 1_000L
private const val OP_INTERVAL_MILLIS = 30_000L
private const val MAX_LEDGER_BYTES = 32L * 1024L * 1024L
private const val MAX_FAILURE_PACKETS = 64
private val PROFILE_ID = Regex("avd0[1-4]-p0[1-5]")
private val AVD_ID = Regex("m16Soak0[1-4]Api35")

/** Isolates fixed-name production DataStores/SharedPreferences without changing them. */
internal class M16ProfileContext(base: Context, val profileId: String) : ContextWrapper(base) {
    private val root = File(base.filesDir, "m16/profiles/$profileId").apply {
        check(canonicalPath.startsWith(File(base.filesDir, "m16/profiles").canonicalPath + File.separator))
        mkdirs()
    }
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = root
    override fun getCacheDir(): File = File(root, "cache").apply(File::mkdirs)
    override fun getDatabasePath(name: String): File = File(root, "databases/$name").also { it.parentFile?.mkdirs() }
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        baseContext.getSharedPreferences("m16_${profileId}_$name", mode)
    fun root(): File = root
}

internal data class M16Identity(
    val avdId: String,
    val profileId: String,
    val processName: String,
    val seed: String,
    val nonce: String,
)

internal class M16ProfileLease private constructor(
    private val file: RandomAccessFile,
    private val lock: FileLock,
) : Closeable {
    override fun close() {
        lock.release()
        file.close()
    }
    companion object {
        fun acquire(root: File): M16ProfileLease {
            val file = RandomAccessFile(File(root, "profile.lock"), "rw")
            val lock = file.channel.tryLock() ?: run { file.close(); error("duplicate_profile_lease") }
            return M16ProfileLease(file, lock)
        }
    }
}

internal object M16Identities {
    private fun digest(label: String, length: Int): String = MessageDigest.getInstance("SHA-256")
        .digest(label.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(length)

    fun resolve(avdId: String, slot: Int): M16Identity {
        require(AVD_ID.matches(avdId))
        require(slot in 1..5)
        val avd = avdId.removePrefix("m16Soak").removeSuffix("Api35").toInt()
        val id = "avd%02d-p%02d".format(avd, slot)
        return M16Identity(
            avdId = avdId,
            profileId = id,
            processName = "$PACKAGE:m16p%02d".format(slot),
            seed = digest("codecks-m16-seed-v1:$id", 16),
            nonce = digest("codecks-m16-nonce-v1:$id", 32),
        )
    }
}

private class DurableProfileLedger(private val root: File, private val identity: M16Identity) {
    private val ledger = File(root, "ledger.jsonl")
    private val checkpoint = File(root, "checkpoint.json")
    private var headHash = "0".repeat(64)

    @Synchronized fun append(event: JSONObject) {
        val closed = event.apply {
            put("profileId", identity.profileId)
            put("processName", identity.processName)
            put("pid", Process.myPid())
            put("originNonce", identity.nonce)
            put("previousHash", headHash)
        }
        checkPrivacy(closed)
        val eventHash = sha256(closed.toString())
        closed.put("eventHash", eventHash)
        val encoded = closed.toString() + "\n"
        check(ledger.length() + encoded.toByteArray().size <= MAX_LEDGER_BYTES) { "ledger_cap" }
        FileOutputStream(ledger, true).use { out ->
            out.write(encoded.toByteArray())
            out.fd.sync()
        }
        headHash = eventHash
    }

    @Synchronized fun checkpoint(value: JSONObject) {
        value.put("ledgerHeadHash", headHash)
        val candidate = File(root, "checkpoint.next")
        FileOutputStream(candidate).use { out ->
            out.write(value.toString().toByteArray())
            out.fd.sync()
        }
        check(candidate.renameTo(checkpoint)) { "checkpoint_rename" }
        val descriptor = Os.open(root.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }

    fun readCheckpoint(): JSONObject? = checkpoint.takeIf(File::isFile)?.let { JSONObject(it.readText()) }

    fun verifyResume(checkpoint: JSONObject) {
        check(ledger.isFile && ledger.length() in 1..MAX_LEDGER_BYTES) { "resume_ledger_missing" }
        var lastAckSeen = checkpoint.optString("lastAckId").isBlank()
        var prefixSeen = false
        var previousHash = "0".repeat(64)
        ledger.forEachLine { line ->
            val event = JSONObject(line)
            check(event.getString("profileId") == identity.profileId) { "ledger_profile_mismatch" }
            check(event.getString("processName") == identity.processName) { "ledger_process_mismatch" }
            check(event.getString("originNonce") == identity.nonce) { "ledger_origin_mismatch" }
            check(event.getString("previousHash") == previousHash) { "ledger_chain_previous" }
            val recorded = event.remove("eventHash") as String
            check(sha256(event.toString()) == recorded) { "ledger_chain_hash" }
            checkPrivacy(event)
            previousHash = recorded
            if (recorded == checkpoint.getString("ledgerHeadHash")) prefixSeen = true
            if (event.optString("ackId") == checkpoint.optString("lastAckId")) lastAckSeen = true
        }
        check(lastAckSeen) { "checkpoint_ack_missing" }
        check(prefixSeen) { "checkpoint_not_ledger_prefix" }
        headHash = previousHash
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun checkPrivacy(value: Any?) {
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                check(!key.matches(Regex("(?i).*(secret|token|password|clipboard(text|content)|private.?key|username|userpath).*"))) { "ledger_privacy_key" }
                checkPrivacy(value.get(key))
            }
            is String -> {
                val macHomePrefix = "/" + "Users/"
                check(!Regex("(?i)(${Regex.escape(macHomePrefix)}|/home/|BEGIN [A-Z ]*PRIVATE KEY|bearer\\s|password=|token=)").containsMatchIn(value)) { "ledger_privacy_value" }
            }
        }
    }
}

abstract class M16ProfileService(private val slot: Int) : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = AtomicBoolean(false)
    private var worker: Job? = null
    @Volatile private var publicStatus = JSONObject().put("state", "idle").toString()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        check(packageName == PACKAGE) { "wrong_package" }
        val expectedProcess = "$PACKAGE:m16p%02d".format(slot)
        val actualProcess = File("/proc/self/cmdline").readText().trimEnd('\u0000')
        check(actualProcess == expectedProcess) { "wrong_process" }
        val avdId = intent?.getStringExtra(EXTRA_AVD_ID).orEmpty()
        val identity = M16Identities.resolve(avdId, slot)
        val durationHours = intent?.getIntExtra(EXTRA_DURATION_HOURS, 0) ?: 0
        require(durationHours == 2 || durationHours == 168) { "duration_not_allowed" }
        check(identity.processName == actualProcess)
        when (intent?.action) {
            ACTION_STOP -> stopWorker("operator_stop")
            ACTION_START, ACTION_RESUME -> startWorker(identity, durationHours, resume = intent.action == ACTION_RESUME)
            else -> error("unknown_action")
        }
        return START_NOT_STICKY
    }

    private fun startWorker(identity: M16Identity, durationHours: Int, resume: Boolean) {
        if (!running.compareAndSet(false, true)) {
            if (resume) return
            error("duplicate_worker")
        }
        createNotificationChannel()
        startForeground(1616 + slot, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Codecks autonomous proxy")
            .setContentText(identity.profileId)
            .build())
        val profileContext = M16ProfileContext(this, identity.profileId)
        val lease = M16ProfileLease.acquire(profileContext.root())
        val ledger = DurableProfileLedger(profileContext.root(), identity)
        publicStatus = JSONObject().put("state", "starting").put("profileId", identity.profileId)
            .put("processName", identity.processName).put("pid", Process.myPid())
            .put("originNonce", identity.nonce).toString()
        worker = scope.launch {
            try {
                runSoak(profileContext, identity, ledger, durationHours, resume)
            } finally {
                publicStatus = JSONObject(publicStatus).put("state", "stopped").toString()
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.also { job -> job.invokeOnCompletion { lease.close() } }
    }

    private suspend fun runSoak(
        context: M16ProfileContext,
        identity: M16Identity,
        ledger: DurableProfileLedger,
        durationHours: Int,
        resume: Boolean,
    ) {
        val bootId = File("/proc/sys/kernel/random/boot_id").readText().trim()
        val previous = ledger.readCheckpoint()
        if (resume) {
            check(previous != null) { "resume_without_checkpoint" }
            check(previous.getString("bootId") == bootId) { "boot_changed" }
            ledger.verifyResume(previous)
        } else {
            check(previous == null) { "start_requires_clean_profile_or_resume" }
        }
        var attemptedWindows = previous?.optInt("attemptedWindows", 0) ?: 0
        var eligibleWindows = previous?.optInt("eligibleWindows", 0) ?: 0
        var windowStartElapsed = previous?.optLong("windowStartElapsed", 0L)?.takeIf { it > 0L }
        var windowStartWall = previous?.optLong("windowStartWall", 0L)?.takeIf { it > 0L }
        var sequence = previous?.optInt("sequence", 0) ?: 0
        var totalSequence = previous?.optInt("totalSequence", 0) ?: 0
        var activeMillis = previous?.optLong("activeMillis", 0L) ?: 0L
        var lastAckElapsed = previous?.optLong("lastAckElapsed", 0L) ?: 0L
        val priorPid = previous?.optInt("pid", -1) ?: -1
        val priorWall = previous?.optLong("lastWall", 0L) ?: 0L
        var pendingExitDelta = if (resume) applicationExitReasons(identity.processName, priorWall, priorPid) else emptyExitReasons()
        val categories = linkedSetOf<String>().apply {
            previous?.optJSONArray("categories")?.let { array -> repeat(array.length()) { add(array.getString(it)) } }
        }
        require(eligibleWindows in 0..durationHours && attemptedWindows in eligibleWindows..durationHours * 2) { "window_count" }
        windowStartElapsed?.let { check(SystemClock.elapsedRealtime() >= it) { "monotonic_rollback" } }
        windowStartWall?.let { check(System.currentTimeMillis() + 5_000L >= it) { "wall_rollback" } }

        val connection = DefaultConnectionRepository(context)
        val actions = DefaultActionRepository(ActionCatalog(context), context, connection)
        val rules = DefaultAutomationRepository(context, actions, connection)
        val clipboardSettings = ClipboardSettingsRepository(context)
        val trackpadSettings = TrackpadSettingsRepository(context)
        val clipboard = ClipboardSyncEngine()
        val productSessions = M16ProductSessions()
        connection.save("m16.invalid", 22, identity.profileId)
        actions.saveLayout(actions.layout())
        rules.resetDefaults()
        clipboardSettings.saveMode(ClipboardSyncMode.Bidirectional)
        trackpadSettings.update { it.copy(pointerSpeed = 0.71f + slot / 100f) }
        FileOutputStream(File(context.root(), "repo-probe.json")).use { out ->
            out.write(JSONObject()
                .put("profileId", identity.profileId)
                .put("pointerSpeed", trackpadSettings.settings.first().pointerSpeed.toDouble())
                .put("originNonce", identity.nonce)
                .toString().toByteArray())
            out.fd.sync()
        }

        var profileCompleted = false
        try {
            while (coroutineContext.isActive && eligibleWindows < durationHours && attemptedWindows < durationHours * 2) {
                val startElapsed = windowStartElapsed ?: SystemClock.elapsedRealtime()
                val startWall = windowStartWall ?: System.currentTimeMillis()
                val windowIndex = attemptedWindows + 1
                if (windowStartElapsed == null) {
                    ledger.append(baseEvent("admitted", startElapsed, startWall).put("bootId", bootId)
                        .put("windowIndex", windowIndex).put("eligibleTarget", durationHours)
                        .put("profileRoot", "m16/profiles/${identity.profileId}")
                        .put("seed", identity.seed))
                } else {
                    check(priorPid > 0 && priorPid != Process.myPid()) { "resume_pid_not_changed" }
                    ledger.append(baseEvent("resumed", SystemClock.elapsedRealtime(), System.currentTimeMillis())
                        .put("windowIndex", windowIndex).put("sequence", sequence)
                        .put("priorPid", priorPid).put("newPid", Process.myPid()))
                }
                publicStatus = JSONObject().put("state", "admitted").put("profileId", identity.profileId)
                    .put("processName", identity.processName).put("pid", Process.myPid())
                    .put("originNonce", identity.nonce)
                    .put("windowIndex", windowIndex).put("sequence", sequence)
                    .put("windowStartElapsed", startElapsed).toString()
                while (coroutineContext.isActive && SystemClock.elapsedRealtime() - startElapsed < WINDOW_MILLIS) {
                    val category = CATEGORIES[totalSequence % CATEGORIES.size]
                    val operationStart = SystemClock.elapsedRealtime()
                    exercise(category, totalSequence, actions, rules, clipboardSettings, trackpadSettings, clipboard, identity, productSessions)
                    val operationLatency = SystemClock.elapsedRealtime() - operationStart
                    check(operationLatency <= 10_000L) { "operation_hang" }
                    sequence++
                    totalSequence++
                    val nowElapsed = SystemClock.elapsedRealtime()
                    if (lastAckElapsed > 0L) {
                        val heartbeatDelta = nowElapsed - lastAckElapsed
                        if (heartbeatDelta in 20_000L..45_000L) activeMillis += heartbeatDelta
                    }
                    lastAckElapsed = nowElapsed
                    categories += category
                    val ackId = sha256("${identity.nonce}:$windowIndex:$sequence")
                    val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                    val battery = getSystemService(BatteryManager::class.java)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(-1, 100)
                    val exitReasons = pendingExitDelta
                    pendingExitDelta = emptyExitReasons()
                    ledger.append(baseEvent("ack", nowElapsed, System.currentTimeMillis())
                        .put("windowIndex", windowIndex).put("sequence", sequence).put("ackId", ackId)
                        .put("category", category).put("operationLatencyMillis", operationLatency)
                        .put("totalPssKb", memory.totalPss).put("batteryPercentProxy", battery)
                        .put("applicationExitReasons", exitReasons)
                        .put("crashOrAnr", exitReasons.getInt("crash") + exitReasons.getInt("nativeCrash") + exitReasons.getInt("anr") > 0))
                    ledger.checkpoint(JSONObject().put("bootId", bootId)
                        .put("attemptedWindows", attemptedWindows).put("eligibleWindows", eligibleWindows)
                        .put("windowStartElapsed", startElapsed)
                        .put("windowStartWall", startWall).put("sequence", sequence)
                        .put("totalSequence", totalSequence).put("activeMillis", activeMillis)
                        .put("lastAckElapsed", lastAckElapsed).put("categories", JSONArray(categories.toList()))
                        .put("lastElapsed", nowElapsed).put("lastAckId", ackId)
                        .put("lastWall", System.currentTimeMillis()).put("pid", Process.myPid()))
                    publicStatus = JSONObject(publicStatus).put("state", "running")
                        .put("attemptedWindows", attemptedWindows).put("eligibleWindows", eligibleWindows)
                        .put("sequence", sequence).put("totalSequence", totalSequence)
                        .put("lastAckId", ackId).put("heartbeatElapsed", nowElapsed)
                        .put("originNonce", identity.nonce).put("lastElapsed", nowElapsed).toString()
                    delay(OP_INTERVAL_MILLIS)
                }
                val elapsed = SystemClock.elapsedRealtime() - startElapsed
                val eligible = activeMillis >= MIN_ACTIVE_MILLIS && sequence >= 100 && categories.size >= 5
                ledger.append(baseEvent("window_complete", SystemClock.elapsedRealtime(), System.currentTimeMillis())
                    .put("windowIndex", windowIndex).put("sequence", sequence).put("elapsedMillis", elapsed)
                    .put("activeMillis", activeMillis).put("categories", JSONArray(categories.toList()))
                    .put("eligible", eligible))
                if (!eligible) {
                    ledger.append(baseEvent("window_ineligible", SystemClock.elapsedRealtime(), System.currentTimeMillis())
                        .put("windowIndex", windowIndex).put("reasonCode", when {
                            activeMillis < MIN_ACTIVE_MILLIS -> "active_minutes_below_50"
                            sequence < 100 -> "acknowledged_operations_below_100"
                            categories.size < 5 -> "core_categories_below_5"
                            else -> "eligibility_unknown"
                        }))
                }
                attemptedWindows++
                if (eligible) eligibleWindows++
                sequence = 0
                activeMillis = 0L
                lastAckElapsed = 0L
                categories.clear()
                windowStartElapsed = null
                windowStartWall = null
                ledger.checkpoint(JSONObject().put("bootId", bootId)
                    .put("attemptedWindows", attemptedWindows).put("eligibleWindows", eligibleWindows)
                    .put("sequence", 0).put("totalSequence", totalSequence)
                    .put("activeMillis", 0).put("lastAckElapsed", 0).put("categories", JSONArray())
                    .put("lastWall", System.currentTimeMillis()).put("pid", Process.myPid()))
                if (eligibleWindows < durationHours && attemptedWindows % 12 == 0) {
                    ledger.append(baseEvent("lifecycle_process_death_scheduled", SystemClock.elapsedRealtime(), System.currentTimeMillis())
                        .put("attemptedWindows", attemptedWindows).put("eligibleWindows", eligibleWindows))
                    ledger.checkpoint(JSONObject().put("bootId", bootId)
                        .put("attemptedWindows", attemptedWindows).put("eligibleWindows", eligibleWindows)
                        .put("sequence", 0).put("totalSequence", totalSequence)
                        .put("activeMillis", 0).put("lastAckElapsed", 0).put("categories", JSONArray())
                        .put("lastWall", System.currentTimeMillis()).put("pid", Process.myPid())
                        .put("scheduledRestart", true))
                    scheduleProcessResume(identity, durationHours)
                    delay(1_000L)
                    Process.killProcess(Process.myPid())
                    return
                }
            }
            check(eligibleWindows == durationHours) { "eligibility_attempt_cap" }
            ledger.append(baseEvent("profile_complete", SystemClock.elapsedRealtime(), System.currentTimeMillis())
                .put("attemptedWindows", attemptedWindows).put("eligibleWindows", eligibleWindows)
                .put("acknowledgedOperations", totalSequence))
            profileCompleted = true
            publicStatus = JSONObject(publicStatus).put("state", "complete")
                .put("attemptedWindows", attemptedWindows).put("eligibleWindows", eligibleWindows)
                .put("totalSequence", totalSequence).toString()
            while (coroutineContext.isActive) delay(60_000L)
        } catch (cancelled: CancellationException) {
            if (!profileCompleted) {
                ledger.append(baseEvent("admitted_incomplete", SystemClock.elapsedRealtime(), System.currentTimeMillis())
                    .put("windowIndex", attemptedWindows + 1).put("sequence", sequence).put("reasonCode", "worker_cancelled"))
            }
            throw cancelled
        } catch (error: Throwable) {
            writeFailurePacket(context.root(), identity, sequence, error)
            ledger.append(baseEvent("admitted_incomplete", SystemClock.elapsedRealtime(), System.currentTimeMillis())
                .put("windowIndex", attemptedWindows + 1).put("sequence", sequence).put("reasonCode", safeCode(error)))
            publicStatus = JSONObject(publicStatus).put("state", "failed")
                .put("reasonCode", safeCode(error)).put("attemptedWindows", attemptedWindows)
                .put("eligibleWindows", eligibleWindows).toString()
            while (coroutineContext.isActive) delay(60_000L)
        }
    }

    private suspend fun exercise(
        category: String,
        sequence: Int,
        actions: DefaultActionRepository,
        rules: DefaultAutomationRepository,
        clipboardSettings: ClipboardSettingsRepository,
        trackpadSettings: TrackpadSettingsRepository,
        clipboard: ClipboardSyncEngine,
        identity: M16Identity,
        productSessions: M16ProductSessions,
    ) {
        when (category) {
            "deck" -> actions.saveLayout(actions.layout())
            "trackpad" -> {
                TrackpadGestureEngine().classifyGesture(2, Offset(40f, 2f), 120, false)
                trackpadSettings.settings.first()
            }
            "keyboard" -> productSessions.keyboardRoundTrip(sequence)
            "clipboard" -> {
                clipboardSettings.settings.first()
                val observation = clipboard.observe(ClipboardEndpoint.Phone, "proxy-${identity.seed}-$sequence", ClipboardSourceId(identity.profileId), sequence.toLong())
                clipboard.markApplied(clipboard.decide(ClipboardSyncMode.PhoneToMac, sequence.toLong()))
                check(observation.revision.hash.length == 64)
            }
            "rules" -> rules.recipes.first()
            "ssh_failure" -> productSessions.sshTypedFailure()
            "ssh_recovery" -> productSessions.sshRecovery()
            "lifecycle" -> check(Process.myPid() > 0)
        }
    }

    private fun stopWorker(reason: String) {
        val cancel = Intent(this, this::class.java).setAction(ACTION_RESUME)
        PendingIntent.getForegroundService(
            this, 16_000 + slot, cancel,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pending ->
            getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }
        worker?.cancel(CancellationException(reason))
        if (worker == null) stopSelf()
    }

    private fun scheduleProcessResume(identity: M16Identity, durationHours: Int) {
        val resume = Intent(this, this::class.java).setAction(ACTION_RESUME)
            .putExtra(EXTRA_AVD_ID, identity.avdId)
            .putExtra(EXTRA_DURATION_HOURS, durationHours)
        val pending = PendingIntent.getForegroundService(
            this,
            16_000 + slot,
            resume,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 5_000L,
            pending,
        )
    }

    private fun emptyExitReasons(): JSONObject = JSONObject()
        .put("crash", 0).put("nativeCrash", 0).put("anr", 0).put("self", 0).put("other", 0)

    private fun applicationExitReasons(processName: String, sinceWallMillis: Long, priorPid: Int): JSONObject {
        val counts = emptyExitReasons()
        if (Build.VERSION.SDK_INT < 30) return counts
        getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(packageName, 0, 32)
            .filter { it.processName == processName && it.timestamp >= sinceWallMillis && it.pid == priorPid }
            .forEach { info ->
                val key = when (info.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "crash"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "nativeCrash"
                    ApplicationExitInfo.REASON_ANR -> "anr"
                    ApplicationExitInfo.REASON_EXIT_SELF -> "self"
                    else -> "other"
                }
                counts.put(key, counts.getInt(key) + 1)
            }
        return counts
    }

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println(publicStatus)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Autonomous proxy", NotificationManager.IMPORTANCE_MIN),
            )
        }
    }

    companion object {
        const val ACTION_START = "app.codecks.internal.m16.START"
        const val ACTION_RESUME = "app.codecks.internal.m16.RESUME"
        const val ACTION_STOP = "app.codecks.internal.m16.STOP"
        const val EXTRA_AVD_ID = "avd_id"
        const val EXTRA_DURATION_HOURS = "duration_hours"
        private const val CHANNEL = "m16-autonomous-proxy"
        private val CATEGORIES = listOf("deck", "trackpad", "keyboard", "clipboard", "rules", "ssh_failure", "ssh_recovery", "lifecycle")

        private fun baseEvent(type: String, elapsed: Long, wall: Long) = JSONObject()
            .put("type", type).put("elapsedRealtimeMillis", elapsed).put("wallTimeMillis", wall)
        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        private fun safeCode(error: Throwable): String = error::class.java.simpleName
            .lowercase().replace(Regex("[^a-z0-9_]"), "_").take(48).ifBlank { "unknown_failure" }
        private fun writeFailurePacket(root: File, identity: M16Identity, sequence: Int, error: Throwable) {
            val directory = File(root, "failures").apply(File::mkdirs)
            check(directory.listFiles().orEmpty().size < MAX_FAILURE_PACKETS) { "failure_packet_cap" }
            val packet = JSONObject()
                .put("schema", "codecks.m16.failure-packet.v1")
                .put("profileId", identity.profileId).put("processName", identity.processName)
                .put("sequence", sequence).put("reasonCode", safeCode(error))
                .put("capturedAt", Instant.now().toString())
            val target = File(directory, "${SystemClock.elapsedRealtime()}-$sequence.json")
            check(target.createNewFile()) { "failure_packet_collision" }
            FileOutputStream(target, false).use { out ->
                out.write(packet.toString().toByteArray()); out.fd.sync()
            }
            val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
            try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
        }
    }
}

class M16ProfileService01 : M16ProfileService(1)
class M16ProfileService02 : M16ProfileService(2)
class M16ProfileService03 : M16ProfileService(3)
class M16ProfileService04 : M16ProfileService(4)
class M16ProfileService05 : M16ProfileService(5)
