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
        preferences[AI_GENERATION_HISTORY_V2]?.let(GenerationHistoryStorageCodec::decryptOrDecode) ?: emptyList()
    }

    override suspend fun save(record: AiGenerationRecord) {
        context.aiGenerationHistoryDataStore.edit { preferences ->
            val current = preferences[AI_GENERATION_HISTORY_V2]?.let(GenerationHistoryStorageCodec::decryptOrDecode).orEmpty()
            preferences[AI_GENERATION_HISTORY_V2] = GenerationHistoryStorageCodec.encrypt(
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

    fun encrypt(raw: String): String = EncryptedApiKeyCodec(PROVIDER_ID).encrypt(raw)

    fun decryptOrDecode(value: String): List<AiGenerationRecord> {
        val raw = runCatching { EncryptedApiKeyCodec(PROVIDER_ID).decrypt(value) }.getOrDefault(value)
        return AiGenerationHistoryJsonCodec.decode(raw)
    }
}

internal object AiGenerationHistoryJsonCodec {
    private const val SCHEMA_VERSION = 2

    fun encode(records: List<AiGenerationRecord>): String =
        jsonObject(
            "schemaVersion" to SCHEMA_VERSION,
            "items" to records.map(::recordToMap),
        )

    fun decode(raw: String): List<AiGenerationRecord> =
        runCatching { parseJsonObject(raw).array("items") }
            .getOrDefault(emptyList())
            .mapNotNull(::parseRecord)

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

    private fun parseRecord(value: JsonValue): AiGenerationRecord? =
        runCatching {
            val item = value.asObject()
            val id = item.optString("id")?.takeIf(String::isNotBlank) ?: return@runCatching null
            AiGenerationRecord(
                id = id,
                providerId = item.optString("providerId").orEmpty(),
                providerLabel = item.optString("providerLabel").orEmpty(),
                modelId = item.optString("modelId").orEmpty(),
                modelLabel = item.optString("modelLabel").orEmpty(),
                draftKind = item.optString("draftKind").orEmpty().toDraftKind(),
                status = item.optString("status").orEmpty().toGenerationStatus(),
                message = item.optString("message").orEmpty(),
                validationErrors = item.array("validationErrors").mapNotNull { (it as? JsonValue.Str)?.value },
                artifactId = item.optString("artifactId")?.ifBlank { null },
                createdAtMillis = item.long("createdAtMillis", System.currentTimeMillis()),
            )
        }.getOrNull()
}

private fun String.toDraftKind(): DraftKind =
    DraftKind.entries.firstOrNull { it.name == this } ?: DraftKind.Action

private fun String.toGenerationStatus(): AiGenerationStatus =
    AiGenerationStatus.entries.firstOrNull { it.name == this } ?: AiGenerationStatus.Failed
