package io.codecks.data.persistence

import io.codecks.data.ai.AiArtifactJsonCodec
import io.codecks.data.ai.AiGenerationHistoryJsonCodec
import io.codecks.data.clipboard.ClipboardLastSyncCodec
import io.codecks.data.privacy.DiagnosticEventCodec
import io.codecks.data.smart.SmartLearningCodec
import org.junit.Assert.assertTrue
import org.junit.Test

class HighRiskCodecBoundsTest {
    @Test
    fun `corrupt oversized and future payloads fail closed`() {
        val oversized = "x".repeat(2 * 1024 * 1024)
        assertTrue(AiGenerationHistoryJsonCodec.decode(oversized).isEmpty())
        assertTrue(AiArtifactJsonCodec.decode(oversized).isEmpty())
        assertTrue(SmartLearningCodec.decode(oversized).isEmpty())
        assertTrue(DiagnosticEventCodec.decode(oversized).isEmpty())
        assertTrue(ClipboardLastSyncCodec.decode(oversized) == null)

        assertTrue(AiGenerationHistoryJsonCodec.decode("{\"schemaVersion\":999,\"items\":[]}").isEmpty())
        assertTrue(AiArtifactJsonCodec.decode("{\"schemaVersion\":999,\"items\":[]}").isEmpty())
        assertTrue(SmartLearningCodec.decode("{\"schemaVersion\":999,\"events\":[]}").isEmpty())
        assertTrue(DiagnosticEventCodec.decode("{\"schemaVersion\":999,\"events\":[]}").isEmpty())
    }
}
