package io.codecks.domain.assurance

import io.codecks.domain.ai.ActionDraft
import io.codecks.domain.ai.ActionStepTypes
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.ActionKind
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.hasCurrentRevisionGateForExecution
import io.codecks.domain.automation.mandatoryChecksSatisfied
import io.codecks.domain.automation.revisionFingerprint
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.sshpack.SshPackValidationResult
import java.security.MessageDigest

fun AutomationRecipe.toAssuranceRequest(
    imported: Boolean = false,
    operationId: String? = null,
): AssuranceRequest {
    val revision = revisionFingerprint()
    return AssuranceRequest.create(
        subjectId = id,
        revision = revision,
        source = AssuranceSource.Rule,
        transport = steps.map { step ->
            when (step) {
                is ActionSpec.LocalRoute -> AssuranceTransport.LocalRoute
                is ActionSpec.DeckActionSpec -> if (step.action.kind == ActionKind.Local) {
                    AssuranceTransport.LocalRoute
                } else {
                    AssuranceTransport.Ssh
                }
                is ActionSpec.CatalogAction,
                is ActionSpec.ShellCommand,
                -> AssuranceTransport.Ssh
            }
        }.distinct().singleOrNull() ?: AssuranceTransport.Composite,
        reviewRequired = true,
        review = gateStamp?.takeIf { hasCurrentRevisionGateForExecution() }
            ?.let { AssuranceReview(it.revisionId, approved = true) },
        permissionGranted = lastPreflight?.mandatoryChecksSatisfied() == true,
        policyPassed = hasCurrentRevisionGateForExecution(),
        imported = imported,
        reversible = cleanupDefinition.action != null,
        operationId = operationId ?: java.util.UUID.randomUUID().toString(),
    )
}

fun ActionDraft.toAssuranceRequest(
    approvedRevision: String? = null,
    operationId: String? = null,
): AssuranceRequest {
    val revision = assuranceHash(definition.toString())
    return AssuranceRequest.create(
        subjectId = definition.id,
        revision = revision,
        source = AssuranceSource.AiDraft,
        transport = definition.steps.mapNotNull { step ->
            when (step.type) {
                ActionStepTypes.HidKey -> AssuranceTransport.Hid
                ActionStepTypes.OpenUrl,
                ActionStepTypes.ClipboardText,
                -> AssuranceTransport.LocalRoute
                ActionStepTypes.SshAction,
                ActionStepTypes.Shell,
                -> AssuranceTransport.Ssh
                ActionStepTypes.Delay -> null
                else -> AssuranceTransport.Composite
            }
        }.distinct().singleOrNull() ?: AssuranceTransport.Composite,
        reviewRequired = true,
        review = approvedRevision?.let { AssuranceReview(it, approved = true) },
        policyPassed = definition.steps.none { it.type == "shell" },
        operationId = operationId ?: java.util.UUID.randomUUID().toString(),
    )
}

fun ReactiveControl.toAssuranceRequest(
    reviewedRevision: String? = null,
    permissionGranted: Boolean = true,
    confirmationGranted: Boolean = true,
    operationId: String? = null,
    idempotencyKey: String? = null,
): AssuranceRequest = AssuranceRequest.create(
    subjectId = id.value,
    revision = actionRevision.value,
    source = AssuranceSource.Reactive,
    transport = when (action) {
        is ReactiveAction.Hid -> AssuranceTransport.Hid
        is ReactiveAction.Helper,
        is ReactiveAction.SpotlightPreview,
        is ReactiveAction.SftpTransferRequest,
        -> AssuranceTransport.Helper
        is ReactiveAction.ChangeMode -> AssuranceTransport.LocalRoute
        is ReactiveAction.BundledSshFallback,
        is ReactiveAction.ExistingCatalog,
        -> AssuranceTransport.Ssh
        is ReactiveAction.Composite -> AssuranceTransport.Composite
    },
    reviewRequired = risk != ReactiveRisk.Safe,
    review = reviewedRevision?.let { AssuranceReview(it, approved = true) },
    permissionGranted = permissionGranted,
    policyPassed = policy.name != "Deny",
    confirmationGranted = confirmationGranted,
    reversible = reversible,
    operationId = operationId ?: java.util.UUID.randomUUID().toString(),
    idempotencyKey = idempotencyKey ?: operationId ?: java.util.UUID.randomUUID().toString(),
)

fun SshPackValidationResult.Valid.toAssuranceRequests(): List<AssuranceRequest> =
    pack.actions.zip(resolvedActions).map { (action, contract) ->
        AssuranceRequest.create(
            subjectId = action.id.value,
            revision = contract.commandRevision,
            source = AssuranceSource.TypedSshCatalog,
            transport = AssuranceTransport.Ssh,
            reviewRequired = contract.requiresConfirmation,
            review = if (contract.requiresConfirmation) null else AssuranceReview(contract.commandRevision, true),
            permissionGranted = contract.preflight.isNotEmpty(),
            policyPassed = true,
        )
    }

private fun assuranceHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
