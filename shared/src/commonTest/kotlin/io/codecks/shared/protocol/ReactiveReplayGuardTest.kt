package io.codecks.shared.protocol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReactiveReplayGuardTest {
    private fun request(sequence: Long, requestId: String = "request-$sequence") =
        ReactiveRequestEnvelope(
            sessionId = "session",
            sequence = sequence,
            requestId = requestId,
            deadlineMillis = 100,
            bodyJson = "{}",
            authTag = "valid",
        )

    @Test
    fun rejectsReplayGapWrongSessionAndBadAuthWithoutAdvancing() {
        val guard = ReactiveReplayGuard("session") { it.authTag == "valid" }
        assertTrue(guard.accept(request(1), 50))
        assertFalse(guard.accept(request(1, "new-request"), 50))
        assertFalse(guard.accept(request(3), 50))
        assertFalse(guard.accept(request(2).copy(sessionId = "other"), 50))
        assertFalse(guard.accept(request(2).copy(authTag = "bad"), 50))
        assertTrue(guard.accept(request(2), 50))
    }

    @Test
    fun responseGuardRejectsDuplicateRequestAndAlteredAuth() {
        val guard = ReactiveResponseReplayGuard("session") { it.authTag == "valid" }
        val first = response(sequence = 1, requestId = "one")
        assertTrue(guard.accept(first))
        assertFalse(guard.accept(response(sequence = 2, requestId = "one")))
        assertFalse(guard.accept(response(sequence = 2, requestId = "two").copy(authTag = "bad")))
        assertTrue(guard.accept(response(sequence = 2, requestId = "two")))
    }

    private fun response(sequence: Long, requestId: String) = ReactiveResponseEnvelope(
        sessionId = "session",
        sequence = sequence,
        requestId = requestId,
        status = ReceiptStatus.Completed,
        authTag = "valid",
    )
}
