package io.codecks.core.actions

import io.codecks.domain.ActionIcon
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.DeckAction
import io.codecks.domain.ExecutionAuthorization
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
    val commandOrigin: CommandOrigin
    val review: CommandReview
    val confirmationTitle: String?
    val confirmationBody: String?
    val riskReason: String?
    val authorization: ExecutionAuthorization

    data class DeckActionSpec(
        val action: DeckAction,
    ) : ActionSpec {
        override val id: String = action.id
        override val title: String = action.label
        override val dangerous: Boolean = action.dangerous
        override val targetSelector: TargetSelector = action.targetSelector
        override val commandOrigin: CommandOrigin = action.commandOrigin
        override val review: CommandReview = action.commandReview
        override val confirmationTitle: String? = action.confirmationTitle
        override val confirmationBody: String? = action.confirmationBody
        override val riskReason: String? = action.riskReason
        override val authorization: ExecutionAuthorization = action.executionAuthorization
    }

    data class CatalogAction(
        override val id: String,
        override val title: String,
        override val dangerous: Boolean = false,
        override val targetSelector: TargetSelector = TargetSelector.CurrentDevice,
        override val commandOrigin: CommandOrigin = CommandOrigin.Bundled,
        override val review: CommandReview = CommandReview(null, null),
        override val confirmationTitle: String? = null,
        override val confirmationBody: String? = null,
        override val riskReason: String? = null,
        override val authorization: ExecutionAuthorization = ExecutionAuthorization(),
    ) : ActionSpec

    data class ShellCommand(
        override val id: String,
        override val title: String,
        val command: String,
        val trustLevel: ShellTrustLevel = ShellTrustLevel.UserReviewed,
        override val dangerous: Boolean = false,
        override val targetSelector: TargetSelector = TargetSelector.CurrentDevice,
        override val commandOrigin: CommandOrigin = CommandOrigin.UserAuthored,
        override val review: CommandReview = CommandReview(null, null),
        override val confirmationTitle: String? = null,
        override val confirmationBody: String? = null,
        override val riskReason: String? = null,
        override val authorization: ExecutionAuthorization = ExecutionAuthorization(),
    ) : ActionSpec

    data class LocalRoute(
        override val id: String,
        override val title: String,
        val route: String,
        override val targetSelector: TargetSelector = TargetSelector.CurrentDevice,
        override val commandOrigin: CommandOrigin = CommandOrigin.UserAuthored,
        override val review: CommandReview = CommandReview(null, null),
        override val confirmationTitle: String? = null,
        override val confirmationBody: String? = null,
        override val riskReason: String? = null,
        override val authorization: ExecutionAuthorization = ExecutionAuthorization(),
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
    RequiresReview,
}

interface ActionRunner {
    suspend fun run(spec: ActionSpec, authorization: ExecutionAuthorization = ExecutionAuthorization()): ActionResult =
        run(spec, allowDangerous = false)

    suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult =
        run(
            spec = spec,
            authorization = if (allowDangerous) {
                ExecutionAuthorization(dangerousRevisionConfirmed = spec.dangerousConfirmationRevision())
            } else {
                ExecutionAuthorization()
            },
        )
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

fun ActionSpec.dangerousConfirmationRevision(): String =
    commandRevision() ?: "${id}:${title.trim()}:dangerous=$dangerous"
