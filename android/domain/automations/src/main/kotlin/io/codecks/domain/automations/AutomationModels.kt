package io.codecks.domain.automations

import io.codecks.domain.actions.ActionInvocation
import kotlinx.serialization.Serializable

@Serializable
data class Automation(
    val id: String,
    val version: Int = 1,
    val title: String,
    val trigger: AutomationTrigger,
    val conditions: List<AutomationCondition> = emptyList(),
    val steps: List<AutomationStep>,
    val policy: AutomationPolicy = AutomationPolicy(),
    val enabled: Boolean = false,
)

@Serializable
sealed interface AutomationTrigger {
    @Serializable
    data object Manual : AutomationTrigger

    @Serializable
    data class TimeOfDay(val hour: Int, val minute: Int, val days: Set<DayOfWeek>) : AutomationTrigger

    @Serializable
    data class ActiveMacApp(val bundleId: String) : AutomationTrigger
}

@Serializable
enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

@Serializable
sealed interface AutomationCondition {
    @Serializable
    data object TargetOnline : AutomationCondition

    @Serializable
    data class ClipboardContains(val text: String) : AutomationCondition
}

@Serializable
data class AutomationStep(
    val order: Int,
    val invocation: ActionInvocation,
    val delayAfterMillis: Long = 0,
    val continueOnFailure: Boolean = false,
)

@Serializable
data class AutomationPolicy(
    val allowOverlap: Boolean = false,
    val retryCount: Int = 0,
    val confirmationExpiresAfterMillis: Long = 60_000,
)

object AutomationTemplates {
    val morningFocus = Automation(
        id = "template-morning-focus",
        title = "Morning Focus",
        trigger = AutomationTrigger.TimeOfDay(
            hour = 8,
            minute = 30,
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        ),
        conditions = listOf(AutomationCondition.TargetOnline),
        steps = emptyList(),
        enabled = false,
    )
}

