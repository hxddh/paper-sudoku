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
import com.titpea.papersudoku.game.model.GameState
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

/**
 * The in-progress (or finished) board for one puzzle, as [com.titpea.papersudoku.game.model.GameState]
 * JSON. [givens] and [grid] repeat the printed and current digits (one character per cell) so the
 * notebook can draw its pages without decoding every game.
 */
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
    @ColumnInfo(defaultValue = "''") val givens: String = "",
    @ColumnInfo(defaultValue = "''") val grid: String = "",
)

/** What the notebook and the widget need of a saved page. */
data class SavedPageRow(
    val puzzleKey: String,
    val kind: String,
    val difficulty: String,
    val number: Int?,
    val epochDay: Long?,
    val solved: Boolean,
    val givens: String,
    val grid: String,
)

/** Per mode and difficulty. [bestSeconds] ignores games where the Coach gave the answer. */
data class StatsRow(
    val mode: String,
    val difficulty: String,
    val solved: Int,
    val bestSeconds: Long?,
)

data class DifficultyCount(
    val difficulty: String,
    val solved: Int,
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

    @Query("SELECT COUNT(*) FROM game_results")
    fun observeCount(): Flow<Int>

    /** When each practice (9×9) page was finished, for the contents' month counts. */
    @Query("SELECT completedAt FROM game_results WHERE mode = 'classic'")
    fun observePracticeCompletions(): Flow<List<Long>>

    /** Practice (9×9) solves without the Coach's answer, per difficulty. */
    @Query(
        """
        SELECT difficulty, COUNT(*) AS solved FROM game_results
        WHERE mode = 'classic' AND coachLevel < 3 AND difficulty != '' GROUP BY difficulty
        """,
    )
    fun observeCleanPractice(): Flow<List<DifficultyCount>>
}

@Dao
interface SavedGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: SavedGameEntity)

    @Query("SELECT * FROM saved_games WHERE puzzleKey = :puzzleKey")
    suspend fun get(puzzleKey: String): SavedGameEntity?

    @Query("SELECT $PAGE_COLUMNS FROM saved_games WHERE kind = 'Classic' AND solved = 0 ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestUnsolvedPractice(): Flow<SavedPageRow?>

    @Query("SELECT puzzleKey FROM saved_games WHERE kind = 'Classic' AND solved = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestUnsolvedPracticeKey(): String?

    @Query("SELECT $PAGE_COLUMNS FROM saved_games WHERE kind = 'Daily' AND epochDay >= :fromEpochDay ORDER BY epochDay DESC")
    fun observeDailies(fromEpochDay: Long): Flow<List<SavedPageRow>>

    @Query("SELECT $PAGE_COLUMNS FROM saved_games WHERE puzzleKey = :puzzleKey")
    fun observePage(puzzleKey: String): Flow<SavedPageRow?>

    /** Highest page number dealt under a key prefix such as "classic-steady-" (the requested level). */
    @Query(
        """
        SELECT MAX(CAST(substr(puzzleKey, length(:prefix) + 1) AS INTEGER)) FROM saved_games
        WHERE kind = 'Classic' AND substr(puzzleKey, 1, length(:prefix)) = :prefix
        """,
    )
    suspend fun maxClassicNumber(prefix: String): Int?

    @Query("DELETE FROM saved_games WHERE kind = 'Classic' AND solved = 0 AND puzzleKey != :except")
    suspend fun discardUnsolvedPractice(except: String)

    @Query("DELETE FROM saved_games WHERE puzzleKey = :puzzleKey")
    suspend fun delete(puzzleKey: String)

    /** Unsolved dailies with no digit written: the only rows that may hold a page never begun. */
    @Query("SELECT puzzleKey FROM saved_games WHERE kind = 'Daily' AND solved = 0 AND grid = givens")
    suspend fun unwrittenDailyKeys(): List<String>

    /** Moves an unreadable row out of every query's way, under a key no page will ask for. */
    @Query("UPDATE saved_games SET puzzleKey = :newKey, kind = 'Damaged' WHERE puzzleKey = :puzzleKey")
    suspend fun setAside(puzzleKey: String, newKey: String)
}

private const val PAGE_COLUMNS = "puzzleKey, kind, difficulty, number, epochDay, solved, givens, grid"

@Database(
    entities = [GameResultEntity::class, SavedGameEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class SudokuDatabase : RoomDatabase() {
    abstract fun resultDao(): GameResultDao
    abstract fun savedGameDao(): SavedGameDao

    companion object {
        const val NAME = "sudoku.db"

        fun create(context: Context, name: String = NAME): SudokuDatabase =
            Room.databaseBuilder(context.applicationContext, SudokuDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
        // The v1 Coach always revealed the digit, so any v1 hint means an answered game.
        db.execSQL("UPDATE `game_results` SET `coachLevel` = 3 WHERE `hints` > 0")
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

/** v2 → v3: printed and current digits beside the JSON, filled in from the saved games. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `saved_games` ADD COLUMN `givens` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `saved_games` ADD COLUMN `grid` TEXT NOT NULL DEFAULT ''")
        val rows = buildList {
            db.query("SELECT `puzzleKey`, `json` FROM `saved_games`").use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
            }
        }
        for ((key, json) in rows) {
            val game = GameState.decode(json) ?: continue
            db.execSQL(
                "UPDATE `saved_games` SET `givens` = ?, `grid` = ? WHERE `puzzleKey` = ?",
                arrayOf(game.spec.givens.digits(), game.values.digits(), key),
            )
        }
    }
}

/** One character per cell; puzzles are at most 9×9. */
fun List<Int>.digits(): String = joinToString("")
