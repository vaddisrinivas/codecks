package io.codecks.data.automation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.data.ConnectionRepository
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.AutomationTriggerClaim
import io.codecks.domain.automation.AutomationTriggerEngine
import io.codecks.domain.automation.AutomationTriggerEvaluation
import io.codecks.domain.automation.revisionFingerprint
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAutomationTriggerEngine internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val stateStore: AutomationTriggerStateStore,
    private val nowProvider: () -> LocalDateTime,
) : AutomationTriggerEngine {
    override suspend fun complete(claim: AutomationTriggerClaim): Boolean =
        stateStore.compareAndSet(
            claim.recipeId,
            claim.pendingState(),
            doneState(claim.fingerprint),
        )

    override suspend fun release(claim: AutomationTriggerClaim): Boolean =
        stateStore.compareAndSet(
            claim.recipeId,
            claim.pendingState(),
            null,
        )

    /** Test/backward-compatibility helper. Production callers must retain the exact claim. */
    internal suspend fun complete(recipeId: String): Boolean {
        val claim = parseRunning(stateStore.get(recipeId))?.copy(recipeId = recipeId) ?: return false
        return complete(claim)
    }
    @Inject
    constructor(
        connectionRepository: ConnectionRepository,
        @ApplicationContext context: Context,
    ) : this(
        connectionRepository = connectionRepository,
        stateStore = SharedPreferencesAutomationTriggerStateStore(context),
        nowProvider = LocalDateTime::now,
    )

    internal constructor(connectionRepository: ConnectionRepository) : this(
        connectionRepository = connectionRepository,
        stateStore = InMemoryAutomationTriggerStateStore(),
        nowProvider = LocalDateTime::now,
    )

    override suspend fun evaluate(recipes: List<AutomationRecipe>): AutomationTriggerEvaluation {
        val checkedAt = nowProvider()
        val enabledTriggeredRecipes = recipes.filter { it.enabled && it.trigger !is AutomationTrigger.Manual }
        val due = mutableListOf<AutomationRecipe>()
        val claims = mutableMapOf<String, AutomationTriggerClaim>()
        val reasonByRecipeId = mutableMapOf<String, String>()
        enabledTriggeredRecipes.forEach { recipe ->
            val result = isDue(recipe)
            reasonByRecipeId[recipe.id] = result.reason
            result.claim?.let { claim ->
                due += recipe
                claims[recipe.id] = claim
            }
        }
        val nowMillis = checkedAt.toEpochMillis()
        return AutomationTriggerEvaluation(
            dueRecipes = due,
            checkedCount = enabledTriggeredRecipes.size,
            checkedAtMillis = nowMillis,
            nextWindowStartAtMillis = nowMillis + TIME_TRIGGER_GRACE_MINUTES * 60_000 / 2,
            nextWindowEndAtMillis = nowMillis + (6 * 60 * 60 * 1000L),
            reasonByRecipeId = reasonByRecipeId,
            claimsByRecipeId = claims,
            message = when {
                enabledTriggeredRecipes.isEmpty() -> "No enabled scheduled rules"
                due.isEmpty() -> "Checked ${enabledTriggeredRecipes.size} triggers"
                else -> "${due.size} trigger${if (due.size == 1) "" else "s"} matched"
            },
        )
    }

    private data class DueResult(
        val claim: AutomationTriggerClaim?,
        val reason: String,
    ) {
        val due: Boolean get() = claim != null
    }

    private suspend fun isDue(recipe: AutomationRecipe): DueResult {
        val trigger = recipe.trigger
        return when (trigger) {
            AutomationTrigger.Manual,
            is AutomationTrigger.AiSuggested -> DueResult(
                claim = null,
                reason = "Manual/AI trigger not supported in background checks",
            )
            is AutomationTrigger.TimeOfDay -> {
                val claim = timeDue(recipe, trigger)
                DueResult(
                    claim = claim,
                    reason = if (claim != null) "Time-of-day trigger fired" else "Time-of-day trigger did not match schedule",
                )
            }
            is AutomationTrigger.ActiveApp -> macTextDue(
                recipe = recipe,
                command = ACTIVE_APP_COMMAND,
                expected = trigger.appName,
            ).let { claim ->
                DueResult(
                    claim = claim,
                    reason = if (claim != null) "Active app changed" else "Active app did not match ${trigger.appName}",
                )
            }
            is AutomationTrigger.ClipboardContains -> macTextDue(
                recipe = recipe,
                command = CLIPBOARD_COMMAND,
                expected = trigger.text,
            ).let { claim ->
                DueResult(
                    claim = claim,
                    reason = if (claim != null) "Clipboard now matches" else "Clipboard did not match",
                )
            }
            is AutomationTrigger.WifiSsid -> macTextDue(
                recipe = recipe,
                command = WIFI_COMMAND,
                expected = trigger.ssid,
            ).let { claim ->
                DueResult(
                    claim = claim,
                    reason = if (claim != null) "Wi-Fi changed" else "Wi-Fi did not match",
                )
            }
            AutomationTrigger.MacAwake -> macFingerprintDue(
                recipe = recipe,
                command = BOOT_FINGERPRINT_COMMAND,
                allowInitialFire = false,
            ).let { claim ->
                DueResult(
                    claim = claim,
                    reason = if (claim != null) "Mac wake event observed" else "No new wake event",
                )
            }
            is AutomationTrigger.FileChanged -> macFingerprintDue(
                recipe = recipe,
                command = "if [ -e ${trigger.path.toShellPath()} ]; then stat -f %m ${trigger.path.toShellPath()}; fi",
                allowInitialFire = false,
            ).let { claim ->
                DueResult(
                    claim = claim,
                    reason = if (claim != null) "Path changed" else "No file-system change",
                )
            }
            is AutomationTrigger.BatteryBelow -> batteryDue(recipe, trigger.percent).let { claim ->
                DueResult(
                    claim = claim,
                    reason = if (claim != null) "Battery below threshold" else "Battery above threshold",
                )
            }
        }
    }

    private suspend fun timeDue(recipe: AutomationRecipe, trigger: AutomationTrigger.TimeOfDay): AutomationTriggerClaim? {
        val now = nowProvider()
        val day = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        val dayMatches = trigger.days.isEmpty() || trigger.days.any { it.equals(day, ignoreCase = true) }
        if (!dayMatches) return null
        val scheduled = now
            .withHour(trigger.hour.coerceIn(0, 23))
            .withMinute(trigger.minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (now.isBefore(scheduled)) return null
        if (Duration.between(scheduled, now).toMinutes() > TIME_TRIGGER_GRACE_MINUTES) return null
        return shouldFire(recipe, "time:${LocalDate.from(now)}:${scheduled.hour}:${scheduled.minute}")
    }

    private suspend fun macTextDue(recipe: AutomationRecipe, command: String, expected: String): AutomationTriggerClaim? {
        if (expected.isBlank()) return null
        val value = connectionRepository.runCommandRaw(command).getOrElse {
            return null
        }.trim()
        val matched = triggerTextMatches(expected, value)
        if (!matched) {
            clearCompletedState(recipe.id)
            return null
        }
        val fingerprint = "${expected.lowercase()}::${value.sha256()}"
        val legacy = "${expected.lowercase()}::${value.hashCode()}"
        return shouldFire(recipe, fingerprint, setOf(legacy))
    }

    private suspend fun macFingerprintDue(
        recipe: AutomationRecipe,
        command: String,
        allowInitialFire: Boolean,
    ): AutomationTriggerClaim? {
        val fingerprint = connectionRepository.runCommandRaw(command).getOrElse {
            return null
        }.trim()
        if (fingerprint.isBlank()) return null
        val previous = stateStore.get(recipe.id)
        if (previous == null) {
            if (!allowInitialFire) {
                stateStore.put(recipe.id, doneState(fingerprint))
                return null
            }
        }
        return shouldFire(recipe, fingerprint)
    }

    private suspend fun batteryDue(recipe: AutomationRecipe, threshold: Int): AutomationTriggerClaim? {
        val percent = connectionRepository.runCommandRaw(BATTERY_COMMAND)
            .getOrElse {
                return null
            }
            .trim()
            .toIntOrNull() ?: return null
        if (percent > threshold.coerceIn(1, 100)) {
            clearCompletedState(recipe.id)
            return null
        }
        return shouldFire(recipe, "battery-below-${threshold.coerceIn(1, 100)}")
    }

    private suspend fun shouldFire(
        recipe: AutomationRecipe,
        fingerprint: String,
        legacyFingerprints: Set<String> = emptySet(),
    ): AutomationTriggerClaim? {
        repeat(MAX_CAS_ATTEMPTS) {
            val current = stateStore.get(recipe.id)
            val completedStates = buildSet {
                add(doneState(fingerprint))
                add(LEGACY_DONE_PREFIX + fingerprint)
                add(fingerprint)
                legacyFingerprints.forEach { legacy ->
                    add(doneState(legacy))
                    add(LEGACY_DONE_PREFIX + legacy)
                    add(legacy)
                }
            }
            if (current in completedStates) {
                if (current != doneState(fingerprint)) {
                    stateStore.compareAndSet(recipe.id, current, doneState(fingerprint))
                }
                return null
            }
            parseRunning(current)?.let { running ->
                if (running.recipeRevision != recipe.revisionFingerprint()) {
                    stateStore.compareAndSet(recipe.id, current, doneState(running.fingerprint))
                    return null
                }
                if (running.leaseUntilMillis > nowProvider().toEpochMillis()) return null
            }
            val claim = AutomationTriggerClaim(
                recipeId = recipe.id,
                recipeRevision = recipe.revisionFingerprint(),
                fingerprint = fingerprint,
                claimId = UUID.randomUUID().toString(),
                leaseUntilMillis = nowProvider().toEpochMillis() + CLAIM_LEASE_MILLIS,
            )
            if (stateStore.compareAndSet(recipe.id, current, claim.pendingState())) return claim
        }
        return null
    }

    private suspend fun clearCompletedState(recipeId: String) {
        val current = stateStore.get(recipeId)
        if (current?.startsWith(DONE_PREFIX) == true || current?.startsWith(LEGACY_DONE_PREFIX) == true) {
            stateStore.compareAndSet(recipeId, current, null)
        }
    }

    private companion object {
        const val ACTIVE_APP_COMMAND =
            "osascript -e 'tell application \"System Events\" to get name of first application process whose frontmost is true'"
        const val CLIPBOARD_COMMAND = "pbpaste 2>/dev/null | head -c 4096"
        const val WIFI_COMMAND =
            "networksetup -getairportnetwork en0 2>/dev/null | sed 's/^Current Wi-Fi Network: //'"
        const val BOOT_FINGERPRINT_COMMAND = "sysctl -n kern.boottime | sed 's/.*sec = \\([0-9]*\\).*/\\1/'"
        const val BATTERY_COMMAND = "pmset -g batt | grep -Eo '[0-9]+%' | head -1 | tr -d '%'"
        const val TIME_TRIGGER_GRACE_MINUTES = 90L
        const val CLAIM_LEASE_MILLIS = 30L * 60L * 1_000L
        const val MAX_CAS_ATTEMPTS = 8
    }
}

private const val PENDING_PREFIX = "running|"
private const val DONE_PREFIX = "done|"
private const val LEGACY_DONE_PREFIX = "done:"

private fun AutomationTriggerClaim.pendingState(): String =
    "$PENDING_PREFIX$claimId|$leaseUntilMillis|$recipeRevision|$fingerprint"
private fun doneState(fingerprint: String): String = "$DONE_PREFIX$fingerprint"
private fun parseRunning(value: String?): AutomationTriggerClaim? {
    if (value?.startsWith(PENDING_PREFIX) != true) return null
    val pieces = value.removePrefix(PENDING_PREFIX).split('|', limit = 4)
    if (pieces.size != 4) return null
    return AutomationTriggerClaim(
        recipeId = "",
        claimId = pieces[0],
        leaseUntilMillis = pieces[1].toLongOrNull() ?: return null,
        recipeRevision = pieces[2],
        fingerprint = pieces[3],
    )
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun triggerTextMatches(expected: String, actual: String): Boolean {
    val expectedText = expected.trim()
    if (expectedText.isBlank()) return false
    if (actual.contains(expectedText, ignoreCase = true)) return true
    val aliases = mapOf(
        "browser" to setOf("Safari", "Google Chrome", "Chrome", "Arc", "Firefox", "Brave", "Edge", "Opera"),
        "meeting" to setOf("Zoom", "Google Meet", "Microsoft Teams", "FaceTime", "Slack"),
        "developer" to setOf("Code", "Visual Studio Code", "Android Studio", "Terminal", "iTerm", "Xcode"),
        "media" to setOf("Music", "Spotify", "YouTube", "VLC", "QuickTime Player"),
    )
    return aliases[expectedText.lowercase()].orEmpty().any { alias ->
        actual.contains(alias, ignoreCase = true)
    }
}

private fun String.toShellPath(): String =
    when {
        this == "~" -> "\"\$HOME\""
        startsWith("~/") -> "\"\$HOME/${drop(2).replace("\"", "\\\"")}\""
        else -> "'${replace("'", "'\"'\"'")}'"
    }

private fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
