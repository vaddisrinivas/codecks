package io.codecks.core.actions

import io.codecks.domain.ActionIcon
import io.codecks.domain.DeckAction
import io.codecks.domain.device.TargetSelector

data class Deck(
    val id: String,
    val title: String,
    val buttons: List<DeckButton>,
)

data class DeckButton(
    val id: String,
    val title: String,
    val icon: IconRef,
    val action: ActionSpec,
    val state: ButtonState = ButtonState.Idle,
)

sealed interface IconRef {
    data class Catalog(val icon: ActionIcon) : IconRef
    data class Named(val name: String) : IconRef
}

enum class ButtonState {
    Idle,
    Pressed,
    Running,
    Succeeded,
    Failed,
    Disabled,
}

enum class ShellTrustLevel {
    Catalog,
    UserReviewed,
    Generated,
}

sealed interface ActionSpec {
    val id: String
    val title: String
    val dangerous: Boolean
    val targetSelector: TargetSelector

    data class DeckActionSpec(
        val action: DeckAction,
    ) : ActionSpec {
        override val id: String = action.id
        override val title: String = action.label
        override val dangerous: Boolean = action.dangerous
        override val targetSelector: TargetSelector = action.targetSelector
    }

    data class CatalogAction(
        override val id: String,
        override val title: String,
        override val dangerous: Boolean = false,
        override val targetSelector: TargetSelector = TargetSelector.CurrentDevice,
    ) : ActionSpec

    data class ShellCommand(
        override val id: String,
        override val title: String,
        val command: String,
        val trustLevel: ShellTrustLevel = ShellTrustLevel.UserReviewed,
        override val dangerous: Boolean = false,
        override val targetSelector: TargetSelector = TargetSelector.CurrentDevice,
    ) : ActionSpec

    data class LocalRoute(
        override val id: String,
        override val title: String,
        val route: String,
        override val targetSelector: TargetSelector = TargetSelector.CurrentDevice,
    ) : ActionSpec {
        override val dangerous: Boolean = false
    }
}

data class ActionResult(
    val actionId: String,
    val title: String,
    val status: ActionResultStatus,
    val message: String,
    val logs: String = message,
    val target: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    val succeeded: Boolean get() = status == ActionResultStatus.Succeeded
}

enum class ActionResultStatus {
    Succeeded,
    Failed,
    RequiresConfirmation,
}

interface ActionRunner {
    suspend fun run(spec: ActionSpec, allowDangerous: Boolean = false): ActionResult
}

fun DeckAction.toActionSpec(): ActionSpec = ActionSpec.DeckActionSpec(this)

fun DeckAction.toDeckButton(state: ButtonState = ButtonState.Idle): DeckButton =
    DeckButton(
        id = id,
        title = label,
        icon = IconRef.Catalog(icon),
        action = toActionSpec(),
        state = state,
    )
