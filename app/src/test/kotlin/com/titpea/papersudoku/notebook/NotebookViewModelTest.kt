package com.titpea.papersudoku.notebook

import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import com.titpea.papersudoku.page.FakeGameStore
import com.titpea.papersudoku.page.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 10, 21).toEpochDay()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC)
    private val store = FakeGameStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(firstDay: Long) = NotebookViewModel(store, FakeSettings(), clock, firstDay)

    private suspend fun NotebookViewModel.loaded(): NotebookUiState = state.first { it.loaded }

    private fun begun(day: Long): GameState = GameState.new(Catalog.daily(day)).let { g ->
        val empty = g.spec.givens.indexOfFirst { it == 0 }
        g.select(empty).input(g.spec.solution[empty], true)
    }

    @Test
    fun `a new notebook has no wall of blank days`() = runTest(dispatcher) {
        val state = viewModel(firstDay = today).loaded()
        assertTrue(state.unfinished.isEmpty() && state.stampedDays.isEmpty())
        assertEquals(today, state.todayPage?.epochDay)
        assertEquals(today, state.firstDay)
    }

    @Test
    fun `only pages written on are listed, and the contents reach back to the install day`() = runTest(dispatcher) {
        store.save(begun(today - 2), now = 1)
        val state = viewModel(firstDay = today - 30).loaded()
        assertEquals(listOf(today - 2), state.unfinished)
        assertTrue(state.stampedDays.isEmpty())
        assertEquals(today - 30, state.firstDay)
        assertFalse(today - 5 in state.pages)
    }

    @Test
    fun `stamped days are listed apart from unfinished ones`() = runTest(dispatcher) {
        val solved = Catalog.daily(today - 1).let { spec -> GameState.new(spec).copy(values = spec.solution).settle(5) }
        store.saveSolved(solved, GameResult(solved.spec.key, PuzzleKind.Daily, solved.spec.difficulty, 60, 0, 0, 1, today - 1), now = 1)
        store.save(begun(today - 3), now = 1)
        val state = viewModel(firstDay = today - 10).loaded()
        assertEquals(listOf(today - 1), state.stampedDays)
        assertEquals(listOf(today - 3), state.unfinished)
    }

    @Test
    fun `pages from before the install (a restored backup) open the notebook`() = runTest(dispatcher) {
        store.save(begun(today - 10), now = 1)
        val state = viewModel(firstDay = today).loaded()
        assertEquals(today - 10, state.firstDay)
        assertFalse(state.pages.getValue(today - 10).saved == null)
    }

    @Test
    fun `practice pages count as stamped`() = runTest(dispatcher) {
        val practice = GameState.new(Catalog.classic(Difficulty.Gentle, 0))
        store.saveSolved(practice, GameResult(practice.spec.key, PuzzleKind.Classic, Difficulty.Gentle, 60, 0, 0, 1, null), now = 1)
        val vm = viewModel(firstDay = today)
        val state = vm.state.first { it.loaded && it.stamped == 1 }
        assertEquals(1, state.stamped)
        assertEquals(1, state.practiceByMonth.values.sum())
    }

    @Test
    fun `finished practice pages are filed in the journal among the stamped days`() = runTest(dispatcher) {
        val day = Catalog.daily(today - 1).let { spec -> GameState.new(spec).copy(values = spec.solution).settle(5) }
        store.saveSolved(day, GameResult(day.spec.key, PuzzleKind.Daily, day.spec.difficulty, 60, 0, 0, 1, today - 1), now = 1)
        val practice = Catalog.classic(Difficulty.Gentle, 0).let { spec -> GameState.new(spec).copy(values = spec.solution).settle(5) }
        val finishedAt = Instant.parse("2026-10-21T08:00:00Z").toEpochMilli()
        store.saveSolved(practice, GameResult(practice.spec.key, PuzzleKind.Classic, Difficulty.Gentle, 60, 0, 0, 1, null), now = finishedAt)
        val state = viewModel(firstDay = today - 10).state.first { it.loaded && it.journal.sumOf { m -> m.rows.size } == 2 }
        val month = state.journal.single()
        assertEquals(YearMonth.of(2026, 10), month.month)
        // Newest first: the practice page finished today, then yesterday's stamped day.
        assertEquals(listOf(practice.spec.key, Catalog.dailyKey(today - 1)), month.rows.map { it.key })
        assertEquals(LocalDate.of(2026, 10, 21), month.rows.first().date)
        assertEquals(listOf(today - 1), state.stampedDays)
    }
}
