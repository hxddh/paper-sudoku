package com.titpea.papersudoku.data

import android.util.Log
import androidx.room.withTransaction
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class GameResult(
    val puzzleKey: String,
    val kind: PuzzleKind,
    val difficulty: Difficulty,
    val durationSeconds: Long,
    val coachAsks: Int,
    val coachLevel: Int,
    val completedAt: Long,
    val epochDay: Long?,
)

/**
 * A saved page as the notebook and widget draw it: the printed and current digits, one character
 * per cell, without the undo history.
 */
data class PageSummary(
    val key: String,
    val kind: PuzzleKind,
    val difficulty: Difficulty,
    val number: Int?,
    val epochDay: Long?,
    val solved: Boolean,
    val givens: String,
    val grid: String,
) {
    val size: Int get() = if (grid.length == 36) 6 else 9
    val boxRows: Int get() = if (size == 6) 2 else 3
    val boxCols: Int get() = 3

    fun valueAt(index: Int): Int = grid[index] - '0'
    fun isGiven(index: Int): Boolean = givens.getOrNull(index)?.let { it != '0' } ?: false

    companion object {
        fun of(game: GameState) = PageSummary(
            key = game.spec.key,
            kind = game.spec.kind,
            difficulty = game.spec.difficulty,
            number = game.spec.number,
            epochDay = game.spec.epochDay,
            solved = game.solved,
            givens = game.spec.givens.digits(),
            grid = game.values.digits(),
        )
    }
}

/** Saved pages and finished results. An interface so ViewModel tests can use a fake. */
interface GameStore {
    /** The saved page, or null. An unreadable save is set aside (kept, out of the way), not lost. */
    suspend fun load(puzzleKey: String): GameState?
    suspend fun save(state: GameState, now: Long)

    /** Drops a saved page: a daily only looked at is not kept as begun. */
    suspend fun forget(puzzleKey: String)

    /** Drops saved dailies that were opened but never begun (saved that way before 8.0). */
    suspend fun forgetUntouchedDailies()

    /**
     * Saves the solved page and records its first completion together, so a page is never left
     * solved but unstamped. Later replays are ignored; returns whether a result was recorded.
     */
    suspend fun saveSolved(state: GameState, result: GameResult, now: Long): Boolean

    suspend fun nextClassicNumber(difficulty: Difficulty): Int

    /** Drops unfinished practice pages other than [except]; numbering continues past them. */
    suspend fun discardUnsolvedPractice(except: String)

    /** The practice page to continue, if one is unfinished. */
    suspend fun latestUnsolvedPracticeKey(): String?

    /** Daily pages saved on or after [fromEpochDay], newest first. */
    fun dailies(fromEpochDay: Long): Flow<List<PageSummary>>

    /** One saved page, as it changes. */
    fun page(puzzleKey: String): Flow<PageSummary?>

    /** The practice page to continue, if one is unfinished. */
    val unfinishedPractice: Flow<PageSummary?>

    /** Practice solves without the Coach's answer, per difficulty. */
    val cleanPractice: Flow<Map<Difficulty, Int>>

    val days: Flow<List<DayResult>>

    /** Every stamped page, dailies and practice. */
    val stampedCount: Flow<Int>
}

class RoomGameStore(
    private val database: SudokuDatabase,
) : GameStore {
    private val savedGames = database.savedGameDao()
    private val results = database.resultDao()

    override suspend fun load(puzzleKey: String): GameState? {
        val json = savedGames.get(puzzleKey)?.json ?: return null
        return withContext(Dispatchers.Default) { GameState.decode(json) } ?: null.also {
            Log.w(TAG, "Unreadable saved page $puzzleKey; set aside, starting it afresh")
            savedGames.setAside(puzzleKey, "damaged-$puzzleKey-${System.currentTimeMillis()}")
        }
    }

    override suspend fun forget(puzzleKey: String) = savedGames.delete(puzzleKey)

    override suspend fun forgetUntouchedDailies() {
        for (key in savedGames.unwrittenDailyKeys()) {
            val json = savedGames.get(key)?.json ?: continue
            val game = withContext(Dispatchers.Default) { GameState.decode(json) } ?: continue
            if (game.untouched) savedGames.delete(key)
        }
    }

    override suspend fun save(state: GameState, now: Long) {
        savedGames.upsert(entity(state, now))
    }

    override suspend fun saveSolved(state: GameState, result: GameResult, now: Long): Boolean {
        val entity = entity(state, now)
        return database.withTransaction {
            savedGames.upsert(entity)
            if (results.exists(result.puzzleKey)) return@withTransaction false
            results.insert(
                GameResultEntity(
                    mode = if (result.kind == PuzzleKind.Daily) MODE_MINI else MODE_CLASSIC,
                    durationSeconds = result.durationSeconds,
                    hints = result.coachAsks,
                    completedAt = result.completedAt,
                    puzzleKey = result.puzzleKey,
                    difficulty = result.difficulty.name,
                    coachLevel = result.coachLevel,
                    epochDay = result.epochDay,
                ),
            )
            true
        }
    }

    // Numbered by the level the page was dealt at (its key), not the grade the generator reached,
    // so a page that came out easier can never be dealt twice.
    override suspend fun nextClassicNumber(difficulty: Difficulty): Int =
        (savedGames.maxClassicNumber(Catalog.classicPrefix(difficulty)) ?: -1) + 1

    override suspend fun discardUnsolvedPractice(except: String) =
        savedGames.discardUnsolvedPractice(except)

    override suspend fun latestUnsolvedPracticeKey(): String? = savedGames.latestUnsolvedPracticeKey()

    override fun dailies(fromEpochDay: Long): Flow<List<PageSummary>> =
        savedGames.observeDailies(fromEpochDay)
            .map { rows -> rows.mapNotNull(::summary) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun page(puzzleKey: String): Flow<PageSummary?> =
        savedGames.observePage(puzzleKey).map { it?.let(::summary) }.distinctUntilChanged()

    override val unfinishedPractice: Flow<PageSummary?> =
        savedGames.observeLatestUnsolvedPractice().map { it?.let(::summary) }.distinctUntilChanged()

    override val cleanPractice: Flow<Map<Difficulty, Int>> = results.observeCleanPractice().map { rows ->
        rows.mapNotNull { row ->
            Difficulty.entries.firstOrNull { it.name == row.difficulty }?.let { it to row.solved }
        }.toMap()
    }

    override val days: Flow<List<DayResult>> = results.observeDays()
    override val stampedCount: Flow<Int> = results.observeCount()

    private suspend fun entity(state: GameState, now: Long): SavedGameEntity {
        val spec = state.spec
        val json = withContext(Dispatchers.Default) { state.encode() }
        return SavedGameEntity(
            puzzleKey = spec.key,
            kind = spec.kind.name,
            difficulty = spec.difficulty.name,
            number = spec.number,
            epochDay = spec.epochDay,
            solved = state.solved,
            json = json,
            updatedAt = now,
            givens = spec.givens.digits(),
            grid = state.values.digits(),
        )
    }

    private fun summary(row: SavedPageRow): PageSummary? {
        val kind = PuzzleKind.entries.firstOrNull { it.name == row.kind } ?: return null
        val difficulty = Difficulty.entries.firstOrNull { it.name == row.difficulty } ?: return null
        if (row.grid.length != 36 && row.grid.length != 81) return null
        return PageSummary(row.puzzleKey, kind, difficulty, row.number, row.epochDay, row.solved, row.givens, row.grid)
    }

    companion object {
        const val MODE_MINI = "mini"
        const val MODE_CLASSIC = "classic"
        private const val TAG = "GameStore"
    }
}
