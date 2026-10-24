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

/**
 * One finished puzzle. v1 rows have an empty [puzzleKey]/[difficulty] and no [epochDay].
 */
@Entity(
    tableName = "game_results",
    indices = [Index("puzzleKey")],
)
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "mini" (dailies) or "classic" (practice), kept from v1; 9.0 adds "weekly", "imported" and "focus". */
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
 *
 * Room orders the table's columns by field order: [json] (up to ~110 KB with the undo history) is
 * kept last so that queries reading only the small columns stop before its overflow pages. The two
 * indices serve page numbering and the unfinished-practice lookup.
 */
@Entity(
    tableName = "saved_games",
    indices = [
        Index("kind", "epochDay"),
        Index("kind", "solved", "updatedAt"),
    ],
)
data class SavedGameEntity(
    @PrimaryKey val puzzleKey: String,
    val kind: String,
    val difficulty: String,
    val number: Int?,
    val epochDay: Long?,
    val solved: Boolean,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "''") val givens: String = "",
    @ColumnInfo(defaultValue = "''") val grid: String = "",
    val json: String,
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
    /** When the page was last written on (epoch millis): the date a finished page is filed under. */
    val updatedAt: Long,
)

/** What the notebook needs of a finished puzzle. */
data class ResultRow(
    val mode: String,
    val difficulty: String,
    val coachLevel: Int,
    val durationSeconds: Long,
    val completedAt: Long,
    val epochDay: Long?,
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

    /** Drops a page's result when its saved page could not be read and was set aside. */
    @Query("DELETE FROM game_results WHERE puzzleKey = :puzzleKey AND puzzleKey != ''")
    suspend fun forget(puzzleKey: String)

    /** Every result, read once for everything the notebook counts. */
    @Query("SELECT mode, difficulty, coachLevel, durationSeconds, completedAt, epochDay FROM game_results")
    suspend fun all(): List<ResultRow>
}

@Dao
interface SavedGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: SavedGameEntity)

    @Query("SELECT * FROM saved_games WHERE puzzleKey = :puzzleKey")
    suspend fun get(puzzleKey: String): SavedGameEntity?

    @Query("SELECT puzzleKey FROM saved_games WHERE kind = 'Classic' AND solved = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestUnsolvedPracticeKey(): String?

    /** Every page (small columns only), read once for everything the notebook and widget show. */
    @Query("SELECT $PAGE_COLUMNS FROM saved_games WHERE kind != 'Damaged'")
    suspend fun pages(): List<SavedPageRow>

    /**
     * Highest page number dealt under a key prefix such as "classic-steady-" (the requested level).
     * Read from the stored number, which is the one in the key, path pages included
     * ("classic-steady-12-pointing" is number 12). The prefixes hold no `%` or `_`.
     */
    @Query("SELECT MAX(number) FROM saved_games WHERE kind = :kind AND puzzleKey LIKE :prefix || '%'")
    suspend fun maxNumber(kind: String, prefix: String): Int?

    @Query("DELETE FROM saved_games WHERE kind = 'Classic' AND solved = 0 AND puzzleKey != :except")
    suspend fun discardUnsolvedPractice(except: String)

    @Query("DELETE FROM saved_games WHERE puzzleKey = :puzzleKey")
    suspend fun delete(puzzleKey: String)

    @Query("DELETE FROM saved_games WHERE puzzleKey = :puzzleKey AND updatedAt = :updatedAt")
    suspend fun deleteIfUnchanged(puzzleKey: String, updatedAt: Long)

    /**
     * Unsolved dailies with no digit written and no undo step or Coach use in their save: the only
     * rows that may hold a page never begun. A save leaves out what is at its default, so a page
     * with any undo step or Coach use names `historyPacked`, `history` or `coach`; those rows are
     * skipped here instead of being decoded. Since 8.0 a page only looked at is not saved, so
     * once the old ones are gone this finds nothing, and only the small columns are read for the
     * rows with no digit written.
     */
    @Query(
        """
        SELECT puzzleKey FROM saved_games WHERE kind = 'Daily' AND solved = 0 AND grid = givens
        AND instr(json, '"history') = 0 AND instr(json, '"coach"') = 0
        """,
    )
    suspend fun unwrittenDailyKeys(): List<String>

    /** Moves an unreadable row out of every query's way, under a key no page will ask for. */
    @Query("UPDATE saved_games SET puzzleKey = :newKey, kind = 'Damaged' WHERE puzzleKey = :puzzleKey")
    suspend fun setAside(puzzleKey: String, newKey: String)
}

private const val PAGE_COLUMNS = "puzzleKey, kind, difficulty, number, epochDay, solved, givens, grid, updatedAt"

@Database(
    entities = [GameResultEntity::class, SavedGameEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class SudokuDatabase : RoomDatabase() {
    abstract fun resultDao(): GameResultDao
    abstract fun savedGameDao(): SavedGameDao

    companion object {
        const val NAME = "sudoku.db"

        fun create(context: Context, name: String = NAME): SudokuDatabase =
            Room.databaseBuilder(context.applicationContext, SudokuDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

/**
 * v3 → v4: `saved_games` rebuilt with `json` as its last column (so page queries never walk the large
 * value) and indexed for the notebook's queries. Every row and value is kept.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_games_new` (`puzzleKey` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`difficulty` TEXT NOT NULL, `number` INTEGER, `epochDay` INTEGER, `solved` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `givens` TEXT NOT NULL DEFAULT '', `grid` TEXT NOT NULL DEFAULT '', " +
                "`json` TEXT NOT NULL, PRIMARY KEY(`puzzleKey`))",
        )
        db.execSQL(
            "INSERT INTO `saved_games_new` (`puzzleKey`, `kind`, `difficulty`, `number`, `epochDay`, `solved`, " +
                "`updatedAt`, `givens`, `grid`, `json`) SELECT `puzzleKey`, `kind`, `difficulty`, `number`, " +
                "`epochDay`, `solved`, `updatedAt`, `givens`, `grid`, `json` FROM `saved_games`",
        )
        db.execSQL("DROP TABLE `saved_games`")
        db.execSQL("ALTER TABLE `saved_games_new` RENAME TO `saved_games`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_games_kind_epochDay` ON `saved_games` (`kind`, `epochDay`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_saved_games_kind_solved_updatedAt` " +
                "ON `saved_games` (`kind`, `solved`, `updatedAt`)",
        )
    }
}

/** One character per cell; puzzles are at most 9×9. */
fun List<Int>.digits(): String = joinToString("")
