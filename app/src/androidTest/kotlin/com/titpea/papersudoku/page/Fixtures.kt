package com.titpea.papersudoku.page

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState

/** Deterministic pages for UI tests and screenshots. */
object Fixtures {
    const val DAY = 20_747L // 2026-10-21, a Wednesday
    val daily = Catalog.daily(DAY)
    val fresh: GameState = GameState.new(daily)
    val firstEmpty = daily.givens.indexOfFirst { it == 0 }

    fun page(game: GameState = fresh, coach: CoachUi? = null) =
        PageUiState(loading = false, game = game, coach = coach, today = DAY)

    fun coach(level: Int, game: GameState = fresh) =
        CoachUi(SudokuEngine(game.spec.puzzle).hint(game.values.toIntArray()), level)

    fun solved(game: GameState = fresh): GameState {
        var s = game
        for (i in s.spec.givens.indices) if (s.spec.givens[i] == 0) s = s.select(i).input(s.spec.solution[i], true)
        return s.addElapsed(125_000).settle(now = 1L)
    }

    /** A 9×9 practice page part-way through, with pencil notes and a digit held. */
    fun practice(): GameState {
        var s = GameState.new(Catalog.classic(Difficulty.Steady, 0))
        val empties = s.spec.givens.indices.filter { s.spec.givens[it] == 0 }
        empties.take(12).forEach { s = s.select(it).input(s.spec.solution[it], true) }
        s = s.toggleNotesMode()
        empties.drop(12).take(10).forEach { i ->
            s = s.select(i).input(s.spec.solution[i], false).input((s.spec.solution[i] % 9) + 1, false)
        }
        return s.toggleNotesMode().select(empties[30]).toggleLock(s.spec.solution[empties[30]])
    }
}
