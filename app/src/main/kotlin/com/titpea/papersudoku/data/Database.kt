package com.titpea.papersudoku.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One finished puzzle. v1 rows have an empty [puzzleKey]/[difficulty] and no [epochDay].
 */
@Entity(
    tableName = "game_results",
    indices = [Index("puzzleKey")],
)
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "mini" or "classic" (kept from v1). */
    val mode: String,
    val durationSeconds: Long,
    /** Number of times the Coach was opened. */
    val hints: Int,
    val completedAt: Long,
    @ColumnInfo(defaultValue = "''") val puzzleKey: String = "",
    @ColumnInfo(defaultValue = "''") val difficulty: String = "",
    /** Deepest Coach level used: 0 none, 1 look, 2 why, 3 answer. */
    @ColumnInfo(defaultValue = "0") val coachLevel: Int = 0,
    val epochDay: Long? = null,
)

/** The in-progress (or finished) board for one puzzle, as [com.titpea.papersudoku.game.model.GameState] JSON. */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val puzzleKey: String,
    val kind: String,
    val difficulty: String,
    val number: Int?,
    val epochDay: Long?,
    val solved: Boolean,
    val json: String,
    val updatedAt: Long,
)

/** Per mode and difficulty. [bestSeconds] ignores games where the Coach gave the answer. */
data class StatsRow(
    val mode: String,
    val difficulty: String,
    val solved: Int,
    val bestSeconds: Long?,
)

data class DayResult(
    val epochDay: Long,
    val coachLevel: Int,
    val durationSeconds: Long,
)

@Dao
interface GameResultDao {
    @Insert
    suspend fun insert(result: GameResultEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM game_results WHERE puzzleKey = :puzzleKey)")
    suspend fun exists(puzzleKey: String): Boolean

    @Query(
        """
        SELECT mode, difficulty, COUNT(*) AS solved,
               MIN(CASE WHEN coachLevel < 3 THEN durationSeconds END) AS bestSeconds
        FROM game_results GROUP BY mode, difficulty ORDER BY mode, difficulty
        """,
    )
    fun observeStats(): Flow<List<StatsRow>>

    @Query(
        """
        SELECT epochDay, MIN(coachLevel) AS coachLevel, MIN(durationSeconds) AS durationSeconds
        FROM game_results WHERE epochDay IS NOT NULL GROUP BY epochDay
        """,
    )
    fun observeDays(): Flow<List<DayResult>>

    @Query("SELECT COUNT(*) FROM game_results")
    suspend fun count(): Int
}

@Dao
interface SavedGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: SavedGameEntity)

    @Query("SELECT * FROM saved_games WHERE puzzleKey = :puzzleKey")
    suspend fun get(puzzleKey: String): SavedGameEntity?

    @Query(
        """
        SELECT * FROM saved_games WHERE kind = 'Classic' AND difficulty = :difficulty AND solved = 0
        ORDER BY updatedAt DESC LIMIT 1
        """,
    )
    suspend fun latestUnsolvedClassic(difficulty: String): SavedGameEntity?

    @Query("SELECT MAX(number) FROM saved_games WHERE kind = 'Classic' AND difficulty = :difficulty")
    suspend fun maxClassicNumber(difficulty: String): Int?

    @Query("DELETE FROM saved_games WHERE kind = 'Daily' AND solved = 0 AND epochDay < :beforeEpochDay")
    suspend fun pruneUnsolvedDailies(beforeEpochDay: Long)
}

@Database(
    entities = [GameResultEntity::class, SavedGameEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SudokuDatabase : RoomDatabase() {
    abstract fun resultDao(): GameResultDao
    abstract fun savedGameDao(): SavedGameDao

    companion object {
        const val NAME = "sudoku.db"

        fun create(context: Context, name: String = NAME): SudokuDatabase =
            Room.databaseBuilder(context.applicationContext, SudokuDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** v1 → v2: richer results (one per puzzle) and saved games. v1 results are kept. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `game_results` ADD COLUMN `puzzleKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `game_results` ADD COLUMN `difficulty` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `game_results` ADD COLUMN `coachLevel` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `game_results` ADD COLUMN `epochDay` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_results_puzzleKey` ON `game_results` (`puzzleKey`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_games` (
                `puzzleKey` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `difficulty` TEXT NOT NULL,
                `number` INTEGER,
                `epochDay` INTEGER,
                `solved` INTEGER NOT NULL,
                `json` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`puzzleKey`)
            )
            """.trimIndent(),
        )
    }
}
