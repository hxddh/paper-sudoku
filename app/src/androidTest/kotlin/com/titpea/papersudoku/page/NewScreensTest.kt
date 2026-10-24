package com.titpea.papersudoku.page

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.Move
import com.titpea.papersudoku.game.model.PuzzleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The 9.0 screens: reading a page back, and typing in a page of your own. */
class NewScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun enableAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
    }

    /** Two right digits, a slip, its erasure, one more right digit: five moves. */
    private fun played(spec: PuzzleSpec): List<Move> {
        val empties = spec.givens.indices.filter { spec.givens[it] == 0 }
        var s = GameState.new(spec)
        s = s.select(empties[0]).input(spec.solution[empties[0]], true)
        s = s.select(empties[1]).input(spec.solution[empties[1]], true)
        s = s.select(empties[2]).input(spec.solution[empties[2]] % spec.size + 1, true)
        s = s.erase()
        s = s.select(empties[3]).input(spec.solution[empties[3]], true)
        return s.moves
    }

    @Test
    fun reviewOpensAtTheLastMoveAndStepsBack() {
        val spec = Catalog.daily(20_000)
        val moves = played(spec)
        assertEquals(5, moves.size)
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ReviewScreen(spec = spec, moves = moves, onBack = {}) }
        }
        // Read back off the main thread: wait for it.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("review-summary").fetchSemanticsNodes().isNotEmpty()
        }
        val list = composeRule.onNodeWithTag("review")
        list.performScrollToNode(hasTestTag("review-slider"))
        composeRule.onNodeWithTag("review-slider").assertIsDisplayed()
        composeRule.onNodeWithTag("review-position")
            .assertTextEquals(context.getString(R.string.review_position, 5, 5))

        composeRule.onNodeWithTag("review-previous").performClick()
        composeRule.onNodeWithTag("review-position")
            .assertTextEquals(context.getString(R.string.review_position, 4, 5))
        list.performScrollToNode(hasTestTag("review-why"))
        composeRule.onNodeWithTag("review-why").assertTextEquals(context.getString(R.string.review_why_erased))

        list.performScrollToNode(hasTestTag("review-previous"))
        composeRule.onNodeWithTag("review-previous").performClick()
        list.performScrollToNode(hasTestTag("review-why"))
        composeRule.onNodeWithTag("review-why").assertTextEquals(context.getString(R.string.review_why_mistake))
    }

    @Test
    fun reviewOfAPageWithoutARecordSaysSo() {
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ReviewScreen(spec = Catalog.daily(20_000), moves = emptyList(), onBack = {}) }
        }
        composeRule.onNodeWithTag("review-empty").assertIsDisplayed()
    }

    @Test
    fun importRefusesTooFewDigits() {
        var imported: PuzzleSpec? = null
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ImportScreen(onBack = {}, onImported = { imported = it }) }
        }
        // One page, no scrolling: everything is on screen at once.
        fun tap(tag: String) = composeRule.onNodeWithTag(tag).performClick()
        tap("cell-0")
        tap("keypad-5")
        tap("cell-40")
        tap("keypad-3")
        tap("import-check")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("import-message").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("import-message").assertTextEquals(context.getString(R.string.import_too_few))
        assertNull(imported)
    }
}
