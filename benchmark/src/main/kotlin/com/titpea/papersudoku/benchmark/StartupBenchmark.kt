package com.titpea.papersudoku.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
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
 * and the frame timing of writing on the board.
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
        startActivityAndWait()
        device.wait(Until.hasObject(By.res("sudoku-board")), 5_000)
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
            startActivityAndWait()
        },
    ) {
        val board = device.wait(Until.findObject(By.res("sudoku-board")), 5_000) ?: return@measureRepeated
        val b = board.visibleBounds
        val cell = b.width() / 6
        for (i in 0 until 6) {
            device.click(b.left + cell / 2 + i * cell, b.top + cell / 2 + i * cell)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.titpea.papersudoku"
    }
}
