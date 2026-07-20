package io.codecks.data.receipts

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import io.codecks.domain.actions.ActionReceipt
import io.codecks.domain.actions.ActionReceiptRepository
import io.codecks.domain.actions.ReceiptState
import io.codecks.domain.actions.RepairHint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "action_receipts")
data class ActionReceiptEntity(
    @PrimaryKey val invocationId: String,
    val state: String,
    val targetId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val safeSummary: String,
    val repairTitle: String?,
    val repairActionLabel: String?,
)

@Dao
interface ActionReceiptDao {
    @Query("SELECT * FROM action_receipts ORDER BY startedAtEpochMillis DESC")
    fun observeReceipts(): Flow<List<ActionReceiptEntity>>

    @Upsert
    suspend fun upsert(receipt: ActionReceiptEntity)

    @Query(
        """
        DELETE FROM action_receipts
        WHERE invocationId NOT IN (
            SELECT invocationId
            FROM action_receipts
            ORDER BY startedAtEpochMillis DESC
            LIMIT :maxReceipts
        )
        """,
    )
    suspend fun trimTo(maxReceipts: Int)

    @Transaction
    suspend fun upsertBounded(receipt: ActionReceiptEntity, maxReceipts: Int) {
        upsert(receipt)
        trimTo(maxReceipts)
    }

    @Query("DELETE FROM action_receipts")
    suspend fun clear()
}

@Database(
    entities = [
        ActionReceiptEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ReceiptDatabase : RoomDatabase() {
    abstract fun receiptDao(): ActionReceiptDao

    companion object {
        fun create(
            context: Context,
            databaseName: String = "codecks-receipts.db",
        ): ReceiptDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReceiptDatabase::class.java,
            databaseName,
        ).build()
    }
}

class RoomActionReceiptRepository(
    private val dao: ActionReceiptDao,
    private val maxReceipts: Int = 64,
) : ActionReceiptRepository {
    init {
        require(maxReceipts in 1..1_000) { "Receipt bound must be 1..1000" }
    }

    override fun observeReceipts(): Flow<List<ActionReceipt>> = dao.observeReceipts().map { entities ->
        entities.mapNotNull { entity -> entity.toReceiptOrNull() }
    }

    override suspend fun append(receipt: ActionReceipt) {
        dao.upsertBounded(receipt.toEntity(), maxReceipts)
    }

    override suspend fun clear() {
        dao.clear()
    }
}

internal fun ActionReceipt.toEntity(): ActionReceiptEntity = ActionReceiptEntity(
    invocationId = invocationId,
    state = state.name,
    targetId = targetId,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    safeSummary = safeSummary,
    repairTitle = repair?.title,
    repairActionLabel = repair?.actionLabel,
)

internal fun ActionReceiptEntity.toReceiptOrNull(): ActionReceipt? {
    val parsedState = runCatching { ReceiptState.valueOf(state) }.getOrNull() ?: return null
    return ActionReceipt(
        invocationId = invocationId,
        state = parsedState,
        targetId = targetId,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        safeSummary = safeSummary,
        repair = repairTitle?.let { title ->
            RepairHint(
                title = title,
                actionLabel = repairActionLabel,
            )
        },
    )
}
