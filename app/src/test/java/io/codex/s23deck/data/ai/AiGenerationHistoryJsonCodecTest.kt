package io.codex.s23deck.data.ai

import io.codex.s23deck.domain.ai.AiGenerationRecord
import io.codex.s23deck.domain.ai.AiGenerationStatus
import io.codex.s23deck.domain.ai.DraftKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiGenerationHistoryJsonCodecTest {
    @Test
    fun encodeDecode_preservesProviderMetadataAndValidationErrors() {
        val encoded = AiGenerationHistoryJsonCodec.encode(
            listOf(
                AiGenerationRecord(
                    id = "generation_1",
                    providerId = "openai",
                    providerLabel = "OpenAI",
                    modelId = "gpt-5.5",
                    modelLabel = "GPT-5.5",
                    draftKind = DraftKind.Automation,
                    status = AiGenerationStatus.Failed,
                    message = "Validation failed",
                    validationErrors = listOf("steps[0].url: URL must be absolute http or https"),
                    artifactId = null,
                    createdAtMillis = 123L,
                ),
            ),
        )

        val decoded = AiGenerationHistoryJsonCodec.decode(encoded).single()

        assertEquals("openai", decoded.providerId)
        assertEquals("gpt-5.5", decoded.modelId)
        assertEquals(DraftKind.Automation, decoded.draftKind)
        assertEquals(AiGenerationStatus.Failed, decoded.status)
        assertEquals(listOf("steps[0].url: URL must be absolute http or https"), decoded.validationErrors)
    }

    @Test
    fun encode_doesNotContainApiKeys() {
        val encoded = AiGenerationHistoryJsonCodec.encode(
            listOf(
                AiGenerationRecord(
                    id = "generation_1",
                    providerId = "openai",
                    providerLabel = "OpenAI",
                    modelId = "gpt-5.5",
                    modelLabel = "GPT-5.5",
                    draftKind = DraftKind.Action,
                    status = AiGenerationStatus.Ready,
                    message = "Ready",
                    validationErrors = emptyList(),
                    artifactId = "artifact_1",
                ),
            ),
        )

        assertFalse(encoded.contains("sk-test-secret"))
        assertFalse(encoded.contains("apiKey"))
    }
}
