package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where pages come from. Generation is CPU work, so it never runs on the caller's thread. */
interface PuzzleSource {
    suspend fun daily(epochDay: Long): PuzzleSpec
    suspend fun classic(difficulty: Difficulty, number: Int): PuzzleSpec

    /** Practice page [number] at [difficulty], dealt to need [target] (10.0: see [Catalog.practice]). */
    suspend fun practice(difficulty: Difficulty, number: Int, target: Technique?): PuzzleSpec =
        if (target == null) classic(difficulty, number) else withContext(Dispatchers.Default) { Catalog.practice(difficulty, number, target) }
}

/** Generates every page on demand from [Catalog]. */
class GeneratingPuzzleSource(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PuzzleSource {
    override suspend fun daily(epochDay: Long): PuzzleSpec = withContext(dispatcher) { Catalog.daily(epochDay) }

    override suspend fun classic(difficulty: Difficulty, number: Int): PuzzleSpec =
        withContext(dispatcher) { Catalog.classic(difficulty, number) }

    override suspend fun practice(difficulty: Difficulty, number: Int, target: Technique?): PuzzleSpec =
        withContext(dispatcher) { Catalog.practice(difficulty, number, target) }
}
