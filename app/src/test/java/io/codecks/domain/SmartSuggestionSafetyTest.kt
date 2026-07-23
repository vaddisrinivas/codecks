package io.codecks.domain

import io.codecks.core.actions.commandRevision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSuggestionSafetyTest {
    @Test
    fun untestedAiActionIsNotRunnableFromSmartSuggestions() {
        val action = aiAction(requiresTest = true, liveSafe = false)

        assertFalse(action.isRunnableFromSmartSuggestion())
    }

    @Test
    fun testedAiActionIsRunnableOnlyWhenItsCurrentRevisionWasChecked() {
        val pending = aiAction(requiresTest = false, liveSafe = true)
        val verified = pending.copy(
            commandReview = CommandReview(checkedRevision = pending.commandRevision()),
        )

        assertFalse(pending.isRunnableFromSmartSuggestion())
        assertTrue(verified.isRunnableFromSmartSuggestion())
        assertFalse(verified.copy(command = "open -a Terminal").isRunnableFromSmartSuggestion())
    }

    @Test
    fun nonAiActionRemainsEligibleForSmartSuggestions() {
        assertTrue(
            DeckAction(
                id = "finder",
                label = "Finder",
                kind = ActionKind.Ssh,
                icon = ActionIcon.Finder,
                command = "open -a Finder",
                commandOrigin = CommandOrigin.Bundled,
            ).isRunnableFromSmartSuggestion(),
        )
    }

    private fun aiAction(requiresTest: Boolean, liveSafe: Boolean) = DeckAction(
        id = "ai_action",
        label = "Open Finder",
        kind = ActionKind.Ssh,
        icon = ActionIcon.Apps,
        command = "open -a Finder",
        commandOrigin = CommandOrigin.AiGenerated,
        requiresTest = requiresTest,
        liveSafe = liveSafe,
    )
}
