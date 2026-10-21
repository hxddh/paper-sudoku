package com.titpea.papersudoku.data

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.flow.Flow

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

/** Saved boards and finished results. An interface so ViewModel tests can use a fake. */
interface GameStore {
    suspend fun load(puzzleKey: String): GameState?
    suspend fun save(state: GameState, now: Long)
    suspend fun latestUnsolvedClassic(difficulty: Difficulty): GameState?
    suspend fun nextClassicNumber(difficulty: Difficulty): Int
    suspend fun pruneUnsolvedDailies(beforeEpochDay: Long)

    /** Records the first completion of a puzzle; later replays are ignored. */
    suspend fun recordOnce(result: GameResult): Boolean

    val stats: Flow<List<StatsRow>>
    val days: Flow<List<DayResult>>
}

class RoomGameStore(
    private val savedGames: SavedGameDao,
    private val results: GameResultDao,
) : GameStore {

    override suspend fun load(puzzleKey: String): GameState? =
        savedGames.get(puzzleKey)?.json?.let(GameState::decode)

    override suspend fun save(state: GameState, now: Long) {
        val spec = state.spec
        savedGames.upsert(
            SavedGameEntity(
                puzzleKey = spec.key,
                kind = spec.kind.name,
                difficulty = spec.difficulty.name,
                number = spec.number,
                epochDay = spec.epochDay,
                solved = state.solved,
                json = state.encode(),
                updatedAt = now,
            ),
        )
    }

    override suspend fun latestUnsolvedClassic(difficulty: Difficulty): GameState? =
        savedGames.latestUnsolvedClassic(difficulty.name)?.json?.let(GameState::decode)

    override suspend fun nextClassicNumber(difficulty: Difficulty): Int =
        (savedGames.maxClassicNumber(difficulty.name) ?: -1) + 1

    override suspend fun pruneUnsolvedDailies(beforeEpochDay: Long) =
        savedGames.pruneUnsolvedDailies(beforeEpochDay)

    override suspend fun recordOnce(result: GameResult): Boolean {
        if (results.exists(result.puzzleKey)) return false
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
        return true
    }

    override val stats: Flow<List<StatsRow>> = results.observeStats()
    override val days: Flow<List<DayResult>> = results.observeDays()

    companion object {
        const val MODE_MINI = "mini"
        const val MODE_CLASSIC = "classic"
    }
}
