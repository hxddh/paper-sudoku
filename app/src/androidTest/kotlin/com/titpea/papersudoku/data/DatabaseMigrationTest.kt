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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A v1 install (Room schema 1) upgrades in place and keeps its results. */
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun createVersion1() {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `game_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mode` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `hints` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL)",
            )
            db.execSQL("INSERT INTO game_results (mode, durationSeconds, hints, completedAt) VALUES ('mini', 95, 1, 1000)")
            db.execSQL("INSERT INTO game_results (mode, durationSeconds, hints, completedAt) VALUES ('classic', 600, 0, 2000)")
            db.version = 1
        }
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun migratesV1ResultsAndStoresV2Games() = runBlocking {
        val database = SudokuDatabase.create(context, name)
        try {
            assertEquals(2, database.resultDao().count())
            val stats = database.resultDao().observeStats().first()
            assertEquals(listOf("classic", "mini"), stats.map { it.mode })
            // The v1 mini used a hint (the old Coach revealed digits), so it is not a clean best.
            assertEquals(null, stats.single { it.mode == "mini" }.bestSeconds)
            assertEquals(600L, stats.single { it.mode == "classic" }.bestSeconds)

            val store = RoomGameStore(database.savedGameDao(), database.resultDao())
            val game = GameState.new(Catalog.classic(Difficulty.Gentle, 0))
            store.save(game, now = 5)
            assertEquals(game, store.load(game.spec.key))
            assertEquals(game, store.latestUnsolvedClassic(Difficulty.Gentle))
            assertEquals(1, store.nextClassicNumber(Difficulty.Gentle))

            val result = GameResult(game.spec.key, PuzzleKind.Classic, Difficulty.Gentle, 300, 0, 0, 10, null)
            assertTrue(store.recordOnce(result))
            assertFalse(store.recordOnce(result))
            assertEquals(3, database.resultDao().count())
        } finally {
            database.close()
        }
    }
}
