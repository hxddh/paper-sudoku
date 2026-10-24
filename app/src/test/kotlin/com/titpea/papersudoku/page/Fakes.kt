package com.titpea.papersudoku.page

import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeGameStore : GameStore {
    private val savedFlow = MutableStateFlow<Map<String, Pair<GameState, Long>>>(emptyMap())
    val saved: Map<String, Pair<GameState, Long>> get() = savedFlow.value
    val results = mutableListOf<GameResult>()
    val clean = MutableStateFlow<Map<Difficulty, Int>>(emptyMap())
    private val daysFlow = MutableStateFlow<List<DayResult>>(emptyList())

    override suspend fun load(puzzleKey: String): GameState? =
        saved[puzzleKey]?.first?.encode()?.let(GameState::decode)

    override suspend fun save(state: GameState, now: Long) {
        savedFlow.update { it + (state.spec.key to (state to now)) }
    }

    override suspend fun forget(puzzleKey: String) {
        savedFlow.update { it - puzzleKey }
    }

    override suspend fun forgetUntouchedDailies() {
        savedFlow.update { map -> map.filterValues { (g, _) -> !(g.spec.kind == PuzzleKind.Daily && g.untouched) } }
    }

    override suspend fun saveSolved(state: GameState, result: GameResult, now: Long): Boolean {
        save(state, now)
        if (results.any { it.puzzleKey == result.puzzleKey }) return false
        results += result
        stampedFlow.value = results.size
        result.epochDay?.let { day -> daysFlow.update { it + DayResult(day, result.coachLevel, result.durationSeconds) } }
        return true
    }

    override suspend fun nextClassicNumber(difficulty: Difficulty): Int {
        val prefix = com.titpea.papersudoku.game.model.Catalog.classicPrefix(difficulty)
        return (saved.keys.filter { it.startsWith(prefix) }.mapNotNull { it.removePrefix(prefix).takeWhile(Char::isDigit).toIntOrNull() }.maxOrNull() ?: -1) + 1
    }

    override suspend fun nextFocusNumber(technique: Technique): Int {
        val prefix = com.titpea.papersudoku.game.model.Catalog.focusPrefix(technique)
        return (saved.keys.filter { it.startsWith(prefix) }.mapNotNull { it.removePrefix(prefix).takeWhile(Char::isDigit).toIntOrNull() }.maxOrNull() ?: -1) + 1
    }

    override suspend fun discardUnsolvedPractice(except: String) {
        savedFlow.update { map ->
            map.filter { (key, value) -> key == except || !(value.first.spec.kind == PuzzleKind.Classic && !value.first.solved) }
        }
    }

    override suspend fun latestUnsolvedPracticeKey(): String? =
        saved.values.filter { (g, _) -> g.spec.kind == PuzzleKind.Classic && !g.solved }.maxByOrNull { it.second }?.first?.spec?.key

    override fun dailies(fromEpochDay: Long): Flow<List<PageSummary>> = savedFlow.map { map ->
        map.values.map { it.first }
            .filter { it.spec.kind == PuzzleKind.Daily && (it.spec.epochDay ?: 0) >= fromEpochDay }
            .map(PageSummary::of)
    }

    override fun page(puzzleKey: String): Flow<PageSummary?> =
        savedFlow.map { map -> map[puzzleKey]?.first?.let(PageSummary::of) }

    override val imported: Flow<List<PageSummary>> = savedFlow.map { map ->
        map.values.filter { (g, _) -> g.spec.kind == PuzzleKind.Imported }.sortedByDescending { it.second }.map { PageSummary.of(it.first) }
    }

    override val solvedNineByNine: Flow<List<PageSummary>> = savedFlow.map { map ->
        map.values.filter { (g, _) -> g.spec.kind != PuzzleKind.Daily && g.solved }
            .sortedByDescending { it.second }
            .map { (g, at) -> PageSummary.of(g).copy(updatedAt = at) }
    }

    override val unfinishedPractice: Flow<PageSummary?> = savedFlow.map { map ->
        map.values.filter { (g, _) -> g.spec.kind == PuzzleKind.Classic && !g.solved }.maxByOrNull { it.second }?.first
            ?.let(PageSummary::of)
    }

    override val cleanPractice: Flow<Map<Difficulty, Int>> = clean
    override val days: Flow<List<DayResult>> = daysFlow
    private val stampedFlow = MutableStateFlow(0)
    override val stampedCount: Flow<Int> = stampedFlow
    override val practiceCompletions: Flow<List<Long>> = savedFlow.map {
        results.filter { it.kind == PuzzleKind.Classic }.map { it.completedAt }
    }
}

class FakeSettings(initial: UserSettings = UserSettings()) : SettingsStore {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = flow
    override suspend fun update(transform: (UserSettings) -> UserSettings) = flow.update(transform)
}
