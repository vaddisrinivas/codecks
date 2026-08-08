package io.codecks.shared.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PortableSnapshotTest {
    private val allowlist = PortableSnapshotAllowlist(
        catalogActionIds = setOf("browser_back", "play_pause"),
        localRouteIds = setOf("route.trackpad", "route.keyboard"),
        routineIds = setOf("focus", "writing"),
    )
    private val codec = PortableSnapshotCodec(allowlist)

    @Test
    fun goldenRoundTripProducesDisabledRestoreCandidate() {
        val snapshot = sampleSnapshot()
        val encoded = codec.encode(snapshot)

        assertEquals(GOLDEN_JSON, encoded)
        val accepted = assertIs<PortableSnapshotDecodeResult.Accepted>(codec.decode(encoded))
        assertEquals(snapshot, accepted.candidate.snapshot)
        assertTrue(accepted.candidate.snapshot.automations.all { !it.enabled })
    }

    @Test
    fun canonicalEncodingIsDeterministicAcrossInputOrdering() {
        val canonical = sampleSnapshot()
        val reordered = canonical.copy(
            decks = canonical.decks.reversed().map { it.copy(slots = it.slots.reversed()) },
            catalog = canonical.catalog.reversed(),
            routineFavorites = canonical.routineFavorites.reversed(),
            automations = canonical.automations.reversed().map { it.copy(steps = it.steps.reversed()) },
            displayLabels = canonical.displayLabels.reversed(),
        )

        assertEquals(codec.encode(canonical), codec.encode(reordered))
    }

    @Test
    fun localOnlyActionCandidatesAreOmittedWithoutRetainingSecretCanaries() {
        val projector = PortableActionProjector(allowlist)
        val secrets = listOf(
            PersistedActionCandidate.RawCommand("curl https://secret.invalid?token=RAW_CANARY"),
            PersistedActionCandidate.TestCommand("TEST_CANARY"),
            PersistedActionCandidate.CleanupCommand("CLEANUP_CANARY"),
        )

        val omissions = secrets.mapIndexed { index, candidate ->
            val result = projector.project(candidate, "automation/$index")
            assertIs<SnapshotActionProjection.Omitted>(result).omission
        }
        val report = omissions.joinToString()

        assertEquals(
            listOf(
                PersistedSnapshotField.RawCommand,
                PersistedSnapshotField.TestCommand,
                PersistedSnapshotField.CleanupCommand,
            ),
            omissions.map(SnapshotOmission::field),
        )
        assertTrue(omissions.all { it.code == SnapshotOmissionCode.LocalOnly })
        assertFalse(report.contains("RAW_CANARY"))
        assertFalse(report.contains("TEST_CANARY"))
        assertFalse(report.contains("CLEANUP_CANARY"))
        assertFalse(secrets.joinToString().contains("CANARY"))
        assertFalse(codec.encode(sampleSnapshot()).contains("CANARY"))
        assertFailsWith<IllegalArgumentException> {
            codec.encode(
                sampleSnapshot().copy(
                    displayLabels = listOf(
                        PortableDisplayLabel(
                            PortableDisplayLabelKey.Workspace,
                            "api_key=DISPLAY_SECRET_CANARY",
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun unknownActionIdsAreVisiblyOmittedAndNeverProjected() {
        val result = PortableActionProjector(allowlist).project(
            PersistedActionCandidate.Catalog("future_unknown_action"),
            "deck/main/slot/0",
        )
        val omitted = assertIs<SnapshotActionProjection.Omitted>(result).omission

        assertEquals(SnapshotOmissionCode.NotAllowlisted, omitted.code)
        assertEquals(PersistedSnapshotField.CatalogActionId, omitted.field)
        assertTrue(omitted.safeMessage.contains("omitted"))
    }

    @Test
    fun allowlistDefensivelyCopiesCallerOwnedSets() {
        val callerOwnedCatalog = mutableSetOf("browser_back")
        val frozenAllowlist = PortableSnapshotAllowlist(
            catalogActionIds = callerOwnedCatalog,
            localRouteIds = emptySet(),
            routineIds = emptySet(),
        )
        callerOwnedCatalog += "late_injected_action"

        val result = PortableActionProjector(frozenAllowlist).project(
            PersistedActionCandidate.Catalog("late_injected_action"),
            "catalog/late",
        )
        assertIs<SnapshotActionProjection.Omitted>(result)
    }

    @Test
    fun everyCandidateFieldHasAnExplicitCloudClassification() {
        assertTrue(PersistedSnapshotField.entries.all { it.cloudPolicy in SnapshotCloudPolicy.entries })
        assertEquals(SnapshotCloudPolicy.CLOUD_ALLOWED, PersistedSnapshotField.DeckLayout.cloudPolicy)
        assertEquals(SnapshotCloudPolicy.CLOUD_ALLOWED, PersistedSnapshotField.AutomationDefinition.cloudPolicy)
        assertEquals(SnapshotCloudPolicy.LOCAL_ONLY, PersistedSnapshotField.SshPrivateKey.cloudPolicy)
        assertEquals(SnapshotCloudPolicy.LOCAL_ONLY, PersistedSnapshotField.AiCredential.cloudPolicy)
        assertEquals(SnapshotCloudPolicy.LOCAL_ONLY, PersistedSnapshotField.ClipboardContent.cloudPolicy)
        assertEquals(SnapshotCloudPolicy.LOCAL_ONLY, PersistedSnapshotField.DeviceIdentifier.cloudPolicy)
        assertEquals(SnapshotCloudPolicy.LOCAL_ONLY, PersistedSnapshotField.DiagnosticPayload.cloudPolicy)
    }

    @Test
    fun malformedOversizedAndInvalidContentFailClosed() {
        assertEquals(
            SnapshotRejectionReason.MalformedOrUnsupportedValue,
            assertIs<PortableSnapshotDecodeResult.Rejected>(codec.decode("{not-json")).reason,
        )
        assertEquals(
            SnapshotRejectionReason.TooLarge,
            assertIs<PortableSnapshotDecodeResult.Rejected>(
                codec.decode("x".repeat(PORTABLE_SNAPSHOT_MAX_BYTES + 1)),
            ).reason,
        )
        assertEquals(
            SnapshotRejectionReason.InvalidContent,
            assertIs<PortableSnapshotDecodeResult.Rejected>(
                codec.decode(GOLDEN_JSON.replace("\"rows\":2", "\"rows\":9")),
            ).reason,
        )
        assertFailsWith<IllegalArgumentException> {
            codec.encode(
                sampleSnapshot().copy(
                    displayLabels = listOf(
                        PortableDisplayLabel(
                            PortableDisplayLabelKey.Workspace,
                            "x".repeat(PORTABLE_SNAPSHOT_MAX_LABEL_CHARS + 1),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun secretShapedIdentifiersAndDiagnosticPathsFailClosed() {
        val secretId = "sk-1234567890ABCDEF"
        assertFailsWith<IllegalArgumentException> {
            codec.encode(sampleSnapshot().copy(decks = listOf(sampleSnapshot().decks.single().copy(id = secretId))))
        }
        assertFailsWith<IllegalArgumentException> {
            PortableSnapshotAllowlist(
                catalogActionIds = setOf(secretId),
                localRouteIds = emptySet(),
                routineIds = emptySet(),
            )
        }

        val omission = assertIs<SnapshotActionProjection.Omitted>(
            PortableActionProjector(allowlist).project(
                PersistedActionCandidate.RawCommand("RAW_CANARY"),
                secretId,
            ),
        ).omission
        assertEquals("portable-item", omission.path)
        assertFalse(omission.toString().contains(secretId))
    }

    @Test
    fun unknownEnumActionKindAndFieldFailClosed() {
        val unknownEnum = GOLDEN_JSON.replace("\"mode\":\"oled\"", "\"mode\":\"future\"")
        val unknownActionKind = GOLDEN_JSON.replace("\"kind\":\"catalog\"", "\"kind\":\"raw_command\"")
        val unknownField = GOLDEN_JSON.replace("\"sourceAppVersion\":", "\"futureField\":true,\"sourceAppVersion\":")

        listOf(unknownEnum, unknownActionKind, unknownField).forEach { encoded ->
            val rejected = assertIs<PortableSnapshotDecodeResult.Rejected>(codec.decode(encoded))
            assertEquals(SnapshotRejectionReason.MalformedOrUnsupportedValue, rejected.reason)
            assertTrue(rejected.safeMessage.isNotBlank())
        }
    }

    @Test
    fun enabledAutomationAndNonAllowlistedRestoreDataAreRejected() {
        val enabled = GOLDEN_JSON.replace("\"enabled\":false", "\"enabled\":true")
        assertEquals(
            SnapshotRejectionReason.InvalidContent,
            assertIs<PortableSnapshotDecodeResult.Rejected>(codec.decode(enabled)).reason,
        )

        val foreignAction = GOLDEN_JSON.replace("browser_back", "dangerous_action")
        assertEquals(
            SnapshotRejectionReason.InvalidContent,
            assertIs<PortableSnapshotDecodeResult.Rejected>(codec.decode(foreignAction)).reason,
        )
    }

    private fun sampleSnapshot(): PortableSnapshotV1 = PortableSnapshotV1(
        sourceAppVersion = "0.1.36",
        decks = listOf(
            PortableDeck(
                id = "main",
                label = "Main",
                rows = 2,
                columns = 2,
                slots = listOf(
                    PortableDeckSlot(
                        row = 0,
                        column = 0,
                        label = "Back",
                        icon = PortableIcon.Browser,
                        colorHex = "#00FFAA",
                        action = PortableActionReference.Catalog("browser_back"),
                    ),
                    PortableDeckSlot(
                        row = 1,
                        column = 1,
                        label = "Keyboard",
                        icon = PortableIcon.Keyboard,
                        colorHex = "#FFFFFF",
                        action = PortableActionReference.LocalRoute("route.keyboard"),
                    ),
                ),
            ),
        ),
        catalog = listOf(
            PortableCatalogItem(
                id = "back_item",
                label = "Browser back",
                icon = PortableIcon.Browser,
                colorHex = "#00FFAA",
                action = PortableActionReference.Catalog("browser_back"),
            ),
        ),
        theme = PortableThemePreferences(
            mode = PortableThemeMode.Oled,
            deckStyle = PortableDeckStyle.StreamDeckPro,
            accentColorHex = "#00FF99",
        ),
        routineFavorites = listOf("focus", "writing"),
        automations = listOf(
            PortableAutomationDefinition(
                id = "morning",
                label = "Morning",
                trigger = PortableAutomationTrigger.Manual,
                steps = listOf(
                    PortableAutomationStep(0, PortableActionReference.LocalRoute("route.trackpad")),
                    PortableAutomationStep(1, PortableActionReference.Catalog("play_pause")),
                ),
                enabled = false,
            ),
        ),
        displayLabels = listOf(
            PortableDisplayLabel(PortableDisplayLabelKey.Workspace, "Desk"),
        ),
    )

    private companion object {
        const val GOLDEN_JSON = "{\"schema\":\"codecks.portable_snapshot.v1\",\"sourceAppVersion\":\"0.1.36\",\"decks\":[{\"id\":\"main\",\"label\":\"Main\",\"rows\":2,\"columns\":2,\"slots\":[{\"row\":0,\"column\":0,\"label\":\"Back\",\"icon\":\"browser\",\"colorHex\":\"#00FFAA\",\"action\":{\"kind\":\"catalog\",\"id\":\"browser_back\"}},{\"row\":1,\"column\":1,\"label\":\"Keyboard\",\"icon\":\"keyboard\",\"colorHex\":\"#FFFFFF\",\"action\":{\"kind\":\"local_route\",\"id\":\"route.keyboard\"}}]}],\"catalog\":[{\"id\":\"back_item\",\"label\":\"Browser back\",\"icon\":\"browser\",\"colorHex\":\"#00FFAA\",\"action\":{\"kind\":\"catalog\",\"id\":\"browser_back\"}}],\"theme\":{\"mode\":\"oled\",\"deckStyle\":\"stream_deck_pro\",\"accentColorHex\":\"#00FF99\",\"motionEnabled\":true,\"highContrast\":false},\"routineFavorites\":[\"focus\",\"writing\"],\"automations\":[{\"id\":\"morning\",\"label\":\"Morning\",\"trigger\":\"manual\",\"steps\":[{\"order\":0,\"action\":{\"kind\":\"local_route\",\"id\":\"route.trackpad\"}},{\"order\":1,\"action\":{\"kind\":\"catalog\",\"id\":\"play_pause\"}}],\"enabled\":false}],\"displayLabels\":[{\"key\":\"workspace\",\"value\":\"Desk\"}]}"
    }
}
