package com.titpea.papersudoku.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to today's page, with the shipped Baseline Profile applied (CompilationMode.Partial),
 * and the frame timing of writing digits on the board.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartToTodaysPage() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        launchToTodaysPage()
    }

    @Test
    fun writingOnThePage() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = {
            pressHome()
            launchToTodaysPage()
        },
    ) {
        // The benchmark build is minified like a release: a missing board is a failure, not a skip.
        val board = checkNotNull(device.wait(Until.findObject(By.res("sudoku-board")), 5_000)) { "No board on today's page" }
        val b = board.visibleBounds
        // Today's daily is a 6×6 page: one tap per row, down the diagonal.
        val cell = b.width() / 6
        // Select a cell, then write in it from the keypad: the frames measured include a digit
        // landing, its candidates being tidied, and the keypad's counts changing.
        for (i in 0 until 6) {
            device.click(b.left + cell / 2 + i * cell, b.top + cell / 2 + i * cell)
            device.waitForIdle()
            val key = checkNotNull(device.findObject(By.res("keypad-${i + 1}"))) { "No keypad on today's page" }
            key.click()
            device.waitForIdle()
        }
    }

    /**
     * `am start -W` plus waiting for the board, rather than startActivityAndWait(): the latter
     * confirms launch through gfxinfo frame stats, which the CI emulator does not report. The
     * startup metric itself still comes from the Perfetto trace.
     */
    private fun MacrobenchmarkScope.launchToTodaysPage() {
        device.executeShellCommand("am start -W -n $PACKAGE/.MainActivity")
        check(device.wait(Until.hasObject(By.res("sudoku-board")), 10_000)) { "Today's page never showed its board" }
    }

    private companion object {
        const val PACKAGE = "com.titpea.papersudoku"
    }
}
