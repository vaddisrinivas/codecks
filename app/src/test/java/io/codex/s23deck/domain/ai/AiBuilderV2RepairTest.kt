package io.codex.s23deck.domain.ai

import io.codex.s23deck.data.ai.AiProviderException
import io.codex.s23deck.domain.commerce.Entitlement
import io.codex.s23deck.domain.commerce.EntitlementStatus
import io.codex.s23deck.domain.commerce.EntitlementTier
import io.codex.s23deck.domain.commerce.FakeEntitlementRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBuilderV2RepairTest {
    @Test
    fun requestValidatedDraft_retriesOnceForSemanticValidationErrors() = runTest {
        val provider = QueueAiProvider(
            listOf(
                actionEnvelope(url = "not-a-url"),
                actionEnvelope(url = "https://docs.example.com"),
            ),
        )
        val builder = builder(provider)

        val result = builder.requestValidatedDraft(DraftRequest("open docs", "gpt-5.5")).getOrThrow()

        val draft = result as GeneratedDraft.Action
        assertEquals("https://docs.example.com", draft.draft.definition.steps.single().url)
        assertEquals(2, provider.requests.size)
        assertTrue(provider.requests.last().repairInstructions.contains("steps[0].url"))
        assertTrue(provider.requests.last().repairInstructions.contains("URL must be absolute http or https"))
    }

    @Test
    fun requestValidatedDraft_doesNotRetryClarifyingQuestion() = runTest {
        val provider = QueueAiProvider(
            listOf(
                """
                {
                  "schemaVersion": 2,
                  "status": "needs_input",
                  "message": "Need a target app.",
                  "questions": ["Which app should this open?"],
                  "assumptions": [],
                  "proposal": null
                }
                """.trimIndent(),
            ),
        )
        val builder = builder(provider)

        val result = builder.requestValidatedDraft(DraftRequest("make a button", "gpt-5.5"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AiDraftProposalUnavailable)
        assertEquals(1, provider.requests.size)
        assertTrue(provider.requests.single().repairInstructions.isBlank())
    }

    @Test
    fun requestValidatedDraft_stopsAfterSingleRepairAttempt() = runTest {
        val provider = QueueAiProvider(
            listOf(
                actionEnvelope(url = "not-a-url"),
                actionEnvelope(url = "still-not-a-url"),
            ),
        )
        val builder = builder(provider)

        val result = builder.requestValidatedDraft(DraftRequest("open docs", "gpt-5.5"))

        assertTrue(result.isFailure)
        assertEquals(2, provider.requests.size)
        assertFalse(provider.requests.last().repairInstructions.isBlank())
    }

    @Test
    fun requestValidatedDraft_rejectsOversizedDeckAfterSingleRepair() = runTest {
        val provider = QueueAiProvider(
            listOf(
                deckEnvelope(actionCount = 13),
                deckEnvelope(actionCount = 13),
            ),
        )
        val builder = builder(provider)

        val result = builder.requestValidatedDraft(DraftRequest("make huge deck", "gpt-5.5", DraftKind.Deck))

        assertTrue(result.isFailure)
        assertEquals(2, provider.requests.size)
        assertTrue(provider.requests.last().repairInstructions.contains("at most 12"))
    }

    private fun builder(provider: AiProvider): AiBuilder =
        AiBuilder(
            provider = provider,
            validator = ActionDraftValidator(),
            entitlementRepository = FakeEntitlementRepository(
                Entitlement(EntitlementTier.Premium, EntitlementStatus.Active),
            ),
        )

    private fun actionEnvelope(url: String): String =
        """
        {
          "schemaVersion": 2,
          "status": "ready",
          "message": "Ready",
          "questions": [],
          "assumptions": [],
          "proposal": {
            "id": "open_docs",
            "title": "Open Docs",
            "description": "Open docs",
            "requiredCapabilities": [],
            "target": {"type": "AnyConnected", "id": null},
            "safety": {
              "level": "Normal",
              "requiresConfirmation": false,
              "confirmationTitle": null,
              "confirmationBody": null
            },
            "steps": [
              {
                "id": "open",
                "type": "open_url",
                "label": "Open docs",
                "url": "$url",
                "text": null,
                "delayMs": null,
                "templateId": null,
                "requiresConfirmation": false
              }
            ]
          }
        }
        """.trimIndent()

    private fun deckEnvelope(actionCount: Int): String {
        val actions = (1..actionCount).joinToString(",") { index ->
            """
            {
              "id": "open_$index",
              "title": "Open $index",
              "description": "Open docs $index",
              "requiredCapabilities": [],
              "target": {"type": "AnyConnected", "id": null},
              "safety": {
                "level": "Normal",
                "requiresConfirmation": false,
                "confirmationTitle": null,
                "confirmationBody": null
              },
              "steps": [
                {
                  "id": "open",
                  "type": "open_url",
                  "label": "Open",
                  "url": "https://docs.example.com/$index",
                  "text": null,
                  "delayMs": null,
                  "templateId": null,
                  "requiresConfirmation": false
                }
              ]
            }
            """.trimIndent()
        }
        return """
        {
          "schemaVersion": 2,
          "status": "ready",
          "message": "Ready",
          "questions": [],
          "assumptions": [],
          "proposal": {
            "id": "huge",
            "title": "Huge",
            "description": "Too many actions",
            "actions": [$actions]
          }
        }
        """.trimIndent()
    }
}

private class QueueAiProvider(
    responses: List<String>,
) : AiProvider {
    private val queue = ArrayDeque(responses.map(::ActionDraftJson))
    val requests = mutableListOf<DraftRequest>()

    override suspend fun listModels(): Result<List<AiModel>> =
        Result.success(listOf(AiModel("gpt-5.5", "GPT-5.5")))

    override suspend fun test(): Result<Unit> = Result.success(Unit)

    override suspend fun draftAction(request: DraftRequest): Result<ActionDraftJson> {
        requests += request
        return queue.removeFirstOrNull()?.let(Result.Companion::success)
            ?: Result.failure(AiProviderException.RemoteFailure("No queued response"))
    }
}
