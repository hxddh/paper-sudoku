package com.titpea.papersudoku.notebook

import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.Learner
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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

    private fun solvedDaily(day: Long): GameState =
        Catalog.daily(day).let { spec -> GameState.new(spec).copy(values = spec.solution).settle(5) }

    private fun at(text: String): Long = Instant.parse(text).toEpochMilli()

    @Test
    fun `a new notebook has no wall of blank days`() = runTest(dispatcher) {
        val state = viewModel(firstDay = today).loaded()
        assertEquals(setOf(today), state.pages.keys)
        assertEquals(today, state.todayPage?.epochDay)
        assertEquals(today, state.firstDay)
        assertNull(state.resume)
    }

    @Test
    fun `only days written on are kept, and the contents reach back to the install day`() = runTest(dispatcher) {
        store.save(begun(today - 2), now = 1)
        val state = viewModel(firstDay = today - 30).loaded()
        assertTrue(state.pages.getValue(today - 2).unfinished)
        assertEquals(today - 30, state.firstDay)
        assertFalse(today - 5 in state.pages)
    }

    @Test
    fun `a stamped day is finished and a begun one is a dash`() = runTest(dispatcher) {
        val solved = solvedDaily(today - 1)
        store.saveSolved(solved, GameResult(solved.spec.key, PuzzleKind.Daily, solved.spec.difficulty, 60, 0, 0, 1, today - 1), now = 1)
        store.save(begun(today - 3), now = 1)
        val state = viewModel(firstDay = today - 10).loaded()
        assertTrue(state.pages.getValue(today - 1).let { it.stamped && it.clean && it.finished && !it.unfinished })
        assertTrue(state.pages.getValue(today - 3).unfinished)
    }

    @Test
    fun `pages from before the install (a restored backup) open the notebook`() = runTest(dispatcher) {
        store.save(begun(today - 10), now = 1)
        val state = viewModel(firstDay = today).loaded()
        assertEquals(today - 10, state.firstDay)
        assertFalse(state.pages.getValue(today - 10).saved == null)
    }

    @Test
    fun `a finished 9x9 page is filed under the day it was finished`() = runTest(dispatcher) {
        val finishedAt = at("2026-10-19T08:00:00Z")
        val practice = Catalog.classic(Difficulty.Gentle, 0).let { spec -> GameState.new(spec).copy(values = spec.solution).settle(finishedAt) }
        store.saveSolved(practice, GameResult(practice.spec.key, PuzzleKind.Classic, Difficulty.Gentle, 60, 0, 0, finishedAt, null), now = at("2026-10-21T08:00:00Z"))
        val state = viewModel(firstDay = today - 10).state.first { it.loaded && (today - 2) in it.pages }
        val day = state.pages.getValue(today - 2)
        assertEquals(listOf(practice.spec.key), day.others.map { it.key })
        assertTrue(day.finished)
        // The day's own page is still one of its pages: a tap asks which.
        assertEquals(2, day.pageCount)
        assertTrue(state.pages.getValue(today).others.isEmpty())
    }

    @Test
    fun `an unfinished page is filed under the day it was last written on, and is the one to continue`() = runTest(dispatcher) {
        store.save(begun(today - 4), now = at("2026-10-17T10:00:00Z"))
        val practice = GameState.new(Catalog.classic(Difficulty.Gentle, 0)).let { g ->
            val empty = g.spec.givens.indexOfFirst { it == 0 }
            g.select(empty).input(g.spec.solution[empty], true)
        }
        store.save(practice, now = at("2026-10-20T10:00:00Z"))
        // Today's own page is the Today card's, never the row to continue.
        store.save(begun(today), now = at("2026-10-21T08:30:00Z"))
        val state = viewModel(firstDay = today - 10).state.first { it.loaded && (today - 1) in it.pages }
        assertTrue(state.pages.getValue(today - 1).unfinished)
        assertEquals(FiledPage(state.pages.getValue(today - 1).others.single(), today - 1), state.resume)
        assertEquals(practice.spec.key, state.practice.saved?.key)
    }

    @Test
    fun `begun technique and past weekly pages stay in the notebook until finished`() = runTest(dispatcher) {
        // 9.0's ways back to them (the weekly card, "Practise this") are gone; they are on their day.
        val base = Catalog.classic(Difficulty.Gentle, 0)
        fun begunAs(key: String, kind: PuzzleKind) = GameState.new(base.copy(key = key, kind = kind)).let { g ->
            val empty = g.spec.givens.indexOfFirst { it == 0 }
            g.select(empty).input(g.spec.solution[empty], true)
        }
        val focus = begunAs(Catalog.focusPrefix(Technique.XWing) + 0, PuzzleKind.Focus)
        val weekly = begunAs(Catalog.weeklyKey(today - 17), PuzzleKind.Weekly)
        val blank = GameState.new(base.copy(key = Catalog.weeklyKey(today - 24), kind = PuzzleKind.Weekly))
        store.save(focus, now = at("2026-10-05T10:00:00Z"))
        store.save(weekly, now = at("2026-10-06T10:00:00Z"))
        store.save(blank, now = at("2026-10-07T10:00:00Z"))
        val state = viewModel(firstDay = today - 30).state.first { it.loaded && it.resume != null }
        assertEquals(listOf(focus.spec.key), state.pages.getValue(LocalDate.of(2026, 10, 5).toEpochDay()).others.map { it.key })
        assertEquals(listOf(weekly.spec.key), state.pages.getValue(LocalDate.of(2026, 10, 6).toEpochDay()).others.map { it.key })
        // A weekly page never written on is not kept as begun.
        assertFalse(LocalDate.of(2026, 10, 7).toEpochDay() in state.pages)
        assertEquals(weekly.spec.key, state.resume?.page?.key)
    }

    @Test
    fun `the practice card speaks of the path, not of levels`() = runTest(dispatcher) {
        val settings = FakeSettings(
            UserSettings(learner = Learner(found = mapOf(Technique.HiddenSingle to 4, Technique.NakedSingle to 3, Technique.Pointing to 3))),
        )
        // Three clean Gentle pages: practice is on Steady now.
        store.clean.value = mapOf(Difficulty.Gentle to 3)
        val state = NotebookViewModel(store, settings, clock, today).loaded()
        assertEquals(Technique.Claiming, state.practice.learning)
        assertEquals(3, state.practice.known)
    }
}
