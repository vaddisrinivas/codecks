package io.codecks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTargetIdentityMigrationTest {
    @Test
    fun legacyEndpointIdentityIsReplacedAndCurrentSelectionFollowsIt() {
        val legacyId = "mac_alice_studio_local_22"
        val migratedId = "8ee80175-c04f-4b03-ae3c-ad18f129a553"
        val legacy = target(id = legacyId)

        val migration = migrateConnectionTargetIdentities(
            storedTargets = listOf(legacy),
            legacyTarget = legacy,
            currentTargetId = legacyId,
            newId = { migratedId },
        )

        assertEquals(listOf(migratedId), migration.targets.map(ConnectionTarget::id))
        assertEquals(migratedId, migration.currentTargetId)
        assertFalse(migration.targets.single().id.contains("alice"))
        assertFalse(migration.targets.single().id.contains("studio"))
    }

    @Test
    fun migratedIdentityRemainsStableForTheSameEndpoint() {
        val opaqueId = "8ee80175-c04f-4b03-ae3c-ad18f129a553"
        var generatedIds = 0
        val persisted = target(id = opaqueId)

        val migration = migrateConnectionTargetIdentities(
            storedTargets = listOf(persisted),
            legacyTarget = persisted.copy(id = opaqueId),
            currentTargetId = opaqueId,
            newId = {
                generatedIds += 1
                "5b1d3b35-d5c0-451e-90ef-4dc2bc086369"
            },
        )

        assertEquals(listOf(persisted), migration.targets)
        assertEquals(opaqueId, migration.currentTargetId)
        assertEquals(0, generatedIds)
    }

    @Test
    fun standaloneLegacyEndpointGetsOneOpaqueIdentityWithoutDuplication() {
        val migratedId = "5b1d3b35-d5c0-451e-90ef-4dc2bc086369"
        val legacy = target(id = "")

        val migration = migrateConnectionTargetIdentities(
            storedTargets = emptyList(),
            legacyTarget = legacy,
            currentTargetId = null,
            newId = { migratedId },
        )

        assertEquals(1, migration.targets.size)
        assertEquals(migratedId, migration.targets.single().id)
        assertEquals(migratedId, migration.currentTargetId)
        assertTrue(migration.targets.single().isConfigured)
    }

    @Test
    fun currentDuplicateWinsWhileSameEndpointKeepsOneIdentity() {
        val stale = target(
            id = "5b1d3b35-d5c0-451e-90ef-4dc2bc086369",
            hostKey = "",
        )
        val current = target(
            id = "8ee80175-c04f-4b03-ae3c-ad18f129a553",
            hostKey = "studio.local ssh-ed25519 key",
        )

        val migration = migrateConnectionTargetIdentities(
            storedTargets = listOf(stale, current),
            legacyTarget = current,
            currentTargetId = current.id,
        )

        assertEquals(listOf(current), migration.targets)
        assertEquals(current.id, migration.currentTargetId)
    }

    @Test
    fun legacySelectorMigrationIsExplicitAndNormalResolutionIsUuidExact() {
        val opaqueId = "8ee80175-c04f-4b03-ae3c-ad18f129a553"
        val target = target(id = opaqueId)

        assertEquals(
            mapOf("mac_alice_studio_local_22" to opaqueId),
            legacyConnectionTargetIdMigrations(listOf(target)),
        )
    }

    @Test
    fun ambiguousLegacyAliasIsNotOfferedForPersistentMigration() {
        val first = target(id = "8ee80175-c04f-4b03-ae3c-ad18f129a553")
        val second = target(id = "5b1d3b35-d5c0-451e-90ef-4dc2bc086369")
            .copy(host = "studio-local")

        assertTrue(legacyConnectionTargetIdMigrations(listOf(first, second)).isEmpty())
    }

    @Test
    fun corruptTargetsPayloadIsDistinguishedFromAnEmptyTargetList() {
        val corrupt = """[{"id":"first","host":"studio.local","user":"alice"},{"broken":true}]"""

        val result = decodeConnectionTargets(corrupt, hasKey = true)

        assertEquals(ConnectionTargetsDecodeResult.Failure(corrupt), result)
        assertEquals(
            ConnectionTargetsDecodeResult.Success(emptyList()),
            decodeConnectionTargets("", hasKey = true),
        )
    }

    @Test
    fun invalidPortMakesWholePayloadUndecodableInsteadOfDroppingOneTarget() {
        val partial = """
            [
              {"id":"first","host":"studio.local","user":"alice","port":22},
              {"id":"second","host":"backup.local","user":"bob","port":"invalid"}
            ]
        """.trimIndent()

        assertEquals(
            ConnectionTargetsDecodeResult.Failure(partial),
            decodeConnectionTargets(partial, hasKey = true),
        )
        assertEquals(
            ConnectionTargetStorageMigration.PreserveUndecodable(partial),
            planConnectionTargetStorageMigration(
                rawTargets = partial,
                hasKey = true,
                legacyTarget = target(id = ""),
                currentTargetId = "mac_alice_studio_local_22",
            ),
        )
    }

    @Test
    fun validLegacyPayloadStillDecodesForIdentityMigration() {
        val raw = """[{"id":"mac_alice_studio_local_22","host":"studio.local","user":"alice","port":22}]"""

        val result = decodeConnectionTargets(raw, hasKey = false)

        assertTrue(result is ConnectionTargetsDecodeResult.Success)
        assertEquals(
            "mac_alice_studio_local_22",
            (result as ConnectionTargetsDecodeResult.Success).targets.single().id,
        )
    }

    @Test
    fun validStorageMigrationReturnsOnlyOpaquePersistableJson() {
        val legacyId = "mac_alice_studio_local_22"
        val opaqueId = "8ee80175-c04f-4b03-ae3c-ad18f129a553"
        val raw = """[{"id":"$legacyId","host":"studio.local","user":"alice","port":22}]"""

        val result = planConnectionTargetStorageMigration(
            rawTargets = raw,
            hasKey = false,
            legacyTarget = target(id = legacyId),
            currentTargetId = legacyId,
            newId = { opaqueId },
        )

        assertTrue(result is ConnectionTargetStorageMigration.Ready)
        result as ConnectionTargetStorageMigration.Ready
        assertEquals(opaqueId, result.currentTargetId)
        assertTrue(result.targetsJson.contains(opaqueId))
        assertFalse(result.targetsJson.contains(legacyId))
    }

    private fun target(
        id: String,
        hostKey: String = "studio.local ssh-ed25519 key",
    ) = ConnectionTarget(
        id = id,
        host = "studio.local",
        port = 22,
        user = "alice",
        hasKey = true,
        hostKey = hostKey,
    )
}
