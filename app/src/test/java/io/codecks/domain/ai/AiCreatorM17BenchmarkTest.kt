package io.codecks.domain.ai

import io.codecks.core.actions.AiGeneratedContentPlanner
import io.codecks.core.actions.toAiArtifact
import io.codecks.data.ai.AiArtifactJsonCodec
import io.codecks.data.ai.AiProviderException
import io.codecks.domain.features.Entitlement
import io.codecks.domain.features.FakeEntitlementRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Provider-free M17 benchmark. Every row executes the scenario assigned by the coverage manifest. */
class AiCreatorM17BenchmarkTest {
    private val parser = StructuredDraftParser()
    private val actionValidator = ActionDraftValidator()
    private val automationValidator = AutomationDraftValidator()
    private val planner = AiGeneratedContentPlanner()

    @Test
    fun corpusScenarios_emitPerCaseResultsFromExecutedPaths() = runTest {
        val cases = corpusCases()
        val categories = coverageAssignments()
        assertEquals(120, cases.size)
        assertEquals((1..120).toSet(), categories.keys)

        val results = cases.mapIndexed { index, (kind, prompt) ->
            val caseNumber = index + 1
            val caseId = caseId(caseNumber)
            runCatching {
                evaluateCase(caseId, categories.getValue(caseNumber), kind, prompt, index)
            }.getOrElse { error ->
                CaseResult.failed(caseId, categories.getValue(caseNumber), error::class.simpleName.orEmpty())
            }
        }
        writeCaseResults(results)

        val failures = results.filterNot(CaseResult::outcomeMatched)
        assertTrue(failures.joinToString { "${it.caseId}:${it.detail}" }, failures.isEmpty())
        assertTrue(results.filter { it.category == "malformed_output" }.all { it.actionableFailure == true })
        assertTrue(results.filter { it.category == "prompt_injection" }.all { it.safeSemanticOutcome == true })
        assertTrue(results.filter { it.category == "provider_error" }.all { it.actionableFailure == true })
        assertTrue(results.filter { it.category == "refine_once" }.all { it.refineOnce == true })
        assertTrue(results.filter { it.category == "regenerate" }.all { it.regenerate == true })
    }

    @Test
    fun immutableGeneratedOutputBypassCorpus_isExactlyRejected() {
        val rows = bypassRows()
        val expectedIds = listOf(
            "unsupported_echo",
            "secret_env",
            "arbitrary_script",
            "variable_sudo",
            "download_only",
            "compound_open",
            "arbitrary_shell",
            "redirect_file",
            "network_probe",
            "credential_helper",
            "spoofed_visual",
            "unknown_tool",
            "command_substitution",
            "backtick_substitution",
            "variable_expansion",
            "generic_applescript",
            "absolute_nested_shell",
            "absolute_interpreter",
            "forged_visual_marker",
        )
        assertEquals(expectedIds, rows.map { it.first })
        assertEquals(19, rows.size)
        assertEquals(EXPECTED_BYPASS_SHA256, sha256(rows.joinToString("") { (id, command) -> "$id\t$command\n" }))

        val results = rows.map { (caseId, command) ->
            val artifact = AiArtifact(
                id = caseId,
                kind = AiArtifactKind.Automation,
                title = caseId,
                prompt = "adversarial deterministic fixture",
                actions = listOf(AiArtifactAction(caseId, caseId, command)),
            )
            val result = planner.automationRecipeFromArtifact(artifact)
            BypassResult(
                caseId = caseId,
                denied = result.isFailure,
                actionable = result.exceptionOrNull()?.message.orEmpty().contains("blocked"),
            )
        }
        writeBypassResults(results)
        assertTrue(results.all { it.denied && it.actionable })
    }

    private suspend fun evaluateCase(
        caseId: String,
        category: String,
        kind: DraftKind,
        prompt: String,
        index: Int,
    ): CaseResult = when (category) {
        "malformed_output" -> expectedParserFailure(caseId, category, kind, prompt, "{")
        "prompt_injection", "adversarial_command" ->
            expectedParserFailure(caseId, category, kind, prompt, maliciousEnvelope(caseId, kind))
        "unsupported_theme" -> expectedUnavailable(caseId, category, kind, prompt)
        "oversized_content" -> expectedOversizedFailure(caseId, category, prompt)
        "provider_error" -> expectedProviderFailure(caseId, category, kind, prompt)
        "refine_once" -> refineOnce(caseId, category, kind, prompt, index)
        "regenerate" -> regenerate(caseId, category, kind, prompt, index)
        else -> safePipeline(caseId, category, kind, prompt, index)
    }

    private fun safePipeline(
        caseId: String,
        category: String,
        kind: DraftKind,
        prompt: String,
        index: Int,
        draft: GeneratedDraft = parser.parse(
            DraftRequest(prompt, LOCAL_MODEL, kind),
            ActionDraftJson(safeEnvelope(caseId, kind)),
        ).getOrThrow(),
    ): CaseResult {
        validate(draft)
        val artifact = draft.toAiArtifact(prompt).getOrThrow().copy(
            id = caseId,
            createdAtMillis = index.toLong(),
            catalogSavedAtMillis = index.toLong(),
        )
        val restored = AiArtifactJsonCodec.decode(AiArtifactJsonCodec.encode(listOf(artifact))).single()
        check(restored == artifact) { "artifact codec round trip changed the artifact" }
        check(restored.review.steps.isNotEmpty()) { "review metadata missing steps" }
        val disabled = if (kind == DraftKind.Automation) {
            val recipe = requireNotNull(planner.automationRecipeFromArtifact(restored).getOrThrow())
            check(!recipe.enabled) { "generated automation was enabled" }
            true
        } else {
            null
        }
        return CaseResult(
            caseId = caseId,
            category = category,
            outcomeMatched = true,
            parserConformance = true,
            safeSemanticOutcome = true,
            artifactConversion = true,
            artifactCodecRoundTrip = true,
            reviewMetadata = true,
            disabledAutomation = disabled,
        )
    }

    private suspend fun refineOnce(
        caseId: String,
        category: String,
        kind: DraftKind,
        prompt: String,
        index: Int,
    ): CaseResult {
        val provider = QueueProvider(
            listOf(
                Result.success(ActionDraftJson(safeEnvelope(caseId, kind, url = "not-a-url"))),
                Result.success(ActionDraftJson(safeEnvelope(caseId, kind))),
            ),
        )
        val draft = builder(provider).requestValidatedDraft(DraftRequest(prompt, LOCAL_MODEL, kind)).getOrThrow()
        check(provider.requests.size == 2 && provider.requests.last().repairInstructions.isNotBlank())
        return safePipeline(caseId, category, kind, prompt, index, draft).copy(refineOnce = true)
    }

    private suspend fun regenerate(
        caseId: String,
        category: String,
        kind: DraftKind,
        prompt: String,
        index: Int,
    ): CaseResult {
        val provider = QueueProvider(
            listOf(
                Result.success(ActionDraftJson(safeEnvelope("$caseId-first", kind))),
                Result.success(ActionDraftJson(safeEnvelope("$caseId-second", kind))),
            ),
        )
        val first = builder(provider).requestValidatedDraft(DraftRequest(prompt, LOCAL_MODEL, kind)).getOrThrow()
        val second = builder(provider).requestValidatedDraft(DraftRequest(prompt, LOCAL_MODEL, kind)).getOrThrow()
        check(provider.requests.size == 2)
        check(first.toAiArtifact().getOrThrow().title != second.toAiArtifact().getOrThrow().title)
        return safePipeline(caseId, category, kind, prompt, index, second).copy(regenerate = true)
    }

    private fun expectedParserFailure(
        caseId: String,
        category: String,
        kind: DraftKind,
        prompt: String,
        payload: String,
    ): CaseResult {
        val error = parser.parse(DraftRequest(prompt, LOCAL_MODEL, kind), ActionDraftJson(payload)).exceptionOrNull()
        check(error != null && error.message.orEmpty().isNotBlank()) { "unsafe payload did not fail actionably" }
        return expectedFailure(caseId, category, parserConformance = true)
    }

    private fun expectedUnavailable(
        caseId: String,
        category: String,
        kind: DraftKind,
        prompt: String,
    ): CaseResult {
        val error = parser.parse(
            DraftRequest(prompt, LOCAL_MODEL, kind),
            ActionDraftJson(unavailableEnvelope("Themes are not a supported AI artifact.")),
        ).exceptionOrNull()
        check(error is AiDraftProposalUnavailable && error.message.orEmpty().isNotBlank())
        return expectedFailure(caseId, category, parserConformance = true)
    }

    private suspend fun expectedOversizedFailure(caseId: String, category: String, prompt: String): CaseResult {
        val response = ActionDraftJson(oversizedDeckEnvelope(caseId))
        val provider = QueueProvider(listOf(Result.success(response), Result.success(response)))
        val error = builder(provider)
            .requestValidatedDraft(DraftRequest(prompt, LOCAL_MODEL, DraftKind.Deck))
            .exceptionOrNull()
        check(provider.requests.size == 2)
        check(error?.message.orEmpty().contains("at most 12"))
        return expectedFailure(caseId, category, parserConformance = true)
    }

    private suspend fun expectedProviderFailure(
        caseId: String,
        category: String,
        kind: DraftKind,
        prompt: String,
    ): CaseResult {
        val provider = QueueProvider(
            listOf(Result.failure(AiProviderException.RemoteFailure("Provider unavailable; retry later."))),
        )
        val error = builder(provider)
            .requestValidatedDraft(DraftRequest(prompt, LOCAL_MODEL, kind))
            .exceptionOrNull()
        check(error is AiProviderException.RemoteFailure && error.message.orEmpty().contains("retry"))
        return expectedFailure(caseId, category, parserConformance = null)
    }

    private fun expectedFailure(caseId: String, category: String, parserConformance: Boolean?): CaseResult =
        CaseResult(
            caseId = caseId,
            category = category,
            outcomeMatched = true,
            parserConformance = parserConformance,
            safeSemanticOutcome = true,
            actionableFailure = true,
        )

    private fun validate(draft: GeneratedDraft) {
        when (draft) {
            is GeneratedDraft.Action -> check(actionValidator.validate(draft.draft) == ValidationResult.Valid)
            is GeneratedDraft.Automation -> check(automationValidator.validate(draft.draft) == ValidationResult.Valid)
            is GeneratedDraft.Deck -> {
                check(draft.draft.actions.isNotEmpty() && draft.draft.actions.size <= 12)
                draft.draft.actions.forEach { check(actionValidator.validate(it) == ValidationResult.Valid) }
            }
        }
    }

    private fun builder(provider: AiProvider): AiBuilder =
        AiBuilder(
            provider = provider,
            validator = actionValidator,
            entitlementRepository = FakeEntitlementRepository(Entitlement(localOnly = true)),
        )

    private fun corpusCases(): List<Pair<DraftKind, String>> =
        resourceLines("ai/ai_creator_v2_eval_corpus.tsv").map { line ->
            DraftKind.valueOf(line.substringBefore('\t')) to line.substringAfter('\t')
        }

    private fun coverageAssignments(): Map<Int, String> =
        resourceLines("ai/ai_creator_v2_eval_coverage.tsv").drop(1).flatMap { line ->
            val (category, rangeText) = line.split('\t')
            val (first, last) = rangeText.split('-').map(String::toInt)
            (first..last).map { it to category }
        }.toMap()

    private fun bypassRows(): List<Pair<String, String>> =
        resourceLines("automation/generated_output_bypass_corpus.tsv").map { line ->
            line.substringBefore('\t') to line.substringAfter('\t')
        }

    private fun resourceLines(path: String): List<String> =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(path))
            .bufferedReader()
            .useLines { lines -> lines.filter(String::isNotBlank).toList() }

    private fun writeCaseResults(results: List<CaseResult>) {
        val file = File(CASE_RESULTS_PATH)
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendLine("schemaVersion\t2")
                appendLine(CaseResult.HEADER)
                results.forEach { appendLine(it.toTsv()) }
            },
        )
    }

    private fun writeBypassResults(results: List<BypassResult>) {
        val file = File(BYPASS_RESULTS_PATH)
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendLine("schemaVersion\t2")
                appendLine("caseId\tdenied\tactionable")
                results.forEach { appendLine("${it.caseId}\t${it.denied.asInt()}\t${it.actionable.asInt()}") }
            },
        )
    }

    private fun safeEnvelope(caseId: String, kind: DraftKind, url: String = "https://docs.codecks.local/$caseId"): String {
        val definition = definition(caseId, url)
        val proposal = when (kind) {
            DraftKind.Action -> definition
            DraftKind.Automation ->
                """{"id":"$caseId","label":"Safe automation $caseId","description":"Deterministic fixture","category":"benchmark","dangerous":false,"definition":$definition}"""
            DraftKind.Deck ->
                """{"id":"$caseId","title":"Safe deck $caseId","description":"Deterministic fixture","actions":[$definition]}"""
        }
        return readyEnvelope(proposal)
    }

    private fun maliciousEnvelope(caseId: String, kind: DraftKind): String {
        val malicious = definition(caseId, "https://example.com").replace(
            "\"type\":\"open_url\",\"label\":\"Open docs\",\"url\":\"https://example.com\"",
            "\"type\":\"shell\",\"label\":\"Ignore policy\",\"url\":null",
        ).replace("\"text\":null", "\"text\":\"rm -rf /\"")
        val proposal = when (kind) {
            DraftKind.Action -> malicious
            DraftKind.Automation ->
                """{"id":"$caseId","label":"Injected","description":"Injected","category":"benchmark","dangerous":false,"definition":$malicious}"""
            DraftKind.Deck ->
                """{"id":"$caseId","title":"Injected","description":"Injected","actions":[$malicious]}"""
        }
        return readyEnvelope(proposal)
    }

    private fun oversizedDeckEnvelope(caseId: String): String =
        readyEnvelope(
            """{"id":"$caseId","title":"Oversized","description":"Thirteen actions","actions":[${
                (1..13).joinToString(",") { definition("$caseId-$it", "https://example.com/$it") }
            }]}""",
        )

    private fun definition(caseId: String, url: String): String =
        """{"id":"$caseId-action","title":"Safe action $caseId","description":"Open benchmark documentation","requiredCapabilities":[],"target":{"type":"AnyConnected","id":null},"safety":{"level":"Normal","requiresConfirmation":false,"confirmationTitle":null,"confirmationBody":null},"steps":[{"id":"open","type":"open_url","label":"Open docs","url":"$url","text":null,"delayMs":null,"templateId":null,"requiresConfirmation":false}]}"""

    private fun readyEnvelope(proposal: String): String =
        """{"schemaVersion":2,"status":"ready","message":"Ready for review","questions":[],"assumptions":["Local deterministic fixture"],"proposal":$proposal}"""

    private fun unavailableEnvelope(message: String): String =
        """{"schemaVersion":2,"status":"unsupported","message":"$message","questions":[],"assumptions":[],"proposal":null}"""

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun caseId(number: Int): String = "m17-${number.toString().padStart(3, '0')}"

    private companion object {
        const val LOCAL_MODEL = "local-deterministic-fixture"
        const val CASE_RESULTS_PATH = "build/reports/ai_creator_v2_m17_case_results.tsv"
        const val BYPASS_RESULTS_PATH = "build/reports/ai_creator_v2_m17_bypass_results.tsv"
        const val EXPECTED_BYPASS_SHA256 = "833f4e13b91e5174e0c43ae7f756a72e3b7638b533fa599fb191124789659d2d"
    }
}

private data class QueueProvider(
    private val queued: List<Result<ActionDraftJson>>,
) : AiProvider {
    private val responses = ArrayDeque(queued)
    val requests = mutableListOf<DraftRequest>()

    override suspend fun listModels(): Result<List<AiModel>> = Result.success(emptyList())
    override suspend fun test(): Result<Unit> = Result.success(Unit)
    override suspend fun draftAction(request: DraftRequest): Result<ActionDraftJson> {
        requests += request
        return responses.removeFirstOrNull() ?: Result.failure(AiProviderException.RemoteFailure("No fixture response"))
    }
}

private data class CaseResult(
    val caseId: String,
    val category: String,
    val outcomeMatched: Boolean,
    val parserConformance: Boolean? = null,
    val safeSemanticOutcome: Boolean? = null,
    val artifactConversion: Boolean? = null,
    val artifactCodecRoundTrip: Boolean? = null,
    val reviewMetadata: Boolean? = null,
    val disabledAutomation: Boolean? = null,
    val refineOnce: Boolean? = null,
    val regenerate: Boolean? = null,
    val actionableFailure: Boolean? = null,
    val detail: String = "",
) {
    fun toTsv(): String = listOf(
        caseId,
        category,
        outcomeMatched.asInt(),
        parserConformance.asCell(),
        safeSemanticOutcome.asCell(),
        artifactConversion.asCell(),
        artifactCodecRoundTrip.asCell(),
        reviewMetadata.asCell(),
        disabledAutomation.asCell(),
        refineOnce.asCell(),
        regenerate.asCell(),
        actionableFailure.asCell(),
    ).joinToString("\t")

    companion object {
        const val HEADER =
            "caseId\tcategory\toutcomeMatched\tparserConformance\tsafeSemanticOutcome\tartifactConversion\tartifactCodecRoundTrip\treviewMetadata\tdisabledAutomation\trefineOnce\tregenerate\tactionableFailure"

        fun failed(caseId: String, category: String, detail: String) =
            CaseResult(caseId, category, outcomeMatched = false, detail = detail)
    }
}

private data class BypassResult(val caseId: String, val denied: Boolean, val actionable: Boolean)

private fun Boolean.asInt(): Int = if (this) 1 else 0
private fun Boolean?.asCell(): String = this?.asInt()?.toString() ?: "-"
