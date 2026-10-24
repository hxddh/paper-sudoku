package com.titpea.papersudoku.screenshots

import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.notebook.FiledPage
import com.titpea.papersudoku.notebook.NotebookUiState
import com.titpea.papersudoku.notebook.PageEntry
import com.titpea.papersudoku.notebook.PracticeEntry
import com.titpea.papersudoku.page.Fixtures

/**
 * A notebook three weeks in: a few stamped days, one begun and left (the page to continue),
 * today begun, practice learning naked pairs, and two finished practice pages filed on their days
 * (one beside a stamped day, so that day holds two pages).
 */
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
    fun practicePage(number: Int) = PageSummary.of(Fixtures.solved(GameState.new(Catalog.classic(Difficulty.Steady, number))))
    val others = mapOf(
        today - 2 to listOf(practicePage(1)),
        today - 3 to listOf(practicePage(0)),
    )
    val days = (saved.keys + results.keys + others.keys).distinct().sortedDescending()
    val pages = days.associateWith { day -> PageEntry(day, saved[day], results[day], others[day].orEmpty()) }
    return NotebookUiState(
        today = today,
        todayPage = pages.getValue(today),
        practice = PracticeEntry(learning = Technique.NakedPair, known = 4, saved = PageSummary.of(Fixtures.practice())),
        firstDay = today - 20,
        pages = pages,
        resume = FiledPage(saved.getValue(today - 6), today - 6),
    )
}
