package io.codecks.domain.ai

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

    @Test
    fun coverageManifest_isVersionedCompleteAndCategoryRich() {
        val manifest = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("ai/ai_creator_v2_eval_coverage.tsv"),
        ).bufferedReader().useLines { lines -> lines.filter(String::isNotBlank).toList() }

        assertEquals("schemaVersion\t2", manifest.first())
        val assignments = manifest.drop(1).flatMap { line ->
            val (category, rangeText) = line.split('\t')
            val (first, last) = rangeText.split('-').map(String::toInt)
            (first..last).map { caseNumber -> caseNumber to category }
        }
        val requiredCategories = setOf(
            "button",
            "deck",
            "automation",
            "unsupported_theme",
            "artifact_conversion",
            "artifact_codec_roundtrip",
            "refine_once",
            "malformed_output",
            "adversarial_command",
            "prompt_injection",
            "oversized_content",
            "provider_error",
            "review_metadata",
            "disabled_automation",
            "regenerate",
        )

        assertEquals((1..120).toList(), assignments.map { it.first }.sorted())
        assertEquals(120, assignments.map { it.first }.distinct().size)
        assertEquals(requiredCategories, assignments.map { it.second }.toSet())
        assertTrue(assignments.groupingBy { it.second }.eachCount().values.all { it >= 8 })
    }
}
