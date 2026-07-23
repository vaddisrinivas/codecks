package io.codecks.data.smart

import io.codecks.domain.smart.SmartFeedback
import io.codecks.domain.smart.SmartFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLearningCodecTest {
    @Test
    fun codecDoesNotPersistRawContentFields() {
        val encoded = SmartLearningCodec.encode(
            listOf(
                feedback(
                    actionId = "open_docs",
                    contextKeys = setOf("macApp:chrome", "hour:12"),
                ),
            ),
        )

        assertTrue(encoded.contains("open_docs"))
        assertFalse(encoded.contains("clipboard"))
        assertFalse(encoded.contains("typed text"))
        assertFalse(encoded.contains("command"))
        assertFalse(encoded.contains("private-path"))
    }

    @Test
    fun corruptPayloadRecoversToEmpty() {
        assertEquals(emptyList<SmartFeedback>(), SmartLearningCodec.decode("{not-json"))
    }

    @Test
    fun summaryAppliesRetentionAndClearEquivalent() {
        val now = SmartLearningCodec.RETENTION_MS + 10_000L
        val fresh = feedback(actionId = "fresh", atMillis = now - 1_000L, type = SmartFeedbackType.Run)
        val old = feedback(actionId = "old", atMillis = 0L, type = SmartFeedbackType.Run)

        val summary = SmartLearningCodec.summary(listOf(fresh, old), now)

        assertEquals(0, summary.actionScores["fresh"])
        assertFalse(summary.actionScores.containsKey("old"))
        assertEquals(SmartLearningCodec.summary(emptyList(), now), SmartLearningCodec.summary(SmartLearningCodec.decode(null), now))
    }

    @Test
    fun neverForAppStoresOnlyAppActionKey() {
        val summary = SmartLearningCodec.summary(
            listOf(feedback(actionId = "reload", appKey = "chrome", type = SmartFeedbackType.NeverForApp)),
            nowMillis = 2_000L,
        )

        assertEquals(setOf("chrome:reload"), summary.neverAppActionKeys)
    }

    @Test
    fun successAndFailureCarryLearningScore() {
        val summary = SmartLearningCodec.summary(
            listOf(
                feedback(actionId = "reload", type = SmartFeedbackType.Success, atMillis = 1_000L),
                feedback(actionId = "broken", type = SmartFeedbackType.Failure, atMillis = 2_000L),
            ),
            nowMillis = 3_000L,
        )

        assertEquals(6, summary.actionScores["reload"])
        assertEquals(-8, summary.actionScores["broken"])
    }

    @Test
    fun successHistoryBuildsTransitionScores() {
        val summary = SmartLearningCodec.summary(
            listOf(
                feedback(actionId = "calendar", type = SmartFeedbackType.Success, atMillis = 1_000L),
                feedback(actionId = "notes", type = SmartFeedbackType.Success, atMillis = 2_000L),
            ),
            nowMillis = 3_000L,
        )

        assertEquals(10, summary.transitionScores["calendar->notes"])
    }

    @Test
    fun neverForAppDoesNotSuppressOtherApps() {
        val summary = SmartLearningCodec.summary(
            listOf(feedback(actionId = "reload", appKey = "chrome", type = SmartFeedbackType.NeverForApp)),
            nowMillis = 2_000L,
        )

        assertFalse("safari:reload" in summary.neverAppActionKeys)
    }

    private fun feedback(
        actionId: String,
        appKey: String? = "chrome",
        contextKeys: Set<String> = setOf("macApp:chrome"),
        type: SmartFeedbackType = SmartFeedbackType.Run,
        atMillis: Long = 1_000L,
    ) = SmartFeedback(
        candidateId = "smart:$actionId",
        actionId = actionId,
        appKey = appKey,
        type = type,
        success = null,
        coarseHourBucket = 12,
        contextKeys = contextKeys,
        atMillis = atMillis,
    )
}
