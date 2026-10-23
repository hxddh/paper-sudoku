package com.titpea.papersudoku.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Renders the key screens across themes, sizes and font scale and saves them as PNGs under the
 * app's external files dir (`screenshots/`), which CI pulls and uploads for review. Each capture
 * must render a non-blank image; layout assertions live in the other UI tests.
 */
class ScreenshotMatrixTest {

    @get:Rule
    val composeRule = createComposeRule()

    private enum class Frame { Phone, Wide, LargeText }

    private data class Shot(val name: String, val dark: Boolean, val frame: Frame, val content: @Composable () -> Unit)

    private fun page(state: PageUiState): @Composable () -> Unit = {
        PageScreen(state = state, onEvent = {}, onBack = {}, onPrint = {}, onShare = {})
    }

    private val notebook: @Composable () -> Unit = { NotebookScreen(state = notebookState(), onOpenDay = {}, onOpenPractice = {}, onOpenSettings = {}) }

    private val handbook: @Composable () -> Unit = {
        HandbookScreen(start = 7, techniquesMet = setOf(Technique.HiddenSingle, Technique.XWing), onBack = {})
    }

    private val shots: List<Shot> = buildList {
        for (dark in listOf(false, true)) {
            val t = if (dark) "night" else "paper"
            add(Shot("$t-page-fresh", dark, Frame.Phone, page(Fixtures.page())))
            add(Shot("$t-page-coach-look", dark, Frame.Phone, page(Fixtures.page(coach = Fixtures.coach(1)))))
            add(Shot("$t-page-coach-why", dark, Frame.Phone, page(Fixtures.page(coach = Fixtures.coach(2)))))
            add(Shot("$t-page-solved", dark, Frame.Phone, page(Fixtures.page(game = Fixtures.solved()))))
            add(Shot("$t-practice", dark, Frame.Phone, page(Fixtures.page(game = Fixtures.practice()))))
            add(Shot("$t-notebook", dark, Frame.Phone, notebook))
            add(Shot("$t-wide-practice", dark, Frame.Wide, page(Fixtures.page(game = Fixtures.practice()))))
            add(Shot("$t-handbook", dark, Frame.Phone, handbook))
        }
        add(Shot("paper-large-text-page", false, Frame.LargeText, page(Fixtures.page(coach = Fixtures.coach(2)))))
        add(Shot("paper-large-text-notebook", false, Frame.LargeText, notebook))
    }

    @Test
    fun captureMatrix() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        var current by mutableStateOf(shots.first())
        composeRule.setContent {
            val shot = current
            val base = LocalDensity.current
            val density = when (shot.frame) {
                Frame.Phone -> base
                // A 1000×600dp landscape window drawn at a smaller scale so it fits the device.
                Frame.Wide -> Density(base.density * 0.4f, base.fontScale)
                Frame.LargeText -> Density(base.density, 2f)
            }
            CompositionLocalProvider(LocalDensity provides density) {
                SudokuTheme(darkTheme = shot.dark) {
                    Box(
                        (if (shot.frame == Frame.Wide) Modifier.requiredSize(1000.dp, 600.dp) else Modifier.fillMaxSize())
                            .background(PaperTheme.colors.background),
                    ) { shot.content() }
                }
            }
        }
        for (shot in shots) {
            current = shot
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1_000)
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            assertTrue("${shot.name} rendered blank", bitmap.hasVisibleContent())
            File(dir, "${shot.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun Bitmap.hasVisibleContent(): Boolean {
        val first = getPixel(width / 2, height / 2)
        for (y in 0 until height step 24) for (x in 0 until width step 24) if (getPixel(x, y) != first) return true
        return false
    }
}
