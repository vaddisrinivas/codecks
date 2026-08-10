package io.codecks.core.actions

import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.assurance.AssuranceRequest
import io.codecks.domain.assurance.AssuranceReview
import io.codecks.domain.assurance.AssuranceSource
import io.codecks.domain.assurance.AssuranceTransport
import io.codecks.domain.ai.MacVisualEffectCatalog
import java.security.MessageDigest

internal fun ActionSpec.assuranceRequest(
    source: AssuranceSource = when {
        commandOrigin == CommandOrigin.AiGenerated -> AssuranceSource.AiDraft
        else -> AssuranceSource.Deck
    },
    confirmationGranted: Boolean = !dangerous,
): AssuranceRequest {
    val revision = commandRevision() ?: assuranceHash(dangerousConfirmationRevision())
    val policyPassed = when (this) {
        is ActionSpec.DeckActionSpec -> when {
            action.kind == ActionKind.Local -> true
            action.commandOrigin == CommandOrigin.AiGenerated -> action.command.orEmpty().let { command ->
                MacVisualEffectCatalog.isKnownCommand(command) || RawCommandPolicy.firstAllowlistViolation(command) == null
            }
            else -> RawCommandPolicy.firstViolation(action.command.orEmpty()) == null
        }
        is ActionSpec.ShellCommand -> when (commandOrigin) {
            CommandOrigin.AiGenerated ->
                MacVisualEffectCatalog.isKnownCommand(command) || RawCommandPolicy.firstAllowlistViolation(command) == null
            else -> RawCommandPolicy.firstViolation(command) == null
        }
        is ActionSpec.CatalogAction,
        is ActionSpec.LocalRoute,
        -> true
    }
    val reviewedRevision = review.reviewedRevision
    return AssuranceRequest.create(
        subjectId = id,
        revision = revision,
        source = source,
        transport = when (this) {
            is ActionSpec.LocalRoute -> AssuranceTransport.LocalRoute
            is ActionSpec.DeckActionSpec -> if (action.kind == ActionKind.Local) {
                AssuranceTransport.LocalRoute
            } else {
                AssuranceTransport.Ssh
            }
            is ActionSpec.CatalogAction,
            is ActionSpec.ShellCommand,
            -> AssuranceTransport.Ssh
        },
        reviewRequired = commandOrigin != CommandOrigin.Bundled,
        review = reviewedRevision?.let { AssuranceReview(it, approved = true) },
        policyPassed = policyPassed,
        confirmationGranted = confirmationGranted,
    )
}

fun ActionResult.withAssuranceSource(source: AssuranceSource): ActionResult =
    copy(assuranceReceipt = assuranceReceipt?.copy(source = source))

private fun assuranceHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
