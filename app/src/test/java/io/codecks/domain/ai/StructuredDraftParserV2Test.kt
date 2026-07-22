package io.codecks.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredDraftParserV2Test {
    private val parser = StructuredDraftParser()

    @Test
    fun readyActionEnvelope_compilesTypedStepsIntoActionDefinition() {
        val result = parser.parse(
            DraftRequest("open docs and keep mac awake", "gpt-5.5", DraftKind.Action),
            ActionDraftJson(
                """
                {
                  "schemaVersion": 2,
                  "status": "ready",
                  "message": "Ready",
                  "questions": [],
                  "assumptions": ["Use browser docs"],
                  "proposal": {
                    "id": "docs_focus",
                    "title": "Docs Focus",
                    "description": "Open docs and ping the Mac awake.",
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
                        "url": "https://docs.example.com",
                        "text": null,
                        "delayMs": null,
                        "templateId": null,
                        "requiresConfirmation": false
                      },
                      {
                        "id": "awake",
                        "type": "template",
                        "label": "Wake display",
                        "url": null,
                        "text": null,
                        "delayMs": null,
                        "templateId": "mac.focus_ping_30",
                        "requiresConfirmation": false
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        ).getOrThrow()

        val draft = result as GeneratedDraft.Action
        assertEquals(listOf("Use browser docs"), draft.draft.metadata.assumptions)
        assertEquals("Docs Focus", draft.draft.definition.title)
        assertEquals(ActionStepTypes.OpenUrl, draft.draft.definition.steps.first().type)
        assertEquals("https://docs.example.com", draft.draft.definition.steps.first().url)
        assertEquals(ActionStepTypes.Shell, draft.draft.definition.steps.last().type)
        assertEquals("caffeinate -u -t 30", draft.draft.definition.steps.last().value)
    }

    @Test
    fun needsInputEnvelope_returnsClarifyingFailure() {
        val result = parser.parse(
            DraftRequest("make it better", "gpt-5.5", DraftKind.Action),
            ActionDraftJson(
                """
                {
                  "schemaVersion": 2,
                  "status": "needs_input",
                  "message": "I need one detail.",
                  "questions": ["Which app should the button open?"],
                  "assumptions": [],
                  "proposal": null
                }
                """.trimIndent(),
            ),
        )

        val error = result.exceptionOrNull()
        assertTrue(error is AiDraftProposalUnavailable)
        assertTrue(error?.message.orEmpty().contains("Which app"))
    }

    @Test
    fun unsupportedTemplate_doesNotCompile() {
        val result = parser.parse(
            DraftRequest("run cleanup", "gpt-5.5", DraftKind.Action),
            ActionDraftJson(
                """
                {
                  "schemaVersion": 2,
                  "status": "ready",
                  "message": "Ready",
                  "questions": [],
                  "assumptions": [],
                  "proposal": {
                    "id": "cleanup",
                    "title": "Cleanup",
                    "description": "Cleanup",
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
                        "id": "cleanup",
                        "type": "template",
                        "label": "Cleanup",
                        "url": null,
                        "text": null,
                        "delayMs": null,
                        "templateId": "mac.rm_rf",
                        "requiresConfirmation": false
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Unsupported built-in template"))
    }

    @Test
    fun freeCommand_compilesWhenHardSafetyPolicyAllowsIt() {
        val result = parser.parse(
            DraftRequest("show front app", "gpt-5.5", DraftKind.Action),
            ActionDraftJson(
                """
                {
                  "schemaVersion": 2,
                  "status": "ready",
                  "message": "Ready",
                  "questions": [],
                  "assumptions": [],
                  "proposal": {
                    "id": "front_app",
                    "title": "Front App",
                    "description": "Print the frontmost app.",
                    "requiredCapabilities": ["Shell", "Ssh"],
                    "target": {"type": "AnyConnected", "id": null},
                    "safety": {"level": "Normal", "requiresConfirmation": false, "confirmationTitle": null, "confirmationBody": null},
                    "steps": [{
                      "id": "run", "type": "command", "label": "Read front app",
                      "url": null, "text": null, "delayMs": null, "templateId": null,
                      "command": "osascript -e 'tell application \"System Events\" to get name of first application process whose frontmost is true'",
                      "requiresConfirmation": false
                    }]
                  }
                }
                """.trimIndent(),
            ),
        ).getOrThrow() as GeneratedDraft.Action

        assertEquals(ActionStepTypes.Shell, result.draft.definition.steps.single().type)
        assertTrue(result.draft.definition.steps.single().value.orEmpty().contains("frontmost"))
    }

    @Test
    fun v2CapabilityAliases_areNormalizedAsAdvisoryMetadata() {
        val result = parser.parse(
            DraftRequest("open docs", "gemini-2.5-flash", DraftKind.Action),
            ActionDraftJson(
                """
                {
                  "schemaVersion": 2,
                  "status": "ready",
                  "message": "Ready",
                  "questions": [],
                  "assumptions": [],
                  "proposal": {
                    "id": "docs",
                    "title": "Docs",
                    "description": "Open docs",
                    "requiredCapabilities": ["open_url", "url_opener", "mac"],
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
                        "url": "https://docs.example.com",
                        "text": null,
                        "delayMs": null,
                        "templateId": null,
                        "requiresConfirmation": false
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        ).getOrThrow()

        val draft = result as GeneratedDraft.Action
        assertEquals(
            listOf(ActionCapability.Browser, ActionCapability.HidKeyboard),
            draft.draft.definition.requiredCapabilities,
        )
    }
}
