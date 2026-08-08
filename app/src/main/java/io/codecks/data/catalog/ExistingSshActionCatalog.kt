package io.codecks.data.catalog

import io.codecks.core.actions.RawCommandPolicy
import io.codecks.core.actions.commandRevision
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.DeckAction
import io.codecks.domain.catalog.CatalogId
import io.codecks.domain.catalog.CatalogActionReferenceResolver
import io.codecks.domain.sshpack.SshCatalogActionContract
import io.codecks.domain.sshpack.SshCatalogActionResolver
import io.codecks.domain.sshpack.SshPreflightRequirement

/** Read-only bridge to the existing bundled ActionCatalog. It has no execution method. */
class ExistingSshActionCatalog(actions: List<DeckAction>) : SshCatalogActionResolver {
    private val contracts = actions.mapNotNull(::toSafeContract)
        .groupBy(SshCatalogActionContract::catalogActionId)
        .mapNotNull { (id, matches) -> matches.singleOrNull()?.let { id to it } }
        .toMap()

    override fun resolve(actionId: CatalogId): SshCatalogActionContract? = contracts[actionId]

    private fun toSafeContract(action: DeckAction): SshCatalogActionContract? {
        if (action.kind != ActionKind.Ssh) return null
        if (action.commandOrigin != CommandOrigin.Bundled) return null
        val command = action.command ?: return null
        if (RawCommandPolicy.firstAllowlistViolation(command) != null) return null
        val revision = action.commandRevision() ?: return null
        val id = runCatching { CatalogId(action.id) }.getOrNull() ?: return null
        return SshCatalogActionContract(
            catalogActionId = id,
            commandRevision = revision,
            preflight = buildSet {
                add(SshPreflightRequirement.SSH_CONNECTION)
                add(SshPreflightRequirement.PINNED_HOST_IDENTITY)
                add(SshPreflightRequirement.COMMAND_SAFETY)
                if (action.dangerous) add(SshPreflightRequirement.EXPLICIT_CONFIRMATION)
            },
            requiresConfirmation = action.dangerous,
        )
    }
}

/** Stable-ID-only bridge for routine validation. It exposes no command or execution method. */
class ExistingCatalogActionReferences(actions: List<DeckAction>) : CatalogActionReferenceResolver {
    private val ids = actions.mapNotNull { runCatching { CatalogId(it.id) }.getOrNull() }
        .groupingBy { it }
        .eachCount()
        .filterValues { it == 1 }
        .keys

    override fun contains(actionId: CatalogId): Boolean = actionId in ids
}
