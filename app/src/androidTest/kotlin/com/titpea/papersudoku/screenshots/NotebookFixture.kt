package com.titpea.papersudoku.screenshots

import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.notebook.NotebookUiState
import com.titpea.papersudoku.notebook.PageEntry
import com.titpea.papersudoku.notebook.PracticeEntry
import com.titpea.papersudoku.page.Fixtures
import java.time.LocalDate
import java.time.YearMonth

/** A notebook three weeks in: a few stamped days, one unfinished, today begun, practice under way. */
fun notebookState(): NotebookUiState {
    val today = Fixtures.DAY
    fun begun(day: Long): PageSummary = GameState.new(Catalog.daily(day)).let { g ->
        val empty = g.spec.givens.indexOfFirst { it == 0 }
        PageSummary.of(g.select(empty).input(g.spec.solution[empty], true))
    }
    val results = mapOf(
        today - 1 to DayResult(today - 1, 3, 301),
        today - 2 to DayResult(today - 2, 0, 125),
        today - 4 to DayResult(today - 4, 0, 98),
    )
    val saved = mapOf(
        today to PageSummary.of(Fixtures.fresh),
        today - 2 to PageSummary.of(Fixtures.solved(GameState.new(Catalog.daily(today - 2)))),
        today - 6 to begun(today - 6),
    )
    val days = (saved.keys + results.keys).distinct().sortedDescending()
    val pages = days.associateWith { day -> PageEntry(day, Catalog.dailyDifficulty(day), saved[day], results[day]) }
    return NotebookUiState(
        today = today,
        todayPage = pages.getValue(today),
        practice = PracticeEntry(Difficulty.Steady, 2, PageSummary.of(Fixtures.practice())),
        firstDay = today - 20,
        unfinished = listOf(today - 6),
        stampedDays = listOf(today - 1, today - 2, today - 4),
        pages = pages,
        practiceByMonth = mapOf(YearMonth.from(LocalDate.ofEpochDay(today)) to 2),
        stamped = 5,
    )
}
