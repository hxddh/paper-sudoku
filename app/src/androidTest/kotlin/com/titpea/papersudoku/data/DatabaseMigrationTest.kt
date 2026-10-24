package com.titpea.papersudoku.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Older installs upgrade in place and keep their results and pages; the store's queries hold. */
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun clear() {
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile?.mkdirs()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    private fun raw(version: Int, block: SQLiteDatabase.() -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.block()
            db.version = version
        }
    }

    @Test
    fun migratesV1Results() = runBlocking {
        raw(version = 1) {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `game_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mode` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `hints` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL)",
            )
            execSQL("INSERT INTO game_results (mode, durationSeconds, hints, completedAt) VALUES ('mini', 95, 1, 1000)")
            execSQL("INSERT INTO game_results (mode, durationSeconds, hints, completedAt) VALUES ('classic', 600, 0, 2000)")
        }
        val database = SudokuDatabase.create(context, name)
        try {
            assertEquals(2, database.resultDao().observeCount().first())
            val stats = database.resultDao().observeStats().first()
            assertEquals(listOf("classic", "mini"), stats.map { it.mode })
            // The v1 mini used a hint (the old Coach revealed digits), so it is not a clean best.
            assertEquals(null, stats.single { it.mode == "mini" }.bestSeconds)
            assertEquals(600L, stats.single { it.mode == "classic" }.bestSeconds)
        } finally {
            database.close()
        }
    }

    @Test
    fun migratesV2PagesWithTheirDigits() = runBlocking {
        val game = GameState.new(Catalog.daily(20_000)).let { it.select(it.spec.givens.indexOfFirst { v -> v == 0 }).input(1, true) }
        raw(version = 2) {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `game_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mode` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `hints` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL, `puzzleKey` TEXT NOT NULL DEFAULT '', " +
                    "`difficulty` TEXT NOT NULL DEFAULT '', `coachLevel` INTEGER NOT NULL DEFAULT 0, `epochDay` INTEGER)",
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_game_results_puzzleKey` ON `game_results` (`puzzleKey`)")
            execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_games` (`puzzleKey` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`difficulty` TEXT NOT NULL, `number` INTEGER, `epochDay` INTEGER, `solved` INTEGER NOT NULL, " +
                    "`json` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`puzzleKey`))",
            )
            execSQL(
                "INSERT INTO saved_games VALUES (?, 'Daily', 'Gentle', NULL, 20000, 0, ?, 7)",
                arrayOf(game.spec.key, game.encode()),
            )
            execSQL("INSERT INTO saved_games VALUES ('broken', 'Daily', 'Gentle', NULL, 20001, 0, '{not json', 8)")
        }
        val database = SudokuDatabase.create(context, name)
        try {
            val store = RoomGameStore(database)
            assertEquals(game, store.load(game.spec.key))
            val page = store.dailies(0).first().single()
            assertEquals(PageSummary.of(game), page.copy(updatedAt = 0)) // when it was saved is not compared
            assertNull(store.load("broken"))
        } finally {
            database.close()
        }
    }

    @Test
    fun migratesV3PagesWithJsonLastAndIndices() = runBlocking {
        val daily = GameState.new(Catalog.daily(20_000)).let { it.select(it.spec.givens.indexOfFirst { v -> v == 0 }).input(4, true) }
        val practice = GameState.new(Catalog.classic(Difficulty.Gentle, 0))
        raw(version = 3) {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `game_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mode` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `hints` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL, `puzzleKey` TEXT NOT NULL DEFAULT '', " +
                    "`difficulty` TEXT NOT NULL DEFAULT '', `coachLevel` INTEGER NOT NULL DEFAULT 0, `epochDay` INTEGER)",
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_game_results_puzzleKey` ON `game_results` (`puzzleKey`)")
            execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_games` (`puzzleKey` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`difficulty` TEXT NOT NULL, `number` INTEGER, `epochDay` INTEGER, `solved` INTEGER NOT NULL, " +
                    "`json` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `givens` TEXT NOT NULL DEFAULT '', " +
                    "`grid` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`puzzleKey`))",
            )
            execSQL(
                "INSERT INTO game_results (mode, durationSeconds, hints, completedAt, puzzleKey, difficulty, coachLevel, epochDay) " +
                    "VALUES ('classic', 300, 0, 9, 'done', 'Gentle', 0, 19999)",
            )
            for ((game, updatedAt) in listOf(daily to 7L, practice to 8L)) {
                execSQL(
                    "INSERT INTO saved_games VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        game.spec.key, game.spec.kind.name, game.spec.difficulty.name, game.spec.number,
                        game.spec.epochDay, game.encode(), updatedAt, game.spec.givens.digits(), game.values.digits(),
                    ),
                )
            }
        }
        val database = SudokuDatabase.create(context, name)
        try {
            val store = RoomGameStore(database)
            assertEquals(1, database.resultDao().observeCount().first())
            assertEquals(daily, store.load(daily.spec.key))
            assertEquals(practice, store.load(practice.spec.key))
            assertEquals(listOf(PageSummary.of(daily)), store.dailies(0).first().map { it.copy(updatedAt = 0) })
            assertEquals(PageSummary.of(practice), store.unfinishedPractice.first()?.copy(updatedAt = 0))
            assertEquals(practice.spec.key, store.latestUnsolvedPracticeKey())

            val sqlite = database.openHelper.readableDatabase
            val columns = buildList {
                sqlite.query("PRAGMA table_info(`saved_games`)").use { c ->
                    val nameIndex = c.getColumnIndexOrThrow("name")
                    while (c.moveToNext()) add(c.getString(nameIndex))
                }
            }
            assertEquals("json", columns.last())
            assertEquals(10, columns.size)
            val indices = buildSet {
                sqlite.query("PRAGMA index_list(`saved_games`)").use { c ->
                    val nameIndex = c.getColumnIndexOrThrow("name")
                    while (c.moveToNext()) add(c.getString(nameIndex))
                }
            }
            assertTrue(indices.containsAll(setOf("index_saved_games_kind_epochDay", "index_saved_games_kind_solved_updatedAt")))
        } finally {
            database.close()
        }
    }

    @Test
    fun storeQueries() = runBlocking {
        val database = SudokuDatabase.create(context, name)
        try {
            val store = RoomGameStore(database)
            val practice = GameState.new(Catalog.classic(Difficulty.Gentle, 0))
            store.save(practice, now = 5)
            assertEquals(practice, store.load(practice.spec.key))
            assertEquals(PageSummary.of(practice), store.unfinishedPractice.first()?.copy(updatedAt = 0))
            assertEquals(practice.spec.key, store.latestUnsolvedPracticeKey())
            assertEquals(1, store.nextClassicNumber(Difficulty.Gentle))

            // Setting a page aside drops it; numbering continues past the replacement.
            val next = GameState.new(Catalog.classic(Difficulty.Gentle, 1))
            store.save(next, now = 6)
            store.discardUnsolvedPractice(except = next.spec.key)
            assertNull(store.load(practice.spec.key))
            assertEquals(2, store.nextClassicNumber(Difficulty.Gentle))

            // Unfinished dailies are kept, however old: the notebook never loses a started page.
            val old = GameState.new(Catalog.daily(100))
            val kept = GameState.new(Catalog.daily(101))
            store.save(old, now = 1)
            store.save(kept, now = 1)
            assertEquals(listOf(kept.spec.key, old.spec.key), store.dailies(0).first().map { it.key })

            // A solve is saved and recorded together, once; answered solves are not clean.
            val result = GameResult(next.spec.key, PuzzleKind.Classic, Difficulty.Gentle, 300, 1, 3, 10, null)
            assertTrue(store.saveSolved(next, result, now = 9))
            assertFalse(store.saveSolved(next, result, now = 10))
            assertEquals(1, database.resultDao().observeCount().first())
            assertEquals(emptyMap<Difficulty, Int>(), store.cleanPractice.first())
            val daily = GameResult(kept.spec.key, PuzzleKind.Daily, Difficulty.Gentle, 50, 0, 0, 11, 101)
            assertTrue(store.saveSolved(kept, daily, now = 11))
            assertEquals(listOf(DayResult(101, 0, 50)), store.days.first())
        } finally {
            database.close()
        }
    }
}
