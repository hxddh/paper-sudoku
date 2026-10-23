package com.titpea.papersudoku.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.notebook.HandbookScreen
import com.titpea.papersudoku.notebook.NotebookScreen
import com.titpea.papersudoku.page.Fixtures
import com.titpea.papersudoku.page.PageScreen
import com.titpea.papersudoku.page.PageUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The Play listing's screenshots: phone shots at 9:16 (1080 × 1920) and a tablet shot at 16:9
 * (1080 × 608), drawn from the same fixtures as the screenshot matrix. Both fit the CI device's
 * 1080px-wide screen, since only what is on screen can be captured. Saved under
 * `screenshots/store/`; CI publishes them with the captures for review. They are not compared
 * with goldens: they are artwork, refreshed when the screens change.
 */
class StoreScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Shot(val name: String, val dark: Boolean, val tablet: Boolean, val content: @Composable () -> Unit)

    private fun page(state: PageUiState): @Composable () -> Unit = {
        PageScreen(state = state, onEvent = {}, onBack = {}, onPrint = {}, onShare = {})
    }

    private val shots = listOf(
        Shot("1-page", false, false, page(Fixtures.page(game = Fixtures.practice()))),
        Shot("2-coach", false, false, page(Fixtures.page(coach = Fixtures.coach(2)))),
        Shot("3-solved", false, false, page(Fixtures.page(game = Fixtures.solved()))),
        Shot("4-notebook", false, false) { NotebookScreen(state = notebookState(), onOpenDay = {}, onOpenPractice = {}, onOpenSettings = {}) },
        Shot("5-handbook", false, false) { HandbookScreen(start = 7, techniquesMet = setOf(Technique.XWing), onBack = {}) },
        Shot("6-night", true, false, page(Fixtures.page(game = Fixtures.practice()))),
        Shot("7-tablet", false, true, page(Fixtures.page(game = Fixtures.practice()))),
    )

    @Test
    fun captureStoreShots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screenshots/store").apply { mkdirs() }
        var current by mutableStateOf(shots.first())
        composeRule.setContent {
            val shot = current
            val base = LocalDensity.current
            // Pixel sizes fixed whatever the device: 360 × 640dp at 3×, or 960 × 540dp at 1.125×.
            val scale = if (shot.tablet) 1080f / 960f else 3f
            CompositionLocalProvider(LocalDensity provides Density(scale, base.fontScale)) {
                SudokuTheme(darkTheme = shot.dark) {
                    Box(
                        Modifier
                            .requiredSize(if (shot.tablet) 960.dp else 360.dp, if (shot.tablet) 540.dp else 640.dp)
                            .background(PaperTheme.colors.background)
                            .testTag("store-frame"),
                    ) { shot.content() }
                }
            }
        }
        for (shot in shots) {
            current = shot
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1_000)
            val bitmap = composeRule.onNodeWithTag("store-frame").captureToImage().asAndroidBitmap()
            assertEquals(shot.name, 1080, bitmap.width)
            File(dir, "${shot.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
