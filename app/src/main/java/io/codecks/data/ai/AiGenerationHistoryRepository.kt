package io.codecks.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.domain.ai.AiGenerationRecord
import io.codecks.domain.ai.AiGenerationStatus
import io.codecks.domain.ai.DraftKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.codecks.data.persistence.BoundedPayload
import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.valueForMutation

private val Context.aiGenerationHistoryDataStore by preferencesDataStore(name = "ai_generation_history")
private val AI_GENERATION_HISTORY_V2 = stringPreferencesKey("history_v2")

interface AiGenerationHistoryRepository {
    val records: Flow<List<AiGenerationRecord>>
    suspend fun save(record: AiGenerationRecord)
    suspend fun clear()
}

@Singleton
class DefaultAiGenerationHistoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AiGenerationHistoryRepository {
    override val records: Flow<List<AiGenerationRecord>> = context.aiGenerationHistoryDataStore.data.map { preferences ->
        GenerationHistoryStorageCodec.read(preferences[AI_GENERATION_HISTORY_V2]).valueOrEmpty()
    }

    override suspend fun save(record: AiGenerationRecord) {
        context.aiGenerationHistoryDataStore.edit { preferences ->
            val current = GenerationHistoryStorageCodec.read(preferences[AI_GENERATION_HISTORY_V2])
                .valueForMutation("AI history") { emptyList() }
            preferences[AI_GENERATION_HISTORY_V2] = GenerationHistoryStorageCodec.encryptBounded(
                AiGenerationHistoryJsonCodec.encode((listOf(record) + current.filterNot { it.id == record.id }).take(MAX_RECORDS)),
            )
        }
    }

    override suspend fun clear() {
        context.aiGenerationHistoryDataStore.edit { it.remove(AI_GENERATION_HISTORY_V2) }
    }

    private companion object {
        const val MAX_RECORDS = 120
    }
}

private object GenerationHistoryStorageCodec {
    private const val PROVIDER_ID = "ai_generation_history_v2"

    private const val MAX_PLAINTEXT_BYTES = 512 * 1024
    private const val MAX_STORED_BYTES = 768 * 1024

    fun encryptBounded(raw: String): String = BoundedPayload.utf8(
        EncryptedApiKeyCodec(PROVIDER_ID).encrypt(BoundedPayload.utf8(raw, MAX_PLAINTEXT_BYTES)),
        MAX_STORED_BYTES,
    )

    fun read(value: String?): PersistenceRead<List<AiGenerationRecord>> = BoundedPayload.decodeEncryptedOrLegacy(
        stored = value,
        maxStoredBytes = MAX_STORED_BYTES,
        maxPlaintextBytes = MAX_PLAINTEXT_BYTES,
        isLegacyPlaintext = { it.trimStart().startsWith("{") },
        decrypt = EncryptedApiKeyCodec(PROVIDER_ID)::decrypt,
        decode = AiGenerationHistoryJsonCodec::decodeStrict,
    )
}

private fun PersistenceRead<List<AiGenerationRecord>>.valueOrEmpty(): List<AiGenerationRecord> =
    (this as? PersistenceRead.Value)?.value.orEmpty()

internal object AiGenerationHistoryJsonCodec {
    private const val SCHEMA_VERSION = 2

    fun encode(records: List<AiGenerationRecord>): String =
        jsonObject(
            "schemaVersion" to SCHEMA_VERSION,
            "items" to records.map(::recordToMap),
        )

    fun decode(raw: String): List<AiGenerationRecord> =
        runCatching { decodeStrict(raw) }
            .getOrDefault(emptyList())

    fun decodeStrict(raw: String): List<AiGenerationRecord> {
        val root = parseJsonObject(raw)
        require(root.strictInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported history schema" }
        require(root.has("items")) { "Missing history items" }
        val items = root.array("items")
        require(items.size <= 120) { "Too many history items" }
        return items.map(::parseRecord)
    }

    private fun recordToMap(record: AiGenerationRecord): Map<String, Any?> =
        mapOf(
            "id" to record.id,
            "providerId" to record.providerId,
            "providerLabel" to record.providerLabel,
            "modelId" to record.modelId,
            "modelLabel" to record.modelLabel,
            "draftKind" to record.draftKind.name,
            "status" to record.status.name,
            "message" to record.message,
            "validationErrors" to record.validationErrors,
            "artifactId" to record.artifactId,
            "createdAtMillis" to record.createdAtMillis,
        )

    private fun parseRecord(value: JsonValue): AiGenerationRecord {
        val item = value.asObject()
        val id = requireNotNull(item.optString("id")?.takeIf(String::isNotBlank))
        val validationErrors = item.strictArrayOrEmpty("validationErrors")
            .map { requireNotNull((it as? JsonValue.Str)?.value) }
        return AiGenerationRecord(
                id = id,
                providerId = item.strictStringOr("providerId", ""),
                providerLabel = item.strictStringOr("providerLabel", ""),
                modelId = item.strictStringOr("modelId", ""),
                modelLabel = item.strictStringOr("modelLabel", ""),
                draftKind = DraftKind.entries.single { it.name == item.string("draftKind") },
                status = AiGenerationStatus.entries.single { it.name == item.string("status") },
                message = item.strictStringOr("message", ""),
                validationErrors = validationErrors,
                artifactId = item.strictOptionalString("artifactId")?.ifBlank { null },
                createdAtMillis = item.strictLongOr("createdAtMillis", -1L).also { require(it >= 0L) },
            )
    }
}
