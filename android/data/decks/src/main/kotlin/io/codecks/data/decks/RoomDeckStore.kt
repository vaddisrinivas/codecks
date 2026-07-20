package io.codecks.data.decks

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import io.codecks.core.common.CodecksClock
import io.codecks.core.common.SystemCodecksClock
import io.codecks.domain.decks.Deck
import io.codecks.domain.decks.DeckImportResult
import io.codecks.domain.decks.DeckRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val deckJson: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "deck_quarantine")
data class QuarantinedDeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawJson: String,
    val reason: String,
    val createdAtEpochMillis: Long,
)

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE")
    fun observeDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id LIMIT 1")
    suspend fun getDeck(id: String): DeckEntity?

    @Upsert
    suspend fun upsert(deck: DeckEntity)

    @Insert
    suspend fun insertQuarantine(deck: QuarantinedDeckEntity)

    @Query("SELECT * FROM deck_quarantine ORDER BY createdAtEpochMillis DESC, id DESC")
    fun observeQuarantine(): Flow<List<QuarantinedDeckEntity>>
}

@Database(
    entities = [
        DeckEntity::class,
        QuarantinedDeckEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DeckDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao

    companion object {
        fun create(
            context: Context,
            databaseName: String = "codecks-decks.db",
        ): DeckDatabase = Room.databaseBuilder(
            context.applicationContext,
            DeckDatabase::class.java,
            databaseName,
        ).build()
    }
}

class RoomDeckRepository(
    private val dao: DeckDao,
    private val codec: DeckJsonCodec = DeckJsonCodec(),
    private val clock: CodecksClock = SystemCodecksClock,
) : DeckRepository {
    override fun observeDecks(): Flow<List<Deck>> = dao.observeDecks().map { entities ->
        entities.mapNotNull { entity ->
            when (val result = entity.toDeck(codec)) {
                is DeckImportResult.Imported -> result.deck
                is DeckImportResult.Quarantined -> null
            }
        }.sortedBy { deck -> deck.name.lowercase() }
    }

    override suspend fun getDeck(id: String): Deck? = when (val result = dao.getDeck(id)?.toDeck(codec)) {
        is DeckImportResult.Imported -> result.deck
        is DeckImportResult.Quarantined,
        null,
        -> null
    }

    override suspend fun upsertDeck(deck: Deck) {
        dao.upsert(deck.toEntity(codec, clock))
    }

    override suspend fun quarantineDeck(rawJson: String, reason: String) {
        dao.insertQuarantine(rawJson.toQuarantineEntity(reason, clock))
    }

    suspend fun importDeck(rawJson: String): DeckImportResult {
        val result = codec.import(rawJson)
        when (result) {
            is DeckImportResult.Imported -> upsertDeck(result.deck)
            is DeckImportResult.Quarantined -> quarantineDeck(rawJson, result.reason)
        }
        return result
    }

    fun observeQuarantine(): Flow<List<QuarantinedDeck>> = dao.observeQuarantine().map { entities ->
        entities.map { entity ->
            QuarantinedDeck(
                rawJson = entity.rawJson,
                reason = entity.reason,
            )
        }
    }
}

internal fun Deck.toEntity(
    codec: DeckJsonCodec,
    clock: CodecksClock,
): DeckEntity = DeckEntity(
    id = id,
    name = name,
    deckJson = codec.export(this),
    updatedAtEpochMillis = clock.nowEpochMillis(),
)

internal fun DeckEntity.toDeck(codec: DeckJsonCodec): DeckImportResult = codec.import(deckJson)

internal fun String.toQuarantineEntity(
    reason: String,
    clock: CodecksClock,
): QuarantinedDeckEntity = QuarantinedDeckEntity(
    rawJson = this,
    reason = reason,
    createdAtEpochMillis = clock.nowEpochMillis(),
)
