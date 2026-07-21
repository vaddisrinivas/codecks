package io.codecks.domain.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexBridgeSnapshotParserTest {
    @Test
    fun `parser converts privacy safe bridge snapshot to cockpit snapshot`() {
        val snapshot = CodexBridgeSnapshotParser.parse(EXAMPLE_JSON)

        assertEquals(2, snapshot.tasks.size)
        assertEquals(1, snapshot.runningCount)
        assertEquals(1, snapshot.attentionCount)
        assertEquals("bridge status-only", snapshot.quotaLabel)
        assertEquals("mock", snapshot.tasks.first().source)
        assertTrue(snapshot.buttons.isNotEmpty())
        assertTrue(snapshot.themes.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parser rejects snapshots that include prompt content`() {
        CodexBridgeSnapshotParser.parse(EXAMPLE_JSON.replace("\"promptContentIncluded\": false", "\"promptContentIncluded\": true"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parser rejects snapshots that include source content`() {
        CodexBridgeSnapshotParser.parse(EXAMPLE_JSON.replace("\"sourceContentIncluded\": false", "\"sourceContentIncluded\": true"))
    }

    private companion object {
        private const val EXAMPLE_JSON = """
            {
              "version": 1,
              "generatedAt": "2026-07-20T22:00:00-04:00",
              "source": "mock",
              "privacy": {
                "promptContentIncluded": false,
                "sourceContentIncluded": false,
                "notes": "Status-only."
              },
              "tasks": [
                {
                  "id": "task-cockpit-ui",
                  "title": "Build Codex cockpit",
                  "repoPath": "~/Projects/codecks",
                  "branch": "codex/all-waves-trackpad-hid",
                  "state": "Running",
                  "elapsedLabel": "18m",
                  "updatedLabel": "now",
                  "needsAttention": false,
                  "hasUnread": false,
                  "effortMode": "Deep",
                  "safeSummary": "Mock dashboard."
                },
                {
                  "id": "task-appfunctions",
                  "title": "Park AppFunctions",
                  "repoPath": "~/Projects/codecks/android",
                  "branch": "main",
                  "state": "Blocked",
                  "elapsedLabel": "parked",
                  "updatedLabel": "today",
                  "needsAttention": true,
                  "hasUnread": false,
                  "effortMode": "Unknown",
                  "safeSummary": "Integration is preserved but paused."
                }
              ]
            }
        """
    }
}

