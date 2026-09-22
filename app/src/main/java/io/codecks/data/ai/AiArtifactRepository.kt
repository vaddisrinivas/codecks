package io.codecks.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactAction
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.ai.AiArtifactParameter
import io.codecks.domain.ai.AiArtifactPlacementChoice
import io.codecks.domain.ai.AiArtifactPlacementRequest
import io.codecks.domain.ai.AiArtifactReview
import io.codecks.domain.ai.AiArtifactRiskLevel
import io.codecks.domain.ai.AiArtifactStepReview
import io.codecks.domain.ai.AiArtifactTest
import io.codecks.domain.ai.AiArtifactTestStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.codecks.data.persistence.BoundedPayload
import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.valueForMutation

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
            val current = preferences.decodeArtifactsForMutation()
            preferences[AI_ARTIFACTS_V2] = ArtifactStorageCodec.encrypt(AiArtifactJsonCodec.encode(transform(current)))
            preferences.remove(AI_ARTIFACTS)
        }
    }

    private companion object {
        const val MAX_ARTIFACTS = 80
    }
}

private fun androidx.datastore.preferences.core.Preferences.decodeArtifacts(): List<AiArtifact> =
    when (val current = ArtifactStorageCodec.read(this[AI_ARTIFACTS_V2])) {
        is PersistenceRead.Value -> current.value
        PersistenceRead.Missing -> (AiArtifactJsonCodec.readLegacy(this[AI_ARTIFACTS]) as? PersistenceRead.Value)?.value.orEmpty()
        else -> emptyList()
    }

private fun androidx.datastore.preferences.core.MutablePreferences.decodeArtifactsForMutation(): List<AiArtifact> =
    ArtifactStorageCodec.read(this[AI_ARTIFACTS_V2]).valueForMutation("AI artifacts") {
        AiArtifactJsonCodec.readLegacy(this[AI_ARTIFACTS])
            .valueForMutation("Legacy AI artifacts") { emptyList() }
    }

private object ArtifactStorageCodec {
    private const val PROVIDER_ID = "ai_artifacts_v2"
    private const val MAX_PLAINTEXT_BYTES = 1024 * 1024
    private const val MAX_STORED_BYTES = 1536 * 1024

    fun encrypt(raw: String): String = BoundedPayload.utf8(
        EncryptedApiKeyCodec(PROVIDER_ID).encrypt(BoundedPayload.utf8(raw, MAX_PLAINTEXT_BYTES)),
        MAX_STORED_BYTES,
    )

    fun read(value: String?): PersistenceRead<List<AiArtifact>> = BoundedPayload.decodeEncryptedOrLegacy(
        stored = value,
        maxStoredBytes = MAX_STORED_BYTES,
        maxPlaintextBytes = MAX_PLAINTEXT_BYTES,
        isLegacyPlaintext = { it.trimStart().let { raw -> raw.startsWith("{") || raw.startsWith("[") } },
        decrypt = EncryptedApiKeyCodec(PROVIDER_ID)::decrypt,
        decode = AiArtifactJsonCodec::decodeStrict,
    )
}

internal object AiArtifactJsonCodec {
    private const val AI_ARTIFACT_SCHEMA_VERSION = 2

    fun encode(artifacts: List<AiArtifact>): String =
        jsonObject(
            "schemaVersion" to AI_ARTIFACT_SCHEMA_VERSION,
            "items" to artifacts.map(::artifactToMap),
        )

    fun decode(raw: String): List<AiArtifact> =
        runCatching { decodeStrict(raw) }
            .getOrDefault(emptyList())

    fun readLegacy(raw: String?): PersistenceRead<List<AiArtifact>> {
        if (raw == null) return PersistenceRead.Missing
        if (raw.isBlank()) return PersistenceRead.Corrupt("blank")
        if (raw.toByteArray(Charsets.UTF_8).size > 1024 * 1024) return PersistenceRead.Corrupt("oversized")
        return runCatching { PersistenceRead.Value(decodeStrict(raw), migratedFrom = 1) }
            .getOrElse { PersistenceRead.Corrupt("decode") }
    }

    fun decodeStrict(raw: String): List<AiArtifact> {
        val trimmed = raw.trimStart()
        val values = if (trimmed.startsWith("[")) {
            parseJsonArray(trimmed)
        } else {
            val root = parseJsonObject(trimmed)
            require(root.strictInt("schemaVersion") == AI_ARTIFACT_SCHEMA_VERSION) { "Unsupported artifact schema" }
            require(root.has("items"))
            root.strictArrayOrEmpty("items")
        }
        require(values.size <= 80) { "Too many artifacts" }
        return values.map(::parseArtifact)
    }

    private fun artifactToMap(artifact: AiArtifact): Map<String, Any?> =
        buildMap {
            put("id", artifact.id)
            put("kind", artifact.kind.name)
            put("title", artifact.title)
            put("description", artifact.description)
            put("prompt", artifact.prompt)
            put("createdAtMillis", artifact.createdAtMillis)
            put("catalogSavedAtMillis", artifact.catalogSavedAtMillis)
            artifact.lastPlacementRequest?.let { request ->
                put(
                    "lastPlacementRequest",
                    mapOf(
                        "choice" to request.choice.name,
                        "timestampMillis" to request.timestampMillis,
                    ),
                )
            }
            put(
                "review",
                mapOf(
                    "assumptions" to artifact.review.assumptions,
                    "riskLevel" to artifact.review.riskLevel.name,
                    "requiresConfirmation" to artifact.review.requiresConfirmation,
                    "riskReason" to artifact.review.riskReason,
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

    private fun parseArtifact(value: JsonValue): AiArtifact {
            val item = value.asObject()
            val id = requireNotNull(item.optString("id")?.takeIf { it.isNotBlank() })
            return AiArtifact(
                id = id,
                kind = AiArtifactKind.entries.single { it.name == item.string("kind") },
                title = item.strictStringOr("title", "").ifBlank { "AI draft" },
                description = item.strictStringOr("description", ""),
                prompt = item.strictStringOr("prompt", ""),
                createdAtMillis = item.strictLongOr("createdAtMillis", 0L),
                catalogSavedAtMillis = item.strictLongOr(
                    "catalogSavedAtMillis",
                    item.strictLongOr("createdAtMillis", 0L),
                ),
                actions = item.strictArrayOrEmpty("actions").mapIndexed(::parseAction),
                review = item.strictOptionalObject("review")?.let(::parseReview) ?: AiArtifactReview(),
                lastTest = item.strictOptionalObject("lastTest")?.let(::parseTest),
                lastPlacementRequest = item.strictOptionalObject("lastPlacementRequest")?.let(::parsePlacementRequest),
            )
    }

    private fun parseAction(index: Int, value: JsonValue): AiArtifactAction {
            val action = value.asObject()
            val command = action.optString("command").orEmpty()
            require(command.isNotBlank())
            return AiArtifactAction(
                id = action.optString("id").orEmpty().ifBlank { "action_$index" },
                title = action.strictStringOr("title", "").ifBlank { "Action ${index + 1}" },
                command = command,
                dangerous = action.strictBoolOr("dangerous", false),
            )
    }

    private fun parseReview(review: JsonObject): AiArtifactReview =
        AiArtifactReview(
            assumptions = review.strictArrayOrEmpty("assumptions").map { requireNotNull((it as? JsonValue.Str)?.value) },
            riskLevel = AiArtifactRiskLevel.entries.single { it.name == review.string("riskLevel") },
            requiresConfirmation = review.strictBoolOr("requiresConfirmation", false),
            riskReason = review.strictOptionalString("riskReason")?.ifBlank { null },
            target = review.strictStringOr("target", "").ifBlank { "Any connected Mac" },
            trigger = review.strictOptionalString("trigger")?.ifBlank { null },
            requiredCapabilities = review.strictArrayOrEmpty("requiredCapabilities").map { requireNotNull((it as? JsonValue.Str)?.value) },
            parameters = review.strictArrayOrEmpty("parameters").map(::parseReviewParameter),
            steps = review.strictArrayOrEmpty("steps").map(::parseReviewStep),
        )

    private fun parseReviewParameter(value: JsonValue): AiArtifactParameter {
            val item = value.asObject()
            return AiArtifactParameter(
                name = item.optString("name").orEmpty(),
                label = item.optString("label").orEmpty(),
                required = item.strictBoolOr("required", false),
                defaultValue = item.strictOptionalString("defaultValue")?.ifBlank { null },
            )
    }

    private fun parseReviewStep(value: JsonValue): AiArtifactStepReview {
            val item = value.asObject()
            return AiArtifactStepReview(
                id = item.optString("id").orEmpty(),
                label = item.optString("label").orEmpty(),
                type = item.optString("type").orEmpty(),
                summary = item.optString("summary").orEmpty(),
                requiresConfirmation = item.strictBoolOr("requiresConfirmation", false),
            )
    }

    private fun parseTest(test: JsonObject): AiArtifactTest =
        AiArtifactTest(
            status = AiArtifactTestStatus.entries.single { it.name == test.string("status") },
            message = test.optString("message").orEmpty(),
            timestampMillis = test.strictLongOr("timestampMillis", 0L),
        )

    private fun parsePlacementRequest(request: JsonObject): AiArtifactPlacementRequest =
        AiArtifactPlacementRequest(
            choice = AiArtifactPlacementChoice.entries.single {
                it.name == request.optString("choice")
            },
            timestampMillis = request.strictLongOr("timestampMillis", 0L),
        )
}
