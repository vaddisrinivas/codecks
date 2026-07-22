package io.codecks.data.ai

import io.codecks.core.actions.toAiArtifact
import io.codecks.domain.ai.ActionDraftJson
import io.codecks.domain.ai.ActionDraftValidator
import io.codecks.domain.ai.AiBuilder
import io.codecks.domain.ai.AiDraftProposalUnavailable
import io.codecks.domain.ai.AiModel
import io.codecks.domain.ai.AiProvider
import io.codecks.domain.ai.AiProviderCatalog
import io.codecks.domain.ai.DraftKind
import io.codecks.domain.ai.DraftRequest
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.ai.SemanticDraftValidationException
import io.codecks.domain.features.Entitlement
import io.codecks.domain.features.EntitlementRepository
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in live gate for AI Creator V2.
 *
 * Normal unit tests never call paid/network providers. To run:
 *
 * CODECKS_AI_V2_LIVE_EVAL=true \
 * CODECKS_AI_V2_LIVE_PROVIDERS=openai,anthropic,gemini \
 * ./gradlew testDebugUnitTest --tests io.codecks.data.ai.AiCreatorV2LiveEvalTest
 *
 * Load secrets through the canonical agent-env wrapper; this test never prints or writes key values.
 */
class AiCreatorV2LiveEvalTest {
    @Test
    fun liveProvidersMeetAiCreatorV2ReleaseGates() = runBlocking {
        assumeTrue("Set CODECKS_AI_V2_LIVE_EVAL=true to run live provider eval", envFlag("CODECKS_AI_V2_LIVE_EVAL"))

        val corpus = evalCorpus().let { rows ->
            envInt("CODECKS_AI_V2_LIVE_LIMIT")?.let(rows::take) ?: rows
        }
        val providers = liveProviderConfigs()
        require(providers.isNotEmpty()) {
            "No live providers configured. Set provider keys and CODECKS_AI_V2_LIVE_PROVIDERS."
        }

        val results = evaluateAll(providers, corpus)
        writeLiveReport(corpus, providers, results)

        if (envFlag("CODECKS_AI_V2_LIVE_ASSERT_GATES", default = true)) {
            val total = results.size
            val schemaPassRate = results.count { it.schemaParsed }.percentOf(total)
            val firstPassRate = results.count { it.firstPassValid }.percentOf(total)
            val repairedRate = results.count { it.validAfterRepair }.percentOf(total)
            val unapprovedCompiles = results.count { it.policyFailure }

            assertTrue("Schema parse gate failed: $schemaPassRate", schemaPassRate == 100.0)
            assertTrue("First-pass semantic gate failed: $firstPassRate", firstPassRate >= 95.0)
            assertTrue("After-repair semantic gate failed: $repairedRate", repairedRate >= 99.0)
            assertTrue("Unapproved command compile gate failed: $unapprovedCompiles", unapprovedCompiles == 0)
        }
    }

    private suspend fun evaluateAll(
        providers: List<LiveProviderConfig>,
        corpus: List<EvalPrompt>,
    ): List<LiveEvalResult> = coroutineScope {
        val concurrency = envInt("CODECKS_AI_V2_LIVE_CONCURRENCY") ?: DEFAULT_LIVE_CONCURRENCY
        val semaphore = Semaphore(concurrency)
        providers.flatMap { provider ->
            corpus.map { row ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { evaluate(provider, row) }
                }
            }
        }.awaitAll()
            .sortedWith(compareBy<LiveEvalResult> { it.providerId }.thenBy { it.promptIndex })
    }

    private suspend fun evaluate(
        provider: LiveProviderConfig,
        row: EvalPrompt,
    ): LiveEvalResult {
        var latest: LiveEvalResult? = null
        repeat(MAX_TRANSIENT_ATTEMPTS) { attempt ->
            val result = evaluateOnce(provider, row)
            latest = result
            if (!result.isTransientProviderFailure || attempt == MAX_TRANSIENT_ATTEMPTS - 1) {
                return result
            }
            delay((attempt + 1) * TRANSIENT_RETRY_DELAY_MS)
        }
        return requireNotNull(latest)
    }

    private suspend fun evaluateOnce(
        provider: LiveProviderConfig,
        row: EvalPrompt,
    ): LiveEvalResult {
        val countingProvider = CountingProvider(provider.create())
        val builder = AiBuilder(
            provider = countingProvider,
            validator = ActionDraftValidator(),
            entitlementRepository = LiveEvalEntitlementRepository,
        )
        val result = builder.requestValidatedDraft(
            DraftRequest(
                prompt = row.prompt,
                modelId = provider.modelId,
                draftKind = row.kind,
                agentContext = LIVE_EVAL_AGENT_CONTEXT,
            ),
        )
        val generated = result.getOrNull()
        val artifactResult = generated?.toAiArtifact(row.prompt)
        return LiveEvalResult(
            providerId = provider.providerId,
            providerLabel = provider.label,
            modelId = provider.modelId,
            kind = row.kind,
            promptIndex = row.index,
            requestCount = countingProvider.requestCount,
            schemaParsed = result.exceptionOrNull() !is AiProviderException.MalformedJson,
            firstPassValid = result.isSuccess && countingProvider.requestCount == 1 && artifactResult?.isSuccess == true,
            validAfterRepair = result.isSuccess && countingProvider.requestCount <= 2 && artifactResult?.isSuccess == true,
            policyFailure = result.isSuccess && artifactResult?.isFailure == true,
            failureClass = classifyFailure(result.exceptionOrNull() ?: artifactResult?.exceptionOrNull()),
            failureDetail = failureDetail(result.exceptionOrNull() ?: artifactResult?.exceptionOrNull()),
        )
    }

    private fun liveProviderConfigs(): List<LiveProviderConfig> {
        val requested = envList("CODECKS_AI_V2_LIVE_PROVIDERS")
            .ifEmpty { listOf("openai", "anthropic", "gemini") }
        val keyStore = EnvSecureApiKeyStore()
        return requested.mapNotNull { providerId ->
            val spec = AiProviderCatalog.byProviderId(providerId) ?: error("Unknown provider $providerId")
            val modelId = envModelOverride(providerId)
                ?: spec.models.firstOrNull { it.supportsStructuredDrafts }?.id
            when {
                keyStore.envKeyName(providerId) == null -> null
                modelId == null -> null
                providerId == "litellm" && env("LITELLM_BASE_URL").isNullOrBlank() -> null
                else -> LiveProviderConfig(
                    providerId = providerId,
                    label = spec.label,
                    modelId = modelId,
                    create = {
                        when (providerId) {
                            "openai" -> OpenAiProvider(keyStore)
                            "anthropic" -> AnthropicProvider(keyStore)
                            "gemini" -> GeminiProvider(keyStore)
                            "openrouter" -> OpenRouterProvider(keyStore)
                            "litellm" -> LiteLlmProvider(keyStore, baseUrl = env("LITELLM_BASE_URL").orEmpty())
                            else -> error("Unknown provider $providerId")
                        }
                    },
                )
            }
        }
    }

    private fun evalCorpus(): List<EvalPrompt> =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("ai/ai_creator_v2_eval_corpus.tsv")) {
            "Missing AI Creator V2 eval corpus"
        }.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }
                .mapIndexed { index, line ->
                    val kind = DraftKind.valueOf(line.substringBefore('\t'))
                    EvalPrompt(index = index + 1, kind = kind, prompt = line.substringAfter('\t'))
                }
                .toList()
        }

    private fun writeLiveReport(
        corpus: List<EvalPrompt>,
        providers: List<LiveProviderConfig>,
        results: List<LiveEvalResult>,
    ) {
        val report = liveReportPath()
        Files.createDirectories(report.parent)
        val total = results.size
        val failures = results.filterNot { it.validAfterRepair }.take(25)
        val lines = buildList {
            add("# AI Creator V2 Live Eval Report")
            add("")
            add("Generated: ${Instant.now()}")
            add("")
            add("## Scope")
            add("")
            add("- Prompts: ${corpus.size}")
            add("- Providers: ${providers.joinToString { "${it.label} (${it.modelId})" }}")
            add("- Full 120-prompt corpus: ${corpus.size == 120}")
            add("")
            add("## Aggregate Gates")
            add("")
            add("- Schema parse: ${results.count { it.schemaParsed }}/${total} (${results.count { it.schemaParsed }.percentOf(total)}%)")
            add("- First-pass semantic validity: ${results.count { it.firstPassValid }}/${total} (${results.count { it.firstPassValid }.percentOf(total)}%)")
            add("- Valid after one repair: ${results.count { it.validAfterRepair }}/${total} (${results.count { it.validAfterRepair }.percentOf(total)}%)")
            add("- Unapproved command policy failures after parse: ${results.count { it.policyFailure }}")
            add("")
            add("## By Provider")
            add("")
            providers.forEach { provider ->
                val providerResults = results.filter { it.providerId == provider.providerId }
                val providerTotal = providerResults.size
                add("### ${provider.label} (${provider.modelId})")
                add("")
                add("- Schema parse: ${providerResults.count { it.schemaParsed }}/${providerTotal} (${providerResults.count { it.schemaParsed }.percentOf(providerTotal)}%)")
                add("- First-pass semantic validity: ${providerResults.count { it.firstPassValid }}/${providerTotal} (${providerResults.count { it.firstPassValid }.percentOf(providerTotal)}%)")
                add("- Valid after repair: ${providerResults.count { it.validAfterRepair }}/${providerTotal} (${providerResults.count { it.validAfterRepair }.percentOf(providerTotal)}%)")
                add("")
            }
            add("## Failure Samples")
            add("")
            if (failures.isEmpty()) {
                add("- None")
            } else {
                failures.forEach { failure ->
                    add("- ${failure.providerLabel} ${failure.modelId} ${failure.kind} #${failure.promptIndex}: ${failure.failureClass}${failure.failureDetail}")
                }
            }
            add("")
            add("No API keys, auth headers, or raw model outputs are written to this report.")
            add("")
        }
        report.toFile().writeText(lines.joinToString("\n"))
    }

    private fun liveReportPath(): Path {
        env("CODECKS_AI_V2_LIVE_REPORT")?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
        return repoRoot().resolve("docs/ai/AI_CREATOR_V2_LIVE_EVAL_REPORT.md")
    }

    private fun repoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.exists(cursor.resolve("settings.gradle.kts"))) {
            cursor = cursor.parent ?: error("Could not find repo root from ${System.getProperty("user.dir")}")
        }
        return cursor
    }

    private fun classifyFailure(error: Throwable?): String =
        when (error) {
            null -> ""
            is AiDraftProposalUnavailable -> "proposal_${error.status.wireName}"
            is SemanticDraftValidationException -> "semantic_validation"
            is AiProviderException.AuthFailure -> "auth"
            is AiProviderException.RateLimited -> "rate_limited"
            is AiProviderException.Timeout -> "timeout"
            is AiProviderException.UnsupportedModel -> "unsupported_model"
            is AiProviderException.MalformedJson -> "malformed_json"
            is AiProviderException.Refused -> "provider_refused"
            is AiProviderException.Incomplete -> "incomplete"
            is AiProviderException.RemoteFailure -> "remote_failure"
            else -> error::class.simpleName ?: "unknown"
        }

    private fun failureDetail(error: Throwable?): String =
        when (error) {
            is AiProviderException.RemoteFailure,
            is AiProviderException.UnsupportedModel,
            is AiProviderException.RateLimited,
            is AiProviderException.Timeout,
            is AiProviderException.AuthFailure,
            is AiProviderException.MalformedJson,
            -> error.message?.takeIf { it.isNotBlank() }?.let { " · ${it.take(160)}" }.orEmpty()
            else -> ""
        }

    private fun env(name: String): String? = System.getenv(name)

    private fun envFlag(name: String, default: Boolean = false): Boolean =
        env(name)?.lowercase()?.let { it in setOf("1", "true", "yes", "y") } ?: default

    private fun envInt(name: String): Int? = env(name)?.toIntOrNull()?.takeIf { it > 0 }

    private fun envList(name: String): List<String> =
        env(name).orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }

    private fun envModelOverride(providerId: String): String? =
        env("CODECKS_AI_V2_${providerId.uppercase()}_MODEL")?.takeIf { it.isNotBlank() }

    private fun Int.percentOf(total: Int): Double =
        if (total == 0) 0.0 else Math.round((this.toDouble() * 1000.0) / total) / 10.0

    private data class EvalPrompt(
        val index: Int,
        val kind: DraftKind,
        val prompt: String,
    )

    private data class LiveProviderConfig(
        val providerId: String,
        val label: String,
        val modelId: String,
        val create: () -> AiProvider,
    )

    private data class LiveEvalResult(
        val providerId: String,
        val providerLabel: String,
        val modelId: String,
        val kind: DraftKind,
        val promptIndex: Int,
        val requestCount: Int,
        val schemaParsed: Boolean,
        val firstPassValid: Boolean,
        val validAfterRepair: Boolean,
        val policyFailure: Boolean,
        val failureClass: String,
        val failureDetail: String,
    ) {
        val isTransientProviderFailure: Boolean
            get() = failureClass == "timeout"
    }

    private class CountingProvider(private val delegate: AiProvider) : AiProvider {
        var requestCount: Int = 0
            private set

        override suspend fun listModels(): Result<List<AiModel>> = delegate.listModels()

        override suspend fun test(): Result<Unit> = delegate.test()

        override suspend fun draftAction(request: DraftRequest): Result<ActionDraftJson> {
            requestCount += 1
            return delegate.draftAction(request)
        }
    }

    private inner class EnvSecureApiKeyStore : SecureApiKeyStore {
        override suspend fun hasKey(providerId: String): Boolean = envKeyName(providerId) != null

        override suspend fun saveKey(providerId: String, key: SecretValue) = Unit

        override suspend fun loadKey(providerId: String): SecretValue? =
            envKeyName(providerId)?.let { SecretValue.of(requireNotNull(env(it))) }

        override suspend fun deleteKey(providerId: String) = Unit

        fun envKeyName(providerId: String): String? =
            when (providerId) {
                "openai" -> "OPENAI_API_KEY".takeIf { !env(it).isNullOrBlank() }
                "anthropic" -> "ANTHROPIC_API_KEY".takeIf { !env(it).isNullOrBlank() }
                "gemini" -> listOf("GEMINI_API_KEY", "GOOGLE_API_KEY").firstOrNull { !env(it).isNullOrBlank() }
                "openrouter" -> "OPENROUTER_API_KEY".takeIf { !env(it).isNullOrBlank() }
                "litellm" -> "LITELLM_API_KEY".takeIf { !env(it).isNullOrBlank() }
                else -> null
            }
    }

    private object LiveEvalEntitlementRepository : EntitlementRepository {
        private val localOnly = Entitlement(localOnly = true)
        override val entitlement: Flow<Entitlement> = flowOf(localOnly)
        override suspend fun currentEntitlement(): Entitlement = localOnly
        override suspend fun refresh(): Result<Entitlement> = Result.success(localOnly)
    }

    private companion object {
        const val DEFAULT_LIVE_CONCURRENCY = 4
        const val MAX_TRANSIENT_ATTEMPTS = 2
        const val TRANSIENT_RETRY_DELAY_MS = 1_000L
        const val LIVE_EVAL_AGENT_CONTEXT =
            "Codecks live eval: generate safe typed AI Creator V2 proposals for local deck, automation, and action testing."
    }
}
