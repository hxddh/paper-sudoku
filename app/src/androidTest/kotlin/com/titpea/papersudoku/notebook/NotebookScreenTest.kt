package com.titpea.papersudoku.notebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.page.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class NotebookScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Compose's own Accessibility Test Framework checks: they run on every perform* action
    // (Espresso's checks never saw Compose content).
    @Before
    fun enableAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
    }

    private val today = Fixtures.DAY

    private fun state(): NotebookUiState {
        val pages = listOf(today, today - 1).associateWith { day ->
            PageEntry(
                epochDay = day,
                difficulty = Catalog.dailyDifficulty(day),
                saved = if (day == today) PageSummary.of(Fixtures.fresh) else null,
                result = if (day == today - 1) DayResult(day, 0, 134) else null,
            )
        }
        return NotebookUiState(
            today = today,
            todayPage = pages.getValue(today),
            practice = PracticeEntry(Difficulty.Steady, 2, null),
            firstDay = today - 30,
            stampedDays = listOf(today - 1),
            pages = pages,
            journal = listOf(
                JournalMonth(YearMonth.from(LocalDate.ofEpochDay(today - 1)), listOf(JournalRow.Day(pages.getValue(today - 1)))),
            ),
            stamped = 1,
        )
    }

    @Test
    fun theNotebookOpensTodayPracticeAndEarlierPages() {
        val opened = mutableListOf<Long>()
        var practice = false
        var settings = false
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(
                    state = state(),
                    onOpenDay = { opened += it },
                    onOpenPractice = { practice = true },
                    onOpenSettings = { settings = true },
                )
            }
        }
        composeRule.onNodeWithTag("notebook-today").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("notebook-practice").performClick()
        composeRule.onNodeWithTag("notebook-practice")
            .assert(hasContentDescription("2 more clean pages to Tricky", substring = true))
        composeRule.onNodeWithTag("open-settings").performClick()
        assertEquals(listOf(today), opened)
        assertTrue(practice)
        assertTrue(settings)
    }

    @Test
    fun aStampedDayShowsItsTime() {
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(state = state(), onOpenDay = {}, onOpenPractice = {}, onOpenSettings = {})
            }
        }
        // Earlier days come after the contents: scroll the list to them.
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("page-row-${today - 1}"))
        composeRule.onNodeWithTag("page-row-${today - 1}").assertExists()
        composeRule.onNodeWithText("Stamped · 2:14", substring = true).assertExists()
    }

    @Test
    fun blankDaysAreReachedThroughTheContentsAndTheHandbookFromTheTopBar() {
        val opened = mutableListOf<Long>()
        val topics = mutableListOf<Int>()
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(
                    state = state(),
                    onOpenDay = { opened += it },
                    onOpenPractice = {},
                    onOpenSettings = {},
                    onOpenHandbook = { topics += it },
                )
            }
        }
        // A blank day has no row of its own, but its square in the contents opens it.
        composeRule.onNodeWithTag("page-row-${today - 5}").assertDoesNotExist()
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("contents-day-${today - 5}"))
        composeRule.onNodeWithTag("contents-day-${today - 5}").performClick()
        composeRule.onNodeWithTag("open-handbook").performClick()
        assertEquals(listOf(today - 5), opened)
        assertEquals(listOf(0), topics)
        // The notebook keeps only its pages: no weekly card, no handbook card.
        composeRule.onNodeWithTag("notebook-weekly").assertDoesNotExist()
        composeRule.onNodeWithTag("handbook").assertDoesNotExist()
    }

    @Test
    fun thePracticeCardSaysWhatComesNextAndOpensItsHandbookPage() {
        val topics = mutableListOf<Int>()
        var practice = false
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(
                    state = state().copy(nextTechnique = Technique.XWing),
                    onOpenDay = {},
                    onOpenPractice = { practice = true },
                    onOpenSettings = {},
                    onOpenHandbook = { topics += it },
                )
            }
        }
        composeRule.onNodeWithTag("practice-next").assertIsDisplayed()
        composeRule.onNodeWithText("Next: X-Wing").assertIsDisplayed()
        composeRule.onNodeWithTag("practice-next").performClick()
        assertEquals(listOf(HandbookTopic.indexOf(Technique.XWing)), topics)
        // The line is its own target: tapping it does not open a practice page.
        assertFalse(practice)
    }

    @Test
    fun withNothingNextThePracticeCardHasNoNextLine() {
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(state = state(), onOpenDay = {}, onOpenPractice = {}, onOpenSettings = {})
            }
        }
        composeRule.onNodeWithTag("practice-next").assertDoesNotExist()
    }

    @Test
    fun finishedPracticePagesAreInTheJournalAndTypingInIsTheLastRow() {
        val page = PageSummary.of(Fixtures.solved(GameState.new(Catalog.classic(Difficulty.Steady, 3))))
        val base = state()
        val journal = listOf(
            JournalMonth(
                YearMonth.from(LocalDate.ofEpochDay(today)),
                listOf(
                    JournalRow.Page(page, LocalDate.ofEpochDay(today)),
                    JournalRow.Day(base.pages.getValue(today - 1)),
                ),
            ),
        )
        val opened = mutableListOf<String>()
        var typeIn = false
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(
                    state = base.copy(journal = journal),
                    onOpenDay = {},
                    onOpenPractice = {},
                    onOpenSettings = {},
                    onOpenSaved = { opened += it },
                    onTypeIn = { typeIn = true },
                )
            }
        }
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("page-row-${page.key}"))
        composeRule.onNodeWithTag("page-row-${page.key}").performClick()
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("notebook-type-in"))
        composeRule.onNodeWithTag("notebook-type-in").performClick()
        assertEquals(listOf(page.key), opened)
        assertTrue(typeIn)
    }
}
