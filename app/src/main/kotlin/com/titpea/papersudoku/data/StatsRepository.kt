package com.titpea.papersudoku.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

data class GameResult(
    val mode: String,
    val durationSeconds: Long,
    val hints: Int,
    val completedAt: Long,
)

interface StatsRepository {
    val completedCount: Flow<Int>
    val bestSeconds: Flow<Long?>
    suspend fun record(result: GameResult)
}

@Entity(tableName = "game_results")
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val durationSeconds: Long,
    val hints: Int,
    val completedAt: Long,
)

@Dao
interface GameResultDao {
    @Insert
    suspend fun insert(result: GameResultEntity)

    @Query("SELECT COUNT(*) FROM game_results")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(durationSeconds) FROM game_results")
    fun observeBestSeconds(): Flow<Long?>
}

@Database(
    entities = [GameResultEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SudokuDatabase : RoomDatabase() {
    abstract fun gameResultDao(): GameResultDao

    companion object {
        fun create(context: Context): SudokuDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SudokuDatabase::class.java,
                "sudoku.db",
            ).build()
    }
}

class RoomStatsRepository(private val dao: GameResultDao) : StatsRepository {
    override val completedCount: Flow<Int> = dao.observeCount()
    override val bestSeconds: Flow<Long?> = dao.observeBestSeconds()

    override suspend fun record(result: GameResult) {
        dao.insert(
            GameResultEntity(
                mode = result.mode,
                durationSeconds = result.durationSeconds,
                hints = result.hints,
                completedAt = result.completedAt,
            ),
        )
    }
}
