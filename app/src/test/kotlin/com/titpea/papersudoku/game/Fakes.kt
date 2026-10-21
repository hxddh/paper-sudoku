package com.titpea.papersudoku.game

import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.StatsRow
import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeGameStore : GameStore {
    val saved = linkedMapOf<String, Pair<GameState, Long>>()
    val results = mutableListOf<GameResult>()
    private val statsFlow = MutableStateFlow<List<StatsRow>>(emptyList())
    private val daysFlow = MutableStateFlow<List<DayResult>>(emptyList())

    override suspend fun load(puzzleKey: String): GameState? =
        saved[puzzleKey]?.first?.encode()?.let(GameState::decode)

    override suspend fun save(state: GameState, now: Long) {
        saved[state.spec.key] = state to now
    }

    override suspend fun latestUnsolvedClassic(difficulty: Difficulty): GameState? =
        saved.values
            .filter { (g, _) -> g.spec.kind == PuzzleKind.Classic && g.spec.difficulty == difficulty && !g.solved }
            .maxByOrNull { it.second }
            ?.first

    override suspend fun nextClassicNumber(difficulty: Difficulty): Int =
        (saved.values.mapNotNull { (g, _) -> g.spec.number.takeIf { g.spec.difficulty == difficulty } }.maxOrNull() ?: -1) + 1

    override suspend fun pruneUnsolvedDailies(beforeEpochDay: Long) {
        saved.entries.removeAll { (_, v) ->
            v.first.spec.kind == PuzzleKind.Daily && !v.first.solved && (v.first.spec.epochDay ?: 0) < beforeEpochDay
        }
    }

    override suspend fun recordOnce(result: GameResult): Boolean {
        if (results.any { it.puzzleKey == result.puzzleKey }) return false
        results += result
        return true
    }

    override val stats: Flow<List<StatsRow>> = statsFlow
    override val days: Flow<List<DayResult>> = daysFlow
}

class FakeSettings(initial: UserSettings = UserSettings()) : SettingsStore {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = flow
    override suspend fun update(transform: (UserSettings) -> UserSettings) = flow.update(transform)
}
