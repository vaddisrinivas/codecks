package io.codecks.domain.sshpack

import io.codecks.domain.catalog.CatalogId
import io.codecks.domain.catalog.CatalogVersion
import java.util.Collections

class SshActionPack(
    val id: CatalogId,
    val version: CatalogVersion,
    actions: List<TypedSshAction>,
) {
    val actions: List<TypedSshAction> = Collections.unmodifiableList(actions.sortedBy { it.id })
}

/** A pack can reference only an existing, typed, bundled action. It cannot contain shell text. */
data class TypedSshAction(
    val id: CatalogId,
    val catalogActionId: CatalogId,
    val title: String,
) {
    init {
        require(title.isNotBlank())
    }
}

enum class SshPreflightRequirement {
    SSH_CONNECTION,
    PINNED_HOST_IDENTITY,
    COMMAND_SAFETY,
    EXPLICIT_CONFIRMATION,
}

class SshCatalogActionContract(
    val catalogActionId: CatalogId,
    val commandRevision: String,
    preflight: Set<SshPreflightRequirement>,
    val requiresConfirmation: Boolean,
) {
    val preflight: Set<SshPreflightRequirement> = Collections.unmodifiableSet(preflight.toSet())

    init {
        require(commandRevision.matches(Regex("[0-9a-f]{64}")))
        require(SshPreflightRequirement.SSH_CONNECTION in this.preflight)
        require(SshPreflightRequirement.PINNED_HOST_IDENTITY in this.preflight)
        require(SshPreflightRequirement.COMMAND_SAFETY in this.preflight)
        require(
            !requiresConfirmation || SshPreflightRequirement.EXPLICIT_CONFIRMATION in this.preflight,
        )
    }
}

fun interface SshCatalogActionResolver {
    /** Resolution validates the existing command policy; this contract never returns command text. */
    fun resolve(actionId: CatalogId): SshCatalogActionContract?
}

enum class SshPackValidationCode {
    EMPTY_PACK,
    PACK_TOO_LARGE,
    DUPLICATE_ACTION_ID,
    DUPLICATE_CATALOG_ACTION_ID,
    ACTION_NOT_ALLOWLISTED,
    ACTION_ID_MISMATCH,
    UNSAFE_ACTION_CONTRACT,
}

sealed interface SshPackValidationResult {
    data class Valid(
        val pack: SshActionPack,
        val resolvedActions: List<SshCatalogActionContract>,
        val requiresConfirmation: Boolean,
        val requiredPreflight: Set<SshPreflightRequirement>,
    ) : SshPackValidationResult

    data class Invalid(val code: SshPackValidationCode, val actionId: CatalogId? = null) :
        SshPackValidationResult
}

object SshActionPackValidator {
    fun validate(pack: SshActionPack, resolver: SshCatalogActionResolver): SshPackValidationResult {
        if (pack.actions.isEmpty()) return SshPackValidationResult.Invalid(SshPackValidationCode.EMPTY_PACK)
        if (pack.actions.size > MAX_PACK_ACTIONS) {
            return SshPackValidationResult.Invalid(SshPackValidationCode.PACK_TOO_LARGE)
        }
        if (pack.actions.map { it.id }.distinct().size != pack.actions.size) {
            return SshPackValidationResult.Invalid(SshPackValidationCode.DUPLICATE_ACTION_ID)
        }
        if (pack.actions.map { it.catalogActionId }.distinct().size != pack.actions.size) {
            return SshPackValidationResult.Invalid(SshPackValidationCode.DUPLICATE_CATALOG_ACTION_ID)
        }
        val resolved = buildList {
            pack.actions.forEach { action ->
                val contract = resolver.resolve(action.catalogActionId)
                    ?: return SshPackValidationResult.Invalid(
                        SshPackValidationCode.ACTION_NOT_ALLOWLISTED,
                        action.catalogActionId,
                    )
                if (contract.catalogActionId != action.catalogActionId) {
                    return SshPackValidationResult.Invalid(
                        SshPackValidationCode.ACTION_ID_MISMATCH,
                        action.catalogActionId,
                    )
                }
                if (
                    SshPreflightRequirement.COMMAND_SAFETY !in contract.preflight ||
                    (contract.requiresConfirmation &&
                        SshPreflightRequirement.EXPLICIT_CONFIRMATION !in contract.preflight)
                ) {
                    return SshPackValidationResult.Invalid(
                        SshPackValidationCode.UNSAFE_ACTION_CONTRACT,
                        action.catalogActionId,
                    )
                }
                add(contract)
            }
        }
        return SshPackValidationResult.Valid(
            pack = pack,
            resolvedActions = Collections.unmodifiableList(resolved.toList()),
            requiresConfirmation = resolved.any(SshCatalogActionContract::requiresConfirmation),
            requiredPreflight = Collections.unmodifiableSet(resolved.flatMap { it.preflight }.toSet()),
        )
    }

    private const val MAX_PACK_ACTIONS = 64
}
