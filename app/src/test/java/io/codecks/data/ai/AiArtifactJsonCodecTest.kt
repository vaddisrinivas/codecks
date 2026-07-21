package io.codecks.data.ai

import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactAction
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.ai.AiArtifactParameter
import io.codecks.domain.ai.AiArtifactReview
import io.codecks.domain.ai.AiArtifactRiskLevel
import io.codecks.domain.ai.AiArtifactStepReview
import io.codecks.domain.ai.AiArtifactTest
import io.codecks.domain.ai.AiArtifactTestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiArtifactJsonCodecTest {
    @Test
    fun roundTrip_preservesArtifactActionsAndLastTest() {
        val artifact =
            AiArtifact(
                id = "artifact-1",
                kind = AiArtifactKind.Automation,
                title = "Morning setup",
                description = "Open workspace",
                prompt = "make morning automation",
                createdAtMillis = 1234L,
                actions = listOf(
                    AiArtifactAction(
                        id = "open",
                        title = "Open Docs",
                        command = "open https://example.com",
                        dangerous = false,
                    ),
                ),
                review = AiArtifactReview(
                    assumptions = listOf("Use browser workspace"),
                    riskLevel = AiArtifactRiskLevel.Dangerous,
                    requiresConfirmation = true,
                    target = "Active Mac",
                    trigger = "Manual trigger until explicitly enabled",
                    requiredCapabilities = listOf("Advanced", "Clipboard"),
                    parameters = listOf(
                        AiArtifactParameter(
                            name = "workspace",
                            label = "Workspace",
                            required = true,
                            defaultValue = "docs",
                        ),
                    ),
                    steps = listOf(
                        AiArtifactStepReview(
                            id = "open_step",
                            label = "Open Docs",
                            type = "Open URL",
                            summary = "https://example.com",
                            requiresConfirmation = false,
                        ),
                    ),
                ),
                lastTest = AiArtifactTest(
                    status = AiArtifactTestStatus.Succeeded,
                    message = "Looks safe",
                    timestampMillis = 5678L,
                ),
            )

        val encoded = AiArtifactJsonCodec.encode(listOf(artifact))
        val decoded = AiArtifactJsonCodec.decode(encoded)

        assertTrue(encoded.contains("schemaVersion"))
        assertEquals(listOf(artifact), decoded)
    }

    @Test
    fun decode_readsLegacyArrayFormat() {
        val decoded = AiArtifactJsonCodec.decode(
            """[{"id":"legacy","kind":"Button","title":"Legacy","prompt":"","actions":[{"command":"open https://example.com"}]}]""",
        )

        assertEquals("legacy", decoded.single().id)
        assertEquals("open https://example.com", decoded.single().actions.single().command)
    }

    @Test
    fun decode_skipsCorruptArtifactsAndActionsWithoutDroppingValidArtifacts() {
        val raw =
            """
            [
              {
                "id":"valid-1",
                "kind":"Button",
                "title":"Valid One",
                "createdAtMillis":100,
                "actions":[
                  {"id":"good","title":"Good","command":"open https://example.com","dangerous":false},
                  {"id":"bad","title":"Bad","dangerous":false},
                  "not an action"
                ]
              },
              {"title":"missing id should be skipped"},
              "not an artifact",
              {
                "id":"valid-2",
                "kind":"Deck",
                "title":"Valid Two",
                "actions":[{"command":"echo ok"}],
                "lastTest":{"status":"Failed","message":"dry run","timestampMillis":200}
              }
            ]
            """.trimIndent()

        val decoded = AiArtifactJsonCodec.decode(raw)

        assertEquals(2, decoded.size)
        assertEquals("valid-1", decoded[0].id)
        assertEquals(listOf("good"), decoded[0].actions.map { it.id })
        assertEquals("valid-2", decoded[1].id)
        assertEquals("action_0", decoded[1].actions.single().id)
        assertEquals(AiArtifactTestStatus.Failed, decoded[1].lastTest?.status)
    }

    @Test
    fun decode_returnsEmptyListForMalformedRoot() {
        assertEquals(emptyList<AiArtifact>(), AiArtifactJsonCodec.decode("""{"not":"array"}"""))
        assertEquals(emptyList<AiArtifact>(), AiArtifactJsonCodec.decode("not json"))
    }
}
