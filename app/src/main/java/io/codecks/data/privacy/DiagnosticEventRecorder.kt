package io.codecks.data.privacy

import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticEvent
import io.codecks.domain.privacy.DiagnosticEventCode
import io.codecks.domain.privacy.DiagnosticResultCode

fun DiagnosticEventStore.recordTerminal(
    component: DiagnosticComponent,
    result: DiagnosticResultCode,
    attempt: Int = 0,
    durationMs: Long = 0L,
    timestampEpochMs: Long = System.currentTimeMillis(),
) {
    record(
        DiagnosticEvent(
            component = component,
            event = DiagnosticEventCode.ATTEMPT_FINISHED,
            result = result,
            attempt = attempt.coerceIn(0, 1_000),
            durationMs = durationMs.coerceIn(0L, 24L * 60L * 60L * 1_000L),
            timestampEpochMs = timestampEpochMs.coerceAtLeast(0L),
        ),
    )
}
