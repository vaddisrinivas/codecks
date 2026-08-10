package io.codecks.spikes

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "automation_run")
data class AutomationRunRow(
    @PrimaryKey val id: String,
    val automationId: String,
    val outcome: String,
    val finishedAtEpochMillis: Long,
    val schemaWriterVersion: Int = 2,
)

@Dao
interface AutomationRunDao {
    @Insert
    suspend fun insert(row: AutomationRunRow)

    @Query("SELECT * FROM automation_run ORDER BY finishedAtEpochMillis DESC")
    suspend fun allNewestFirst(): List<AutomationRunRow>

    @Query("SELECT COUNT(*) FROM automation_run WHERE automationId = :automationId")
    suspend fun countForAutomation(automationId: String): Int

    @Transaction
    suspend fun replaceAtomically(row: AutomationRunRow) {
        delete(row.id)
        insert(row)
    }

    @Query("DELETE FROM automation_run WHERE id = :id")
    suspend fun delete(id: String)
}
@Database(entities = [AutomationRunRow::class], version = 2, exportSchema = true)
abstract class AutomationHistoryDatabase : RoomDatabase() {
    abstract fun automationRunDao(): AutomationRunDao
}

object AutomationHistoryPolicy {
    const val CURRENT_WRITER = 2
    const val OLDEST_READABLE = 1
    val forbiddenColumns = setOf("password", "privateKey", "token", "secret")

    fun acceptsReader(readerVersion: Int): Boolean = readerVersion in OLDEST_READABLE..CURRENT_WRITER
    fun acceptsWriter(writerVersion: Int): Boolean = writerVersion <= CURRENT_WRITER
}
