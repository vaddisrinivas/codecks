package io.codecks.domain.sshpack

import io.codecks.data.catalog.ExistingSshActionCatalog
import io.codecks.data.catalog.ExistingCatalogActionReferences
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.DeckAction
import io.codecks.domain.catalog.CatalogId
import io.codecks.domain.catalog.CatalogVersion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshActionPackTest {
    @Test
    fun validatorRejectsEmptyDuplicateUnknownAndMismatchedActions() {
        assertInvalid(pack(emptyList()), resolver(), SshPackValidationCode.EMPTY_PACK)
        assertInvalid(
            pack((0..64).map { typed("pack.action_$it", "finder") }),
            resolver("finder"),
            SshPackValidationCode.PACK_TOO_LARGE,
        )
        val duplicate = typed("pack.same", "finder")
        assertInvalid(pack(listOf(duplicate, duplicate)), resolver("finder"), SshPackValidationCode.DUPLICATE_ACTION_ID)
        assertInvalid(
            pack(listOf(typed("pack.first", "finder"), typed("pack.second", "finder"))),
            resolver("finder"),
            SshPackValidationCode.DUPLICATE_CATALOG_ACTION_ID,
        )
        assertInvalid(pack(listOf(typed("pack.raw", "rm_rf"))), resolver(), SshPackValidationCode.ACTION_NOT_ALLOWLISTED)
        val mismatch = SshCatalogActionResolver { contract("different") }
        assertInvalid(pack(listOf(typed("pack.finder", "finder"))), mismatch, SshPackValidationCode.ACTION_ID_MISMATCH)
    }

    @Test
    fun validatorAggregatesConfirmationAndMandatoryPreflight() {
        val result = SshActionPackValidator.validate(
            pack(listOf(typed("pack.finder", "finder"), typed("pack.lock", "lock"))),
            SshCatalogActionResolver { id -> contract(id.value, requiresConfirmation = id.value == "lock") },
        ) as SshPackValidationResult.Valid

        assertTrue(result.requiresConfirmation)
        assertTrue(SshPreflightRequirement.SSH_CONNECTION in result.requiredPreflight)
        assertTrue(SshPreflightRequirement.PINNED_HOST_IDENTITY in result.requiredPreflight)
        assertTrue(SshPreflightRequirement.COMMAND_SAFETY in result.requiredPreflight)
        assertTrue(SshPreflightRequirement.EXPLICIT_CONFIRMATION in result.requiredPreflight)
    }

    @Test
    fun existingBridgeAllowsOnlySafeBundledSshAndNeverExecutes() {
        val safe = action("finder", "open -a Finder")
        val dangerous = action("lock", "open -a Finder", dangerous = true)
        val user = action("user_action", "open -a Finder", origin = CommandOrigin.UserAuthored)
        val local = action("local", null, kind = ActionKind.Local)
        val malicious = action("destroy", "rm -rf /tmp/codecks")
        val catalog = ExistingSshActionCatalog(listOf(safe, dangerous, user, local, malicious))

        assertTrue(catalog.resolve(CatalogId("finder")) != null)
        assertTrue(catalog.resolve(CatalogId("lock"))!!.requiresConfirmation)
        assertNull(catalog.resolve(CatalogId("user_action")))
        assertNull(catalog.resolve(CatalogId("local")))
        assertNull(catalog.resolve(CatalogId("destroy")))

        val duplicated = listOf(action("finder", "open -a Finder"), action("finder", "open -a Finder"))
        assertNull(ExistingSshActionCatalog(duplicated).resolve(CatalogId("finder")))
        assertFalse(ExistingCatalogActionReferences(duplicated).contains(CatalogId("finder")))
    }

    @Test
    fun ownedContractsCannotCarryRawShellSecretsCredentialsOrExecutionHooks() {
        val sources = listOf(
            File("src/main/java/io/codecks/domain/sshpack"),
            File("src/main/java/io/codecks/domain/catalog"),
            File("src/main/java/io/codecks/data/catalog"),
        ).flatMap { it.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
            .joinToString("\n") { it.readText() }

        assertFalse(Regex("\\b(rawCommand|shell|credential|secret)\\s*:\\s*String\\b", RegexOption.IGNORE_CASE).containsMatchIn(sources))
        listOf("fun execute(", "fun run(", "ProcessBuilder", "Runtime.getRuntime", "SshExecutor").forEach {
            assertFalse(it, sources.contains(it))
        }
    }

    private fun assertInvalid(pack: SshActionPack, resolver: SshCatalogActionResolver, code: SshPackValidationCode) {
        assertEquals(code, (SshActionPackValidator.validate(pack, resolver) as SshPackValidationResult.Invalid).code)
    }

    private fun resolver(vararg ids: String) = SshCatalogActionResolver { requested ->
        ids.firstOrNull { it == requested.value }?.let(::contract)
    }

    private fun contract(id: String, requiresConfirmation: Boolean = false): SshCatalogActionContract =
        SshCatalogActionContract(
            CatalogId(id),
            "a".repeat(64),
            buildSet {
                add(SshPreflightRequirement.SSH_CONNECTION)
                add(SshPreflightRequirement.PINNED_HOST_IDENTITY)
                add(SshPreflightRequirement.COMMAND_SAFETY)
                if (requiresConfirmation) add(SshPreflightRequirement.EXPLICIT_CONFIRMATION)
            },
            requiresConfirmation,
        )

    private fun pack(actions: List<TypedSshAction>) =
        SshActionPack(CatalogId("sshpack.fixture"), CatalogVersion(1, 0, 0), actions)

    private fun typed(id: String, actionId: String) = TypedSshAction(CatalogId(id), CatalogId(actionId), id)

    private fun action(
        id: String,
        command: String?,
        dangerous: Boolean = false,
        origin: CommandOrigin = CommandOrigin.Bundled,
        kind: ActionKind = ActionKind.Ssh,
    ) = DeckAction(
        id = id,
        label = id,
        kind = kind,
        icon = ActionIcon.Terminal,
        command = command,
        dangerous = dangerous,
        commandOrigin = origin,
    )
}
