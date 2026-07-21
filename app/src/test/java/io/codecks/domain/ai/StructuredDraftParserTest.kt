package io.codecks.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredDraftParserTest {
    private val parser = StructuredDraftParser()

    @Test
    fun parsesActionDraftFromJsonFence() {
        val result =
            parser.parse(
                DraftRequest(prompt = "open docs", modelId = "gpt-5-mini"),
                ActionDraftJson(
                    """
                    ```json
                    {"prompt":"open docs","definition":{"id":"draft.open","title":"Open Docs","steps":[{"id":"step-1","type":"open_url","url":"https://example.com"}]}}
                    ```
                    """.trimIndent(),
                ),
            ).getOrThrow()

        assertTrue(result is GeneratedDraft.Action)
        result as GeneratedDraft.Action
        assertEquals("draft.open", result.draft.definition.id)
        assertEquals("https://example.com", result.draft.definition.steps.single().url)
    }

    @Test
    fun parsesAutomationDraft() {
        val result =
            parser.parse(
                DraftRequest(prompt = "daily automation", modelId = "gpt-5-mini", draftKind = DraftKind.Automation),
                ActionDraftJson(
                    """{"prompt":"daily automation","id":"automation.daily","label":"Daily Sync","category":"Workspace","definition":{"id":"draft.sync","title":"Sync Notes","steps":[{"id":"step-1","type":"shell","value":"sync","requiredCapabilities":["Advanced"]}]}}""",
                ),
            ).getOrThrow()

        assertTrue(result is GeneratedDraft.Automation)
        result as GeneratedDraft.Automation
        assertEquals("automation.daily", result.draft.id)
        assertEquals("draft.sync", result.draft.definition.id)
    }

    @Test
    fun parsesDeckDraft() {
        val result =
            parser.parse(
                DraftRequest(prompt = "coding deck", modelId = "gpt-5-mini", draftKind = DraftKind.Deck),
                ActionDraftJson(
                    """{"prompt":"coding deck","id":"deck.coding","title":"Coding Deck","actions":[{"id":"draft.github","title":"Open GitHub","steps":[{"id":"step-1","type":"open_url","url":"https://github.com"}]},{"id":"draft.terminal","title":"Open Terminal","steps":[{"id":"step-1","type":"shell","value":"open -a Terminal","requiredCapabilities":["Advanced"]}],"requiredCapabilities":["Advanced"]}]}""",
                ),
            ).getOrThrow()

        assertTrue(result is GeneratedDraft.Deck)
        result as GeneratedDraft.Deck
        assertEquals("deck.coding", result.draft.id)
        assertEquals(2, result.draft.actions.size)
        assertEquals("Open GitHub", result.draft.actions.first().title)
    }

    @Test
    fun rejectsProseWrappedJson() {
        val result =
            parser.parse(
                DraftRequest(prompt = "open docs", modelId = "gpt-5-mini"),
                ActionDraftJson(
                    """
                    Here is the draft:
                    {"prompt":"open docs","definition":{"id":"draft.open","title":"Open Docs","steps":[{"id":"step-1","type":"open_url","url":"https://example.com"}]}}
                    Looks good.
                    """.trimIndent(),
                ),
            )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsJsonFenceWithExtraNarration() {
        val result =
            parser.parse(
                DraftRequest(prompt = "open docs", modelId = "gpt-5-mini"),
                ActionDraftJson(
                    """
                    I made this:
                    ```json
                    {"prompt":"open docs","definition":{"id":"draft.open","title":"Open Docs","steps":[{"id":"step-1","type":"open_url","url":"https://example.com"}]}}
                    ```
                    """.trimIndent(),
                ),
            )

        assertTrue(result.isFailure)
    }
}
