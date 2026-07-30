package io.codecks.data.automation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.data.ConnectionRepository
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.AutomationTriggerEngine
import io.codecks.domain.automation.AutomationTriggerEvaluation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAutomationTriggerEngine internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val stateStore: AutomationTriggerStateStore,
    private val nowProvider: () -> LocalDateTime,
) : AutomationTriggerEngine {
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
        val reasonByRecipeId = mutableMapOf<String, String>()
        enabledTriggeredRecipes.forEach { recipe ->
            val result = isDue(recipe)
            reasonByRecipeId[recipe.id] = result.reason
            if (result.due) due += recipe
        }
        val nowMillis = checkedAt.toEpochMillis()
        return AutomationTriggerEvaluation(
            dueRecipes = due,
            checkedCount = enabledTriggeredRecipes.size,
            checkedAtMillis = nowMillis,
            nextWindowStartAtMillis = nowMillis + TIME_TRIGGER_GRACE_MINUTES * 60_000 / 2,
            nextWindowEndAtMillis = nowMillis + (6 * 60 * 60 * 1000L),
            reasonByRecipeId = reasonByRecipeId,
            message = when {
                enabledTriggeredRecipes.isEmpty() -> "No enabled scheduled rules"
                due.isEmpty() -> "Checked ${enabledTriggeredRecipes.size} triggers"
                else -> "${due.size} trigger${if (due.size == 1) "" else "s"} matched"
            },
        )
    }

    private data class DueResult(
        val due: Boolean,
        val reason: String,
    )

    private suspend fun isDue(recipe: AutomationRecipe): DueResult {
        val trigger = recipe.trigger
        return when (trigger) {
            AutomationTrigger.Manual,
            is AutomationTrigger.AiSuggested -> DueResult(
                due = false,
                reason = "Manual/AI trigger not supported in background checks",
            )
            is AutomationTrigger.TimeOfDay -> {
                val due = timeDue(recipe, trigger)
                DueResult(
                    due = due,
                    reason = if (due) "Time-of-day trigger fired" else "Time-of-day trigger did not match schedule",
                )
            }
            is AutomationTrigger.ActiveApp -> macTextDue(
                recipe = recipe,
                command = ACTIVE_APP_COMMAND,
                expected = trigger.appName,
            ).run {
                DueResult(
                    due = this,
                    reason = if (this) "Active app changed" else "Active app did not match ${trigger.appName}",
                )
            }
            is AutomationTrigger.ClipboardContains -> macTextDue(
                recipe = recipe,
                command = CLIPBOARD_COMMAND,
                expected = trigger.text,
            ).run {
                DueResult(
                    due = this,
                    reason = if (this) "Clipboard now matches ${trigger.text}" else "Clipboard did not match",
                )
            }
            is AutomationTrigger.WifiSsid -> macTextDue(
                recipe = recipe,
                command = WIFI_COMMAND,
                expected = trigger.ssid,
            ).run {
                DueResult(
                    due = this,
                    reason = if (this) "Wi-Fi changed to ${trigger.ssid}" else "Wi-Fi did not match ${trigger.ssid}",
                )
            }
            AutomationTrigger.MacAwake -> macFingerprintDue(
                recipe = recipe,
                command = BOOT_FINGERPRINT_COMMAND,
                allowInitialFire = false,
            ).run {
                DueResult(
                    due = this,
                    reason = if (this) "Mac wake event observed" else "No new wake event",
                )
            }
            is AutomationTrigger.FileChanged -> macFingerprintDue(
                recipe = recipe,
                command = "if [ -e ${trigger.path.toShellPath()} ]; then stat -f %m ${trigger.path.toShellPath()}; fi",
                allowInitialFire = false,
            ).run {
                DueResult(
                    due = this,
                    reason = if (this) "Path changed: ${trigger.path}" else "No file-system change for ${trigger.path}",
                )
            }
            is AutomationTrigger.BatteryBelow -> batteryDue(recipe, trigger.percent).run {
                DueResult(
                    due = this,
                    reason = if (this) "Battery below ${trigger.percent}%" else "Battery above ${trigger.percent}%",
                )
            }
        }
    }

    private fun timeDue(recipe: AutomationRecipe, trigger: AutomationTrigger.TimeOfDay): Boolean {
        val now = nowProvider()
        val day = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        val dayMatches = trigger.days.isEmpty() || trigger.days.any { it.equals(day, ignoreCase = true) }
        if (!dayMatches) return false
        val scheduled = now
            .withHour(trigger.hour.coerceIn(0, 23))
            .withMinute(trigger.minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (now.isBefore(scheduled)) return false
        if (Duration.between(scheduled, now).toMinutes() > TIME_TRIGGER_GRACE_MINUTES) return false
        return shouldFire(recipe.id, "time:${LocalDate.from(now)}:${scheduled.hour}:${scheduled.minute}")
    }

    private suspend fun macTextDue(recipe: AutomationRecipe, command: String, expected: String): Boolean {
        if (expected.isBlank()) return false
        val value = connectionRepository.runCommandRaw(command).getOrElse {
            stateStore.remove(recipe.id)
            return false
        }.trim()
        val matched = triggerTextMatches(expected, value)
        if (!matched) {
            stateStore.remove(recipe.id)
            return false
        }
        return shouldFire(recipe.id, "${expected.lowercase()}::${value.hashCode()}")
    }

    private suspend fun macFingerprintDue(
        recipe: AutomationRecipe,
        command: String,
        allowInitialFire: Boolean,
    ): Boolean {
        val fingerprint = connectionRepository.runCommandRaw(command).getOrElse {
            stateStore.remove(recipe.id)
            return false
        }.trim()
        if (fingerprint.isBlank()) return false
        val previous = stateStore.get(recipe.id)
        if (previous == null) {
            stateStore.put(recipe.id, fingerprint)
            return allowInitialFire
        }
        if (previous == fingerprint) return false
        stateStore.put(recipe.id, fingerprint)
        return true
    }

    private suspend fun batteryDue(recipe: AutomationRecipe, threshold: Int): Boolean {
        val percent = connectionRepository.runCommandRaw(BATTERY_COMMAND)
            .getOrElse {
                stateStore.remove(recipe.id)
                return false
            }
            .trim()
            .toIntOrNull() ?: return false
        if (percent > threshold.coerceIn(1, 100)) {
            stateStore.remove(recipe.id)
            return false
        }
        return shouldFire(recipe.id, "battery-below-${threshold.coerceIn(1, 100)}")
    }

    private fun shouldFire(recipeId: String, fingerprint: String): Boolean {
        if (stateStore.get(recipeId) == fingerprint) return false
        stateStore.put(recipeId, fingerprint)
        return true
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
    }
}

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
