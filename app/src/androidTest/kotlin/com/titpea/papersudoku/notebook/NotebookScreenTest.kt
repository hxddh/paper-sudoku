package com.titpea.papersudoku.notebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.accessibility.AccessibilityChecks
import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.page.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NotebookScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun enableAccessibilityChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }

    private val today = Fixtures.DAY

    private fun state(): NotebookUiState {
        val past = (1..30L).map { today - it }
        val pages = (past + today).associateWith { day ->
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
            pastDays = past,
            pages = pages,
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
            .assert(hasContentDescription("Clean pages to the next level: 2", substring = true))
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
        composeRule.onNodeWithTag("page-row-${today - 1}").assertExists()
        composeRule.onNodeWithText("Stamped · 2:14", substring = true).assertExists()
    }
}
