package com.titpea.papersudoku.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to today's page, with the shipped Baseline Profile applied (CompilationMode.Partial)
 * and without any ahead-of-time compilation (CompilationMode.None) for comparison, and the frame
 * timing of writing digits on a fresh page.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartToTodaysPage() = coldStart(CompilationMode.Partial())

    /** The same start with nothing pre-compiled: the gap to [coldStartToTodaysPage] is the profile's worth. */
    @Test
    fun coldStartWithoutCompilation() = coldStart(CompilationMode.None())

    private fun coldStart(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        device.launchToTodaysPage()
    }

    /**
     * Every iteration starts from cleared app data, so it writes on the same fresh page (today's)
     * rather than on the previous iteration's ink; the first launch after clearing deals the
     * puzzles, outside the measured block.
     */
    @Test
    fun writingOnThePage() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 3,
        setupBlock = {
            device.clearAppData()
            pressHome()
            device.launchToTodaysPage()
        },
    ) {
        // Select a cell, then write in it from the keypad: the frames measured include a digit
        // landing (with its ink animation), its candidates being tidied, and the keypad's counts
        // changing. The benchmark build is minified like a release: a missing board is a failure.
        device.writeInEmptyCells(6)
    }
}
