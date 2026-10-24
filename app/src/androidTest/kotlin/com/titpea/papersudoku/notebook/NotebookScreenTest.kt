package com.titpea.papersudoku.notebook

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.page.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
    private val finishedPractice = PageSummary.of(Fixtures.solved(GameState.new(Catalog.classic(Difficulty.Steady, 3))))
    private val left = PageSummary.of(
        GameState.new(Catalog.daily(today - 6)).let { g ->
            val empty = g.spec.givens.indexOfFirst { it == 0 }
            g.select(empty).input(g.spec.solution[empty], true)
        },
    )

    /**
     * Today begun, yesterday stamped, two days ago a stamped day with a practice page finished
     * beside it, and a day begun and left, the page to continue.
     */
    private fun state(): NotebookUiState {
        val pages = mapOf(
            today to PageEntry(today, PageSummary.of(Fixtures.fresh), null),
            today - 1 to PageEntry(today - 1, null, DayResult(today - 1, 0, 134)),
            today - 2 to PageEntry(today - 2, null, DayResult(today - 2, 3, 200), listOf(finishedPractice)),
            today - 6 to PageEntry(today - 6, left, null),
        )
        return NotebookUiState(
            today = today,
            todayPage = pages.getValue(today),
            practice = PracticeEntry(learning = Technique.NakedPair, known = 4, saved = null),
            firstDay = today - 30,
            pages = pages,
            resume = FiledPage(left, today - 6),
        )
    }

    @Test
    fun theNotebookOpensTodayTheContinueRowAndPractice() {
        val opened = mutableListOf<Long>()
        var practice = false
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(
                    state = state(),
                    onOpenDay = { opened += it },
                    onOpenPractice = { practice = true },
                    onOpenLicences = {},
                )
            }
        }
        composeRule.onNodeWithTag("notebook-today").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("notebook-continue").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("notebook-practice").performClick()
        composeRule.onNodeWithTag("notebook-practice")
            .assert(hasContentDescription("Learning: Naked pair", substring = true))
            .assert(hasContentDescription("Known: 4 of ${Technique.entries.size}", substring = true))
        assertEquals(listOf(today, today - 6), opened)
        assertTrue(practice)
    }

    @Test
    fun progressIsThePathNeverALevel() {
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(state = state(), onOpenDay = {}, onOpenPractice = {}, onOpenLicences = {})
            }
        }
        for (level in listOf("Gentle", "Steady", "Tricky", "Deep")) {
            composeRule.onAllNodes(hasText(level, substring = true) or hasContentDescription(level, substring = true), useUnmergedTree = true)
                .assertCountEquals(0)
        }
        // No separate "Next:" line into the handbook; the Coach's technique tag leads there.
        composeRule.onNodeWithTag("practice-next").assertDoesNotExist()
    }

    @Test
    fun aDayWithOnlyItsOwnPageOpensIt() {
        val opened = mutableListOf<Long>()
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(state = state(), onOpenDay = { opened += it }, onOpenPractice = {}, onOpenLicences = {})
            }
        }
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("contents-day-${today - 1}"))
        composeRule.onNodeWithTag("contents-day-${today - 1}").performClick()
        // A blank day opens its own page too: every day since the notebook began is reachable.
        composeRule.onNodeWithTag("contents-day-${today - 5}").performClick()
        composeRule.onNodeWithTag("contents-day-pages").assertDoesNotExist()
        assertEquals(listOf(today - 1, today - 5), opened)
    }

    @Test
    fun aDayWithSeveralPagesListsThemToChooseFrom() {
        val days = mutableListOf<Long>()
        val saved = mutableListOf<String>()
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(
                    state = state(),
                    onOpenDay = { days += it },
                    onOpenPractice = {},
                    onOpenLicences = {},
                    onOpenSaved = { saved += it },
                )
            }
        }
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("contents-day-${today - 2}"))
        composeRule.onNodeWithTag("contents-day-${today - 2}")
            .assert(hasContentDescription("2 pages", substring = true))
            .performClick()
        assertTrue(days.isEmpty())
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("contents-page-${finishedPractice.key}"))
        composeRule.onNodeWithTag("contents-page-${finishedPractice.key}").performClick()
        composeRule.onNodeWithTag("contents-page-daily-${today - 2}").performClick()
        assertEquals(listOf(finishedPractice.key), saved)
        assertEquals(listOf(today - 2), days)
    }

    @Test
    fun aStampedDaySaysSoInTheContents() {
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(state = state(), onOpenDay = {}, onOpenPractice = {}, onOpenLicences = {})
            }
        }
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("contents-day-${today - 1}"))
        composeRule.onNodeWithTag("contents-day-${today - 1}").assert(hasContentDescription("stamped clean", substring = true))
        composeRule.onNodeWithTag("contents-day-${today - 6}").assert(hasContentDescription("In progress", substring = true))
        composeRule.onNodeWithTag("contents-tally").assertTextEquals("3 pages stamped")
    }

    @Test
    fun typingInIsOnThePracticeCardAndTheFooterHasTheLicences() {
        val topics = mutableListOf<Int>()
        var typeIn = false
        var licences = false
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                NotebookScreen(
                    state = state(),
                    onOpenDay = {},
                    onOpenPractice = {},
                    onOpenLicences = { licences = true },
                    onOpenHandbook = { topics += it },
                    onTypeIn = { typeIn = true },
                    versionName = "11.0.0",
                )
            }
        }
        composeRule.onNodeWithTag("open-handbook").performClick()
        composeRule.onNodeWithTag("notebook-type-in").performClick()
        composeRule.onNodeWithTag("notebook-list").performScrollToNode(hasTestTag("notebook-licences"))
        composeRule.onNodeWithTag("notebook-version").assertTextEquals("Version 11.0.0")
        composeRule.onNodeWithTag("notebook-licences").performClick()
        assertEquals(listOf(0), topics)
        assertTrue(typeIn)
        assertTrue(licences)
        // The notebook keeps only its pages: no settings, no weekly card, no handbook card.
        composeRule.onNodeWithTag("open-settings").assertDoesNotExist()
        composeRule.onNodeWithTag("notebook-weekly").assertDoesNotExist()
        composeRule.onNodeWithTag("handbook").assertDoesNotExist()
    }
}
