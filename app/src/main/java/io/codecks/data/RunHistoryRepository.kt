package io.codecks.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.domain.privacy.DiagnosticRedactor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.runHistoryDataStore by preferencesDataStore(name = "run_history")

interface RunHistoryRepository {
    val results: Flow<List<ActionResult>>
    suspend fun record(result: ActionResult)
    suspend fun clear()
}

@Singleton
class DefaultRunHistoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RunHistoryRepository {
    override val results: Flow<List<ActionResult>> = context.runHistoryDataStore.data.map { preferences ->
        decodeResults(preferences[RESULTS].orEmpty())
    }

    override suspend fun record(result: ActionResult) {
        val redacted = result.redactedForStorage()
        context.runHistoryDataStore.edit { preferences ->
            val next = (listOf(redacted) + decodeResults(preferences[RESULTS].orEmpty()))
                .distinctBy { "${it.actionId}:${it.timestampMillis}" }
                .take(MAX_RESULTS)
            preferences[RESULTS] = encodeResults(next)
        }
    }

    override suspend fun clear() {
        context.runHistoryDataStore.edit { it.remove(RESULTS) }
    }

    private companion object {
        val RESULTS = stringPreferencesKey("results_v1")
    }
}

class InMemoryRunHistoryRepository(
    initialResults: List<ActionResult> = emptyList(),
) : RunHistoryRepository {
    private val state = MutableStateFlow(initialResults)
    override val results: Flow<List<ActionResult>> = state

    override suspend fun record(result: ActionResult) {
        state.value = (listOf(result.redactedForStorage()) + state.value)
            .distinctBy { "${it.actionId}:${it.timestampMillis}" }
            .take(MAX_RESULTS)
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

private const val MAX_RESULTS = 50
private const val MAX_STORED_LOG_LENGTH = 1_200

private fun ActionResult.redactedForStorage(): ActionResult =
    copy(
        message = DiagnosticRedactor.redact(message, maxLength = 240),
        logs = DiagnosticRedactor.redact(logs, maxLength = MAX_STORED_LOG_LENGTH),
        target = target?.let { DiagnosticRedactor.redact(it, maxLength = 80) },
    )

private fun encodeResults(results: List<ActionResult>): String {
    val array = JSONArray()
    results.forEach { result ->
        array.put(
            JSONObject()
                .put("actionId", result.actionId)
                .put("title", result.title)
                .put("status", result.status.name)
                .put("message", result.message)
                .put("logs", result.logs)
                .put("target", result.target)
                .put("timestampMillis", result.timestampMillis),
        )
    }
    return array.toString()
}

private fun decodeResults(raw: String): List<ActionResult> =
    runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ActionResult(
                actionId = item.optString("actionId"),
                title = item.optString("title"),
                status = runCatching {
                    ActionResultStatus.valueOf(item.optString("status"))
                }.getOrDefault(ActionResultStatus.Failed),
                message = item.optString("message"),
                logs = item.optString("logs", item.optString("message")),
                target = item.optString("target").takeIf(String::isNotBlank),
                timestampMillis = item.optLong("timestampMillis"),
            ).takeIf { it.actionId.isNotBlank() && it.title.isNotBlank() }
        }
    }.getOrDefault(emptyList())
