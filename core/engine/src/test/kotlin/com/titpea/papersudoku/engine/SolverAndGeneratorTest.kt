package com.titpea.papersudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverAndGeneratorTest {

    @Test
    fun `backtracking counts unique and ambiguous positions`() {
        val solver = BacktrackingSolver(Geometry.Classic)
        assertEquals(1, solver.countSolutions(Fixtures.classic.givens.toIntArray()))
        assertEquals(2, solver.countSolutions(IntArray(81)))
        val broken = Fixtures.classic.givens.toIntArray().also { it[2] = 5 } // duplicates the 5 in row 0
        assertEquals(0, solver.countSolutions(broken))
    }

    @Test
    fun `random grids are valid and seed-stable`() {
        for (g in listOf(Geometry.Mini, Geometry.Classic)) {
            val a = BacktrackingSolver(g).randomGrid(SplitMix64(7))
            val b = BacktrackingSolver(g).randomGrid(SplitMix64(7))
            assertEquals(a.toList(), b.toList())
            assertTrue(SudokuEngine(puzzleOf(g, a, a)).conflicts(a).isEmpty())
            assertTrue(a.none { it == 0 })
        }
    }

    @Test
    fun `generated puzzles are unique, graded as requested, and deterministic`() {
        val cases = listOf(
            Geometry.Mini to Difficulty.Gentle,
            Geometry.Mini to Difficulty.Steady,
            Geometry.Classic to Difficulty.Gentle,
            Geometry.Classic to Difficulty.Steady,
            Geometry.Classic to Difficulty.Tricky,
        )
        for ((g, difficulty) in cases) {
            val generator = PuzzleGenerator(g)
            for (seed in 1L..6L) {
                val generated = generator.generate(seed, difficulty)
                val givens = generated.puzzle.givens.toIntArray()
                assertEquals("unique ${g.size} $difficulty $seed", 1, BacktrackingSolver(g).countSolutions(givens))
                assertEquals("grade ${g.size} $difficulty $seed", difficulty, generated.grade.difficulty)
                assertEquals(generated, generator.generate(seed, difficulty))
                assertSymmetric(g, givens)
            }
        }
    }

    @Test
    fun `every logical step agrees with the solution`() {
        for (g in listOf(Geometry.Mini, Geometry.Classic)) {
            val generator = PuzzleGenerator(g)
            val solver = LogicalSolver(g)
            val seen = mutableSetOf<Technique>()
            for (seed in 100L..140L) {
                val difficulty = Difficulty.entries[(seed % 3).toInt()].let {
                    if (g == Geometry.Mini && it == Difficulty.Tricky) Difficulty.Steady else it
                }
                val puzzle = generator.generate(seed, difficulty).puzzle
                val board = Board.from(g, puzzle.givens.toIntArray())
                while (!board.isSolved) {
                    val step = assertNotNull(solver.nextStep(board)).let { solver.nextStep(board)!! }
                    seen += step.technique
                    when (step) {
                        is Placement -> assertEquals(puzzle.solution[step.index], step.digit)
                        is Elimination -> step.removals.forEach {
                            assertTrue("$step removes the answer", puzzle.solution[it.index] != it.digit)
                        }
                    }
                    board.apply(step)
                }
            }
            if (g == Geometry.Classic) {
                assertTrue("techniques seen: $seen", seen.containsAll(listOf(Technique.HiddenSingle, Technique.NakedSingle)))
                assertTrue("an elimination technique was exercised: $seen", seen.any { it.tier >= 2 })
            }
        }
    }

    private fun assertSymmetric(g: Geometry, givens: IntArray) {
        for (i in givens.indices) {
            assertEquals(givens[i] == 0, givens[g.cellCount - 1 - i] == 0)
        }
    }

    private fun puzzleOf(g: Geometry, givens: IntArray, solution: IntArray) =
        Puzzle(g.size, g.boxRows, g.boxCols, givens.toList(), solution.toList())
}
