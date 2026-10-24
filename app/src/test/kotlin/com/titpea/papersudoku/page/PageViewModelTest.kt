package com.titpea.papersudoku.page

import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import androidx.lifecycle.SavedStateHandle
import com.titpea.papersudoku.game.model.GeneratingPuzzleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 10, 21) // a Wednesday: Gentle daily
    private var instant = Instant.parse("2026-10-21T09:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
    private var now = 1_000L
    private val store = FakeGameStore()
    private val settings = FakeSettings()
    private var dailySaves = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        generator: kotlinx.coroutines.CoroutineDispatcher = dispatcher,
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = PageViewModel(
        store, settings, clock,
        ticker = { now },
        puzzles = GeneratingPuzzleSource(generator),
        savedState = savedState,
        onDailySaved = { dailySaves++ },
    )

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun PageViewModel.game(): GameState = checkNotNull(state.value.game)

    /** Follows the Coach on a Tricky page until a placement needs two or more eliminations first. */
    private fun multiStepPosition(page: Int): Pair<GameState, Int>? {
        var game = GameState.new(Catalog.classic(Difficulty.Tricky, page))
        val engine = SudokuEngine(game.spec.puzzle)
        while (true) {
            val hint = engine.hint(game.values.toIntArray()) as? CoachHint.Next ?: return null
            if (hint.groundwork.size >= 2) return game to hint.groundwork.size
            game = game.select(hint.placement.index).input(hint.placement.digit, true)
        }
    }

    private fun PageViewModel.solveFully() {
        val spec = game().spec
        for (i in spec.givens.indices) {
            // Cells already right are left alone: tapping their digit again would clear them.
            if (spec.givens[i] == 0 && game().values[i] != spec.solution[i]) {
                onEvent(PageEvent.Tap(i))
                onEvent(PageEvent.Digit(spec.solution[i]))
            }
        }
    }

    @Test
    fun `opens today's page and keeps it only once it is begun`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val game = vm.game()
        assertEquals(Catalog.dailyKey(today.toEpochDay()), game.spec.key)
        assertEquals(Difficulty.Gentle, game.spec.difficulty)
        assertFalse(vm.state.value.loading)
        // Only looked at: the notebook and the widget still offer it as a fresh page.
        assertFalse(store.saved.containsKey(game.spec.key))
        val empty = game.spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(game.spec.solution[empty]))
        advanceUntilIdle()
        assertTrue(store.saved.containsKey(game.spec.key))
        // Undone back to blank, it is fresh again.
        vm.onEvent(PageEvent.Undo)
        advanceUntilIdle()
        assertFalse(store.saved.containsKey(game.spec.key))
        // The widget is redrawn when the page is put away or solved, not on every save.
        assertEquals(0, dailySaves)
        vm.onEvent(PageEvent.Background)
        advanceUntilIdle()
        assertEquals(1, dailySaves)
    }

    @Test
    fun `progress is autosaved and restored by a new view model`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(spec.solution[empty]))
        advanceUntilIdle()
        val restored = viewModel()
        advanceUntilIdle()
        assertEquals(spec.solution[empty], restored.game().values[empty])
    }

    @Test
    fun `the hidden clock runs only while the page is on screen`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Foreground)
        assertEquals(1_000L, vm.state.value.runningSince)
        now = 6_000
        vm.onEvent(PageEvent.Background)
        assertNull(vm.state.value.runningSince)
        assertEquals(5_000L, vm.game().elapsedMillis)
        now = 60_000
        vm.onEvent(PageEvent.Foreground)
        now = 61_000
        vm.onEvent(PageEvent.Background)
        assertEquals(6_000L, vm.game().elapsedMillis)
    }

    @Test
    fun `a digit with nothing to write into is picked up, then written by tapping`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        val digit = spec.solution[empty]
        vm.onEvent(PageEvent.Digit(digit))
        assertEquals(digit, vm.game().lockedDigit)
        vm.onEvent(PageEvent.Tap(empty))
        assertEquals(digit, vm.game().values[empty])
        val other = (digit % spec.size) + 1
        vm.onEvent(PageEvent.Digit(other)) // swap what is held
        assertEquals(other, vm.game().lockedDigit)
        vm.onEvent(PageEvent.Digit(other)) // put it down
        assertNull(vm.game().lockedDigit)
    }

    @Test
    fun `a selected empty cell takes the digit`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(spec.solution[empty]))
        assertEquals(spec.solution[empty], vm.game().values[empty])
        assertNull(vm.game().lockedDigit)
    }

    @Test
    fun `solving records exactly once`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Foreground)
        now = 91_000
        vm.solveFully()
        advanceUntilIdle()
        assertTrue(vm.game().solved)
        assertTrue(vm.state.value.justSolved)
        assertEquals(1, store.results.size)
        assertEquals(90L, store.results.single().durationSeconds)
        assertTrue(vm.game().spec.hardest in settings.flow.value.techniquesMet)
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        assertTrue(vm.game().solved)
        assertEquals(1, store.results.size)
    }

    @Test
    fun `coach goes look, why, answer and then writes the digit`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Coach)
        val coach = checkNotNull(vm.state.value.coach)
        assertEquals(1, coach.level)
        val hint = coach.hint as CoachHint.Next
        vm.onEvent(PageEvent.CoachApply)
        assertEquals(0, vm.game().values[hint.placement.index])
        vm.onEvent(PageEvent.CoachDeeper)
        vm.onEvent(PageEvent.CoachDeeper)
        vm.onEvent(PageEvent.CoachApply)
        assertEquals(hint.placement.digit, vm.game().values[hint.placement.index])
        assertNull(vm.state.value.coach)
        assertEquals(3, vm.game().coach.deepest)
    }

    @Test
    fun `coach points at a wrong digit first`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        val wrong = (spec.solution[empty] % spec.size) + 1
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(wrong))
        vm.onEvent(PageEvent.Coach)
        assertEquals(CoachHint.Mistake(empty, wrong), vm.state.value.coach?.hint)
    }

    @Test
    fun `practice starts gentle, resumes, and follows clean solves`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val first = vm.game()
        assertEquals(PuzzleKind.Classic, first.spec.kind)
        assertEquals(Difficulty.Gentle, first.spec.difficulty)
        val empty = first.spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(first.spec.solution[empty]))
        advanceUntilIdle()

        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        assertEquals(first.spec.key, vm.game().spec.key)
        assertEquals(first.spec.solution[empty], vm.game().values[empty])

        store.clean.value = mapOf(Difficulty.Gentle to 3)
        vm.onEvent(PageEvent.NextPractice)
        advanceUntilIdle()
        assertEquals(Difficulty.Steady, vm.game().spec.difficulty)
    }

    @Test
    fun `preparing the next page is not charged to the current one`() = test {
        val generator = StandardTestDispatcher(testScheduler)
        val vm = viewModel(generator)
        advanceUntilIdle()
        val daily = vm.game().spec.key
        // Begun, so it is kept with its time.
        val empty = vm.game().spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(vm.game().spec.solution[empty]))
        vm.onEvent(PageEvent.Foreground)
        now = 3_000
        vm.onEvent(PageEvent.OpenPractice)
        assertTrue(vm.state.value.loading)
        now = 50_000
        advanceUntilIdle()
        assertEquals(PuzzleKind.Classic, vm.game().spec.kind)
        assertEquals(2_000L, store.saved.getValue(daily).first.elapsedMillis)
    }

    @Test
    fun `a finished daily left open past midnight turns to the new day`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.solveFully()
        advanceUntilIdle()
        assertTrue(vm.game().solved)
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-22T07:00:00Z")
        vm.onEvent(PageEvent.Foreground)
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay() + 1), vm.game().spec.key)
    }

    /** Writes one right digit, so the page is begun. */
    private fun PageViewModel.begin() {
        val spec = game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        onEvent(PageEvent.Tap(empty))
        onEvent(PageEvent.Digit(spec.solution[empty]))
    }

    @Test
    fun `a blank daily left open past midnight turns to the new day`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-22T07:00:00Z")
        vm.onEvent(PageEvent.Foreground)
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay() + 1), vm.game().spec.key)
    }

    @Test
    fun `a blank daily restored after a night away is the new day's`() = test {
        val handle = SavedStateHandle()
        viewModel(savedState = handle)
        advanceUntilIdle()
        instant = Instant.parse("2026-10-22T07:00:00Z")
        val restored = viewModel(savedState = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay() + 1), restored.game().spec.key)
    }

    @Test
    fun `an unfinished daily stays open past midnight`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.begin()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-22T07:00:00Z")
        vm.onEvent(PageEvent.Foreground)
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay()), vm.game().spec.key)
    }

    @Test
    fun `a practice page set aside is not offered again`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val setAside = vm.game().spec
        vm.onEvent(PageEvent.NextPractice)
        advanceUntilIdle()
        val replacement = vm.game().spec
        assertEquals((setAside.number ?: 0) + 1, replacement.number)
        assertFalse(store.saved.containsKey(setAside.key))

        vm.solveFully()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val next = vm.game().spec
        assertTrue(next.key != setAside.key && next.key != replacement.key)
        assertEquals((replacement.number ?: 0) + 1, next.number)
    }

    @Test
    fun `opening yesterday's finished page after midnight keeps it open`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.solveFully()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-22T07:00:00Z")
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        vm.onEvent(PageEvent.Foreground)
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay()), vm.game().spec.key)
    }

    @Test
    fun `restarting keeps the coach on record and a solved page cannot restart`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Coach)
        vm.onEvent(PageEvent.CoachDeeper)
        vm.onEvent(PageEvent.CoachDeeper)
        vm.onEvent(PageEvent.Restart)
        assertEquals(3, vm.game().coach.deepest)
        vm.solveFully()
        advanceUntilIdle()
        assertEquals(3, store.results.single().coachLevel)
        val solved = vm.game()
        vm.onEvent(PageEvent.Restart)
        assertEquals(solved, vm.game())
    }

    @Test
    fun `a typed digit never picks a digit up`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val given = spec.givens.indexOfFirst { it != 0 }
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Select(given))
        vm.onEvent(PageEvent.TypeDigit(spec.solution[empty]))
        assertNull(vm.game().lockedDigit)
        vm.onEvent(PageEvent.Select(empty))
        vm.onEvent(PageEvent.TypeDigit(spec.solution[empty]))
        assertEquals(spec.solution[empty], vm.game().values[empty])
    }

    @Test
    fun `after process death the open page comes back`() = test {
        val handle = SavedStateHandle()
        val vm = viewModel(savedState = handle)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val practice = vm.game().spec.key
        val restored = viewModel(savedState = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        advanceUntilIdle()
        assertEquals(practice, restored.game().spec.key)
    }

    @Test
    fun `the stamp lands once`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.solveFully()
        advanceUntilIdle()
        assertTrue(vm.state.value.justSolved)
        vm.onEvent(PageEvent.StampLanded)
        assertFalse(vm.state.value.justSolved)
        assertEquals(1, dailySaves)
    }

    @Test
    fun `solving an earlier day does not redraw today's widget`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay() - 3))
        advanceUntilIdle()
        vm.solveFully()
        advanceUntilIdle()
        assertTrue(vm.game().solved)
        assertEquals(0, dailySaves)
    }

    @Test
    fun `reopening the page in memory while another loads keeps its moves`() = test {
        val generator = StandardTestDispatcher(testScheduler)
        val vm = viewModel(generator)
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(spec.solution[empty]))
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay() - 40)) // not generated yet: stays loading
        assertTrue(vm.state.value.loading)
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        assertFalse(vm.state.value.loading)
        advanceUntilIdle()
        assertEquals(spec.key, vm.game().spec.key)
        assertEquals(spec.solution[empty], vm.game().values[empty])
        assertEquals(spec.solution[empty], store.saved.getValue(spec.key).first.values[empty])
    }

    @Test
    fun `practice asked for while another page loads stays on practice`() = test {
        val generator = StandardTestDispatcher(testScheduler)
        val vm = viewModel(generator)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val practice = vm.game().spec.key
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay() - 40))
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        assertEquals(practice, vm.game().spec.key)
    }

    @Test
    fun `a solved practice page comes back after process death`() = test {
        val handle = SavedStateHandle()
        val vm = viewModel(savedState = handle)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        vm.solveFully()
        advanceUntilIdle()
        val solved = vm.game().spec.key
        val restored = viewModel(savedState = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        advanceUntilIdle()
        assertEquals(solved, restored.game().spec.key)
        assertTrue(restored.game().solved)
    }

    @Test
    fun `being shown a wrong digit keeps the page clean`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit((spec.solution[empty] % spec.size) + 1))
        vm.onEvent(PageEvent.Coach)
        assertTrue(vm.state.value.coach?.hint is CoachHint.Mistake)
        vm.onEvent(PageEvent.CoachDeeper)
        vm.onEvent(PageEvent.CoachDeeper)
        assertEquals(3, vm.state.value.coach?.level)
        assertEquals(2, vm.game().coach.deepest)
    }

    @Test
    fun `a page set aside is not revived by a save on the way out`() = test {
        val generator = StandardTestDispatcher(testScheduler)
        val vm = viewModel(generator)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val setAside = vm.game().spec
        val empty = setAside.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(setAside.solution[empty]))
        vm.onEvent(PageEvent.NextPractice)
        vm.onEvent(PageEvent.Background)
        advanceUntilIdle()
        assertFalse(store.saved.containsKey(setAside.key))
        assertTrue(vm.game().spec.key != setAside.key)
    }

    @Test
    fun `a practice page taken back before the next is dealt keeps saving`() = test {
        val generator = StandardTestDispatcher(testScheduler)
        val vm = viewModel(generator)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val setAside = vm.game().spec
        vm.onEvent(PageEvent.NextPractice)
        // Practice tapped while the next page is still being dealt: the same page carries on.
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        assertEquals(setAside.key, vm.game().spec.key)
        val empty = setAside.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(setAside.solution[empty]))
        vm.onEvent(PageEvent.Background)
        advanceUntilIdle()
        assertEquals(setAside.solution[empty], store.saved.getValue(setAside.key).first.values[empty])
    }

    @Test
    fun `a practice page set aside stays aside when another page opens during the deal`() = test {
        val generator = StandardTestDispatcher(testScheduler)
        val vm = viewModel(generator)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val setAside = vm.game().spec
        vm.onEvent(PageEvent.NextPractice)
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay()), vm.game().spec.key)
        assertFalse(store.saved.containsKey(setAside.key))
        val offered = checkNotNull(store.latestUnsolvedPracticeKey())
        assertEquals(Catalog.classicKey(setAside.difficulty, (setAside.number ?: 0) + 1), offered)
    }

    @Test
    fun `the Coach explains every elimination before giving the answer`() = test {
        val (game, needed) = (0 until 20).firstNotNullOf { multiStepPosition(it) }
        store.save(game, 1)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        assertEquals(game.spec.key, vm.game().spec.key)
        vm.onEvent(PageEvent.Coach)
        vm.onEvent(PageEvent.CoachDeeper)
        repeat(needed - 1) { i ->
            assertEquals(2, vm.state.value.coach?.level)
            assertEquals(i, vm.state.value.coach?.step)
            assertFalse(checkNotNull(vm.state.value.coach).lastStep)
            vm.onEvent(PageEvent.CoachDeeper)
        }
        val coach = checkNotNull(vm.state.value.coach)
        assertEquals(2, coach.level)
        assertTrue(coach.lastStep)
        // Walking through the steps is still Why: the page stays clean until the answer.
        assertEquals(2, vm.game().coach.deepest)
        vm.onEvent(PageEvent.CoachDeeper)
        assertEquals(3, vm.state.value.coach?.level)
    }

    @Test
    fun `a solved practice page says where practice stands and records every technique`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val spec = vm.game().spec
        store.clean.value = mapOf(Difficulty.Gentle to 1)
        vm.solveFully()
        advanceUntilIdle()
        val outcome = checkNotNull(vm.state.value.practiceAfter)
        assertEquals(Difficulty.Gentle, outcome.level)
        assertEquals(2, outcome.remaining)
        assertFalse(outcome.leveledUp)
        assertTrue(settings.flow.value.techniquesMet.containsAll(spec.techniquesUsed))
        // Opening another page forgets it.
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        assertNull(vm.state.value.practiceAfter)
    }

    @Test
    fun `leaving a just-solved page ends the stamp's landing`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.solveFully()
        assertTrue(vm.state.value.justSolved)
        vm.onEvent(PageEvent.Background)
        advanceUntilIdle()
        assertFalse(vm.state.value.justSolved)
    }

    @Test
    fun `the how-to line goes once a first digit is written`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.settings.wroteFirstDigit)
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(spec.solution[empty]))
        advanceUntilIdle()
        assertTrue(settings.flow.value.wroteFirstDigit)
    }

    @Test
    fun `a daily solved a day late still turns to the new day`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.begin()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-22T09:00:00Z")
        vm.onEvent(PageEvent.Foreground)
        vm.solveFully()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-23T09:00:00Z")
        vm.onEvent(PageEvent.Foreground)
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay() + 2), vm.game().spec.key)
    }

    @Test
    fun `a daily finished a day late says today's page is waiting`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.begin()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-22T09:00:00Z")
        vm.onEvent(PageEvent.Foreground)
        vm.solveFully()
        advanceUntilIdle()
        assertEquals(today.toEpochDay() + 1, vm.state.value.today)
        assertTrue(vm.game().spec.epochDay!! < vm.state.value.today)
        assertTrue(vm.state.value.todayWaiting)
        // Reopened later, the page no longer says so.
        vm.onEvent(PageEvent.OpenToday)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        assertFalse(vm.state.value.todayWaiting)
    }

    @Test
    fun `a clean page counts once towards each technique it needed`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val used = vm.game().spec.techniquesUsed
        vm.solveFully()
        advanceUntilIdle()
        assertEquals(used.associateWith { 1 }, settings.flow.value.cleanPages)
    }

    @Test
    fun `practising a technique deals its own pages, outside practice`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.PractiseTechnique(com.titpea.papersudoku.engine.Technique.Pointing))
        advanceUntilIdle()
        val page = vm.game().spec
        assertEquals(com.titpea.papersudoku.game.model.PuzzleKind.Focus, page.kind)
        assertEquals(Catalog.focusPrefix(com.titpea.papersudoku.engine.Technique.Pointing) + 0, page.key)
        // Asked again while it is unfinished: the same page.
        vm.onEvent(PageEvent.OpenToday)
        advanceUntilIdle()
        vm.onEvent(PageEvent.PractiseTechnique(com.titpea.papersudoku.engine.Technique.Pointing))
        advanceUntilIdle()
        assertEquals(page.key, vm.game().spec.key)
        assertEquals(null, store.latestUnsolvedPracticeKey())
    }

    @Test
    fun `this week's page opens and comes back after process death`() = test {
        val handle = SavedStateHandle()
        val vm = viewModel(savedState = handle)
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenWeekly)
        advanceUntilIdle()
        assertEquals(Catalog.weeklyKey(Catalog.weekOf(today.toEpochDay())), vm.game().spec.key)
        val restored = viewModel(savedState = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        advanceUntilIdle()
        assertEquals(Catalog.weeklyKey(Catalog.weekOf(today.toEpochDay())), restored.game().spec.key)
    }

    @Test
    fun `pages saved only for being opened are forgotten`() = test {
        store.save(GameState.new(Catalog.daily(today.toEpochDay() - 3)), 0)
        val begun = GameState.new(Catalog.daily(today.toEpochDay() - 2)).let { g ->
            val empty = g.spec.givens.indexOfFirst { it == 0 }
            g.select(empty).input(g.spec.solution[empty], true)
        }
        store.save(begun, 0)
        viewModel()
        advanceUntilIdle()
        assertFalse(store.saved.containsKey(Catalog.dailyKey(today.toEpochDay() - 3)))
        assertTrue(store.saved.containsKey(begun.spec.key))
    }
}
