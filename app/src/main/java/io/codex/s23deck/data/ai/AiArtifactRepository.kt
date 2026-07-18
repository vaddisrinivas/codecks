package io.codex.s23deck.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codex.s23deck.domain.ai.AiArtifact
import io.codex.s23deck.domain.ai.AiArtifactAction
import io.codex.s23deck.domain.ai.AiArtifactKind
import io.codex.s23deck.domain.ai.AiArtifactParameter
import io.codex.s23deck.domain.ai.AiArtifactReview
import io.codex.s23deck.domain.ai.AiArtifactRiskLevel
import io.codex.s23deck.domain.ai.AiArtifactStepReview
import io.codex.s23deck.domain.ai.AiArtifactTest
import io.codex.s23deck.domain.ai.AiArtifactTestStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aiArtifactsDataStore by preferencesDataStore(name = "ai_artifacts")
private val AI_ARTIFACTS = stringPreferencesKey("artifacts")
private val AI_ARTIFACTS_V2 = stringPreferencesKey("artifacts_v2")

interface AiArtifactRepository {
    val artifacts: Flow<List<AiArtifact>>
    suspend fun save(artifact: AiArtifact)
    suspend fun recordTest(artifactId: String, test: AiArtifactTest)
    suspend fun delete(artifactId: String)
    suspend fun clear()
}

@Singleton
class DefaultAiArtifactRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AiArtifactRepository {
    override val artifacts: Flow<List<AiArtifact>> = context.aiArtifactsDataStore.data.map { preferences ->
        preferences.decodeArtifacts()
    }

    override suspend fun save(artifact: AiArtifact) {
        mutate { artifacts ->
            val existing = artifacts.indexOfFirst { it.id == artifact.id }
            (if (existing >= 0) artifacts.toMutableList().also { it[existing] = artifact } else listOf(artifact) + artifacts)
                .take(MAX_ARTIFACTS)
        }
    }

    override suspend fun recordTest(artifactId: String, test: AiArtifactTest) {
        mutate { artifacts ->
            artifacts.map { artifact ->
                if (artifact.id == artifactId) artifact.copy(lastTest = test) else artifact
            }
        }
    }

    override suspend fun delete(artifactId: String) {
        mutate { artifacts -> artifacts.filterNot { it.id == artifactId } }
    }

    override suspend fun clear() {
        context.aiArtifactsDataStore.edit {
            it.remove(AI_ARTIFACTS)
            it.remove(AI_ARTIFACTS_V2)
        }
    }

    private suspend fun mutate(transform: (List<AiArtifact>) -> List<AiArtifact>) {
        context.aiArtifactsDataStore.edit { preferences ->
            val current = preferences.decodeArtifacts()
            preferences[AI_ARTIFACTS_V2] = ArtifactStorageCodec.encrypt(AiArtifactJsonCodec.encode(transform(current)))
            preferences.remove(AI_ARTIFACTS)
        }
    }

    private companion object {
        const val MAX_ARTIFACTS = 80
    }
}

private fun androidx.datastore.preferences.core.Preferences.decodeArtifacts(): List<AiArtifact> =
    this[AI_ARTIFACTS_V2]
        ?.let(ArtifactStorageCodec::decryptOrDecode)
        ?: this[AI_ARTIFACTS]?.let(AiArtifactJsonCodec::decode)
        ?: emptyList()

private object ArtifactStorageCodec {
    private const val PROVIDER_ID = "ai_artifacts_v2"

    fun encrypt(raw: String): String = EncryptedApiKeyCodec(PROVIDER_ID).encrypt(raw)

    fun decryptOrDecode(value: String): List<AiArtifact> {
        val raw = runCatching { EncryptedApiKeyCodec(PROVIDER_ID).decrypt(value) }.getOrDefault(value)
        return AiArtifactJsonCodec.decode(raw)
    }
}

internal object AiArtifactJsonCodec {
    private const val AI_ARTIFACT_SCHEMA_VERSION = 2

    fun encode(artifacts: List<AiArtifact>): String =
        jsonObject(
            "schemaVersion" to AI_ARTIFACT_SCHEMA_VERSION,
            "items" to artifacts.map(::artifactToMap),
        )

    fun decode(raw: String): List<AiArtifact> =
        runCatching { parseArtifactArray(raw) }
            .getOrDefault(emptyList())
            .mapNotNull(::parseArtifact)

    private fun artifactToMap(artifact: AiArtifact): Map<String, Any?> =
        buildMap {
            put("id", artifact.id)
            put("kind", artifact.kind.name)
            put("title", artifact.title)
            put("description", artifact.description)
            put("prompt", artifact.prompt)
            put("createdAtMillis", artifact.createdAtMillis)
            put(
                "review",
                mapOf(
                    "assumptions" to artifact.review.assumptions,
                    "riskLevel" to artifact.review.riskLevel.name,
                    "requiresConfirmation" to artifact.review.requiresConfirmation,
                    "target" to artifact.review.target,
                    "trigger" to artifact.review.trigger,
                    "requiredCapabilities" to artifact.review.requiredCapabilities,
                    "parameters" to artifact.review.parameters.map { parameter ->
                        mapOf(
                            "name" to parameter.name,
                            "label" to parameter.label,
                            "required" to parameter.required,
                            "defaultValue" to parameter.defaultValue,
                        )
                    },
                    "steps" to artifact.review.steps.map { step ->
                        mapOf(
                            "id" to step.id,
                            "label" to step.label,
                            "type" to step.type,
                            "summary" to step.summary,
                            "requiresConfirmation" to step.requiresConfirmation,
                        )
                    },
                ),
            )
            put(
                "actions",
                artifact.actions.map { action ->
                    mapOf(
                        "id" to action.id,
                        "title" to action.title,
                        "command" to action.command,
                        "dangerous" to action.dangerous,
                    )
                },
            )
            artifact.lastTest?.let { test ->
                put(
                    "lastTest",
                    mapOf(
                        "status" to test.status.name,
                        "message" to test.message,
                        "timestampMillis" to test.timestampMillis,
                    ),
                )
            }
        }

    private fun parseArtifactArray(raw: String): List<JsonValue> {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("[")) {
            parseJsonArray(trimmed)
        } else {
            parseJsonObject(trimmed).array("items")
        }
    }

    private fun parseArtifact(value: JsonValue): AiArtifact? =
        runCatching {
            val item = value.asObject()
            val id = item.optString("id")?.takeIf { it.isNotBlank() } ?: return@runCatching null
            AiArtifact(
                id = id,
                kind = item.optString("kind").orEmpty().toArtifactKind(),
                title = item.optString("title").orEmpty().ifBlank { "AI artifact" },
                description = item.optString("description").orEmpty(),
                prompt = item.optString("prompt").orEmpty(),
                createdAtMillis = item.long("createdAtMillis", System.currentTimeMillis()),
                actions = item.array("actions").mapIndexedNotNull(::parseAction),
                review = item.optObj("review")?.let(::parseReview) ?: AiArtifactReview(),
                lastTest = item.optObj("lastTest")?.let(::parseTest),
            )
        }.getOrNull()

    private fun parseAction(index: Int, value: JsonValue): AiArtifactAction? =
        runCatching {
            val action = value.asObject()
            val command = action.optString("command").orEmpty()
            if (command.isBlank()) return@runCatching null
            AiArtifactAction(
                id = action.optString("id").orEmpty().ifBlank { "action_$index" },
                title = action.optString("title").orEmpty().ifBlank { "Action ${index + 1}" },
                command = command,
                dangerous = action.bool("dangerous", false),
            )
        }.getOrNull()

    private fun parseReview(review: JsonObject): AiArtifactReview =
        AiArtifactReview(
            assumptions = review.array("assumptions").mapNotNull { (it as? JsonValue.Str)?.value },
            riskLevel = review.optString("riskLevel").orEmpty().toRiskLevel(),
            requiresConfirmation = review.bool("requiresConfirmation", false),
            target = review.optString("target").orEmpty().ifBlank { "Any connected Mac" },
            trigger = review.optString("trigger")?.ifBlank { null },
            requiredCapabilities = review.array("requiredCapabilities").mapNotNull { (it as? JsonValue.Str)?.value },
            parameters = review.array("parameters").mapNotNull(::parseReviewParameter),
            steps = review.array("steps").mapNotNull(::parseReviewStep),
        )

    private fun parseReviewParameter(value: JsonValue): AiArtifactParameter? =
        runCatching {
            val item = value.asObject()
            AiArtifactParameter(
                name = item.optString("name").orEmpty(),
                label = item.optString("label").orEmpty(),
                required = item.bool("required", false),
                defaultValue = item.optString("defaultValue")?.ifBlank { null },
            )
        }.getOrNull()

    private fun parseReviewStep(value: JsonValue): AiArtifactStepReview? =
        runCatching {
            val item = value.asObject()
            AiArtifactStepReview(
                id = item.optString("id").orEmpty(),
                label = item.optString("label").orEmpty(),
                type = item.optString("type").orEmpty(),
                summary = item.optString("summary").orEmpty(),
                requiresConfirmation = item.bool("requiresConfirmation", false),
            )
        }.getOrNull()

    private fun parseTest(test: JsonObject): AiArtifactTest =
        AiArtifactTest(
            status = test.optString("status").orEmpty().toTestStatus(),
            message = test.optString("message").orEmpty(),
            timestampMillis = test.long("timestampMillis", System.currentTimeMillis()),
        )
}

private fun String.toArtifactKind(): AiArtifactKind =
    AiArtifactKind.entries.firstOrNull { it.name == this } ?: AiArtifactKind.Button

private fun String.toTestStatus(): AiArtifactTestStatus =
    AiArtifactTestStatus.entries.firstOrNull { it.name == this } ?: AiArtifactTestStatus.Failed

private fun String.toRiskLevel(): AiArtifactRiskLevel =
    AiArtifactRiskLevel.entries.firstOrNull { it.name == this } ?: AiArtifactRiskLevel.Normal
