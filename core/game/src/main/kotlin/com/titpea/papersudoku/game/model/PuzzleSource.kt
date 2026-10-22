package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where pages come from. Generation is CPU work, so it never runs on the caller's thread. */
interface PuzzleSource {
    suspend fun daily(epochDay: Long): PuzzleSpec
    suspend fun classic(difficulty: Difficulty, number: Int): PuzzleSpec
}

/** Generates every page on demand from [Catalog]. */
class GeneratingPuzzleSource(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PuzzleSource {
    override suspend fun daily(epochDay: Long): PuzzleSpec = withContext(dispatcher) { Catalog.daily(epochDay) }

    override suspend fun classic(difficulty: Difficulty, number: Int): PuzzleSpec =
        withContext(dispatcher) { Catalog.classic(difficulty, number) }
}
