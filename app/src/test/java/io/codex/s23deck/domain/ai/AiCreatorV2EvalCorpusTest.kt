package io.codex.s23deck.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCreatorV2EvalCorpusTest {
    @Test
    fun corpus_containsRequiredPromptCounts() {
        val lines = requireNotNull(javaClass.classLoader?.getResourceAsStream("ai/ai_creator_v2_eval_corpus.tsv")) {
            "Missing AI Creator V2 eval corpus"
        }.bufferedReader().useLines { sequence ->
            sequence.filter { it.isNotBlank() }.toList()
        }

        val groups = lines
            .map { line -> line.substringBefore('\t') to line.substringAfter('\t') }
            .groupBy({ it.first }, { it.second })

        assertEquals(120, lines.size)
        assertEquals(40, groups.getValue("Action").size)
        assertEquals(40, groups.getValue("Deck").size)
        assertEquals(40, groups.getValue("Automation").size)
        assertTrue(groups.values.flatten().all { it.length >= 8 })
    }
}
