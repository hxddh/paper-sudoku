package com.titpea.papersudoku.data

import android.os.Trace
import android.util.Log
import androidx.room.withTransaction
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    /** When the page was last written on (epoch millis); 0 where it is not known. */
    val updatedAt: Long = 0,
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

    /** The next page number for practising [technique]. */
    suspend fun nextFocusNumber(technique: Technique): Int

    /** Drops unfinished practice pages other than [except]; numbering continues past them. */
    suspend fun discardUnsolvedPractice(except: String)

    /** The practice page to continue, if one is unfinished. */
    suspend fun latestUnsolvedPracticeKey(): String?

    /** Daily pages saved on or after [fromEpochDay], newest first. */
    fun dailies(fromEpochDay: Long): Flow<List<PageSummary>>

    /** One saved page, as it changes. */
    fun page(puzzleKey: String): Flow<PageSummary?>

    /** Pages the player typed in, most recently touched first. */
    val imported: Flow<List<PageSummary>>

    /** Finished pages other than dailies (all 9×9), most recently finished first. */
    val solvedNineByNine: Flow<List<PageSummary>>

    /**
     * Weekly and technique pages begun and not finished, any week: 10.0 has no other way back to
     * them (the weekly card and "Practise this" are gone).
     */
    val unfinishedOthers: Flow<List<PageSummary>>

    /** The practice page to continue, if one is unfinished. */
    val unfinishedPractice: Flow<PageSummary?>

    /** Practice solves without the Coach's answer, per difficulty. */
    val cleanPractice: Flow<Map<Difficulty, Int>>

    val days: Flow<List<DayResult>>

    /** Every stamped page, dailies and practice. */
    val stampedCount: Flow<Int>

    /** When each practice page was finished (epoch millis). */
    val practiceCompletions: Flow<List<Long>>
}

class RoomGameStore(
    private val database: SudokuDatabase,
) : GameStore {
    private val savedGames = database.savedGameDao()
    private val results = database.resultDao()

    override suspend fun load(puzzleKey: String): GameState? {
        val json = savedGames.get(puzzleKey)?.json ?: return null
        return withContext(Dispatchers.Default) {
            Trace.beginSection("ps.decode")
            try { GameState.decode(json) } finally { Trace.endSection() }
        } ?: null.also {
            Log.w(TAG, "Unreadable saved page $puzzleKey; set aside, starting it afresh")
            // Its stamp goes with it: the day reads as not done, and finishing it again stamps it.
            database.withTransaction {
                savedGames.setAside(puzzleKey, "damaged-$puzzleKey-${System.currentTimeMillis()}")
                results.forget(puzzleKey)
            }
        }
    }

    override suspend fun forget(puzzleKey: String) = savedGames.delete(puzzleKey)

    override suspend fun forgetUntouchedDailies() {
        for (key in savedGames.unwrittenDailyKeys()) {
            val row = savedGames.get(key) ?: continue
            val game = withContext(Dispatchers.Default) { GameState.decode(row.json) } ?: continue
            // Only the row as read: a page begun meanwhile was saved again and stays.
            if (game.untouched) savedGames.deleteIfUnchanged(key, row.updatedAt)
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
                    mode = modeOf(result.kind),
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
        (savedGames.maxNumber(PuzzleKind.Classic.name, Catalog.classicPrefix(difficulty)) ?: -1) + 1

    override suspend fun nextFocusNumber(technique: Technique): Int =
        (savedGames.maxNumber(PuzzleKind.Focus.name, Catalog.focusPrefix(technique)) ?: -1) + 1

    override suspend fun discardUnsolvedPractice(except: String) =
        savedGames.discardUnsolvedPractice(except)

    override suspend fun latestUnsolvedPracticeKey(): String? = savedGames.observeLatestUnsolvedPractice().first()?.puzzleKey

    override fun dailies(fromEpochDay: Long): Flow<List<PageSummary>> =
        savedGames.observeDailies(fromEpochDay)
            .map { rows -> rows.mapNotNull(::summary) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun page(puzzleKey: String): Flow<PageSummary?> =
        savedGames.observePage(puzzleKey).map { it?.let(::summary) }.distinctUntilChanged()

    override val imported: Flow<List<PageSummary>> =
        savedGames.observeKind(PuzzleKind.Imported.name)
            .map { rows -> rows.mapNotNull(::summary) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override val unfinishedOthers: Flow<List<PageSummary>> =
        savedGames.observeUnfinishedOthers()
            .map { rows -> rows.mapNotNull(::summary) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override val solvedNineByNine: Flow<List<PageSummary>> =
        savedGames.observeSolvedNineByNine()
            .map { rows -> rows.mapNotNull(::summary) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override val unfinishedPractice: Flow<PageSummary?> =
        savedGames.observeLatestUnsolvedPractice().map { it?.let(::summary) }.distinctUntilChanged()

    override val cleanPractice: Flow<Map<Difficulty, Int>> = results.observeCleanPractice().map { rows ->
        rows.mapNotNull { row ->
            Difficulty.entries.firstOrNull { it.name == row.difficulty }?.let { it to row.solved }
        }.toMap()
    }

    override val days: Flow<List<DayResult>> = results.observeDays()
    override val stampedCount: Flow<Int> = results.observeCount()
    override val practiceCompletions: Flow<List<Long>> = results.observePracticeCompletions()

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
            // A finished page keeps the time it was finished: reading it again later does not move
            // it in the journal, which files it under that day.
            updatedAt = if (state.solved) state.solvedAt ?: now else now,
            givens = spec.givens.digits(),
            grid = state.values.digits(),
        )
    }

    private fun summary(row: SavedPageRow): PageSummary? {
        val kind = PuzzleKind.entries.firstOrNull { it.name == row.kind } ?: return null
        val difficulty = Difficulty.entries.firstOrNull { it.name == row.difficulty } ?: return null
        if (row.grid.length != 36 && row.grid.length != 81) return null
        return PageSummary(row.puzzleKey, kind, difficulty, row.number, row.epochDay, row.solved, row.givens, row.grid, row.updatedAt)
    }

    companion object {
        const val MODE_MINI = "mini"
        const val MODE_CLASSIC = "classic"

        /** Only practice pages are "classic": the levels and the month counts read that mode. */
        fun modeOf(kind: PuzzleKind): String = when (kind) {
            PuzzleKind.Daily -> MODE_MINI
            PuzzleKind.Classic -> MODE_CLASSIC
            PuzzleKind.Weekly -> "weekly"
            PuzzleKind.Imported -> "imported"
            PuzzleKind.Focus -> "focus"
        }
        private const val TAG = "GameStore"
    }
}
