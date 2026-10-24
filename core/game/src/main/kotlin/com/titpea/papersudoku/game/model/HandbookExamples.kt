package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Puzzle
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.engine.Technique

/**
 * One worked example per technique for the handbook: a real position from a practice page, where
 * the Coach's next hint teaches exactly that technique (as its only elimination, or as the
 * placement itself). The positions are fixed text, so the handbook needs no puzzle generation;
 * a test checks the engine still teaches each one.
 */
object HandbookExamples {

    data class Position(val givens: String, val values: String, val solution: String) {
        val puzzle: Puzzle get() = Puzzle(9, 3, 3, givens.digitsList(), solution.digitsList())
        val board: List<Int> get() = values.digitsList()

        /** The Coach's hint here: the technique is its lesson. */
        val hint: CoachHint.Next get() = SudokuEngine(puzzle).hint(board.toIntArray()) as CoachHint.Next
    }

    fun of(technique: Technique): Position = positions.getValue(technique)

    /** The example as a page to draw: its digits written, nothing else. */
    fun page(technique: Technique): GameState {
        val position = of(technique)
        val puzzle = position.puzzle
        val spec = PuzzleSpec(
            key = "handbook-${technique.name}",
            kind = PuzzleKind.Classic,
            difficulty = Difficulty.Tricky,
            hardest = technique,
            size = puzzle.size,
            boxRows = puzzle.boxRows,
            boxCols = puzzle.boxCols,
            givens = puzzle.givens,
            solution = puzzle.solution,
        )
        return GameState.new(spec).copy(values = position.board)
    }

    private val positions: Map<Technique, Position> = mapOf(
        Technique.HiddenSingle to Position(
            // From classic-tricky-0.
            givens = "890000001401005200000004003007009000300040006000100300500800000003500907700000035",
            values = "890000001401005200000004003007009000300040006000100300500800000003500907700000035",
            solution = "895632741431785269276914853627359418318247596954168372569873124143526987782491635",
        ),
        Technique.NakedSingle to Position(
            // From classic-tricky-0.
            givens = "890000001401005200000004003007009000300040006000100300500800000003500907700000035",
            values = "890000001431085200070014003007009000300040006000100300500800000003500907700400035",
            solution = "895632741431785269276914853627359418318247596954168372569873124143526987782491635",
        ),
        Technique.Pointing to Position(
            // From classic-tricky-2.
            givens = "500800000908203700070000000000020930706000504023090000000000010007305208000006005",
            values = "500800000908203700070000000000620930796138524023590000000002010007305298000006005",
            solution = "514879362968253741372461859185624937796138524423597186859742613647315298231986475",
        ),
        Technique.Claiming to Position(
            // From classic-tricky-21.
            givens = "005104009400000020000370000103600004050000070200001306000053000080000005500807600",
            values = "005104069400500023000370540173605004050030070200701356000053007080000005500807600",
            solution = "735124869419568723862379541173685294956432178248791356621953487387246915594817632",
        ),
        Technique.NakedPair to Position(
            // From classic-tricky-6.
            givens = "070300000300005000009600100090084300820000071005270040008001700000500003000009080",
            values = "070302000300805007089647130090184300824953671135276040058431700000508013013709080",
            solution = "471392856362815497589647132796184325824953671135276948258431769947568213613729584",
        ),
        Technique.HiddenPair to Position(
            // From classic-tricky-0.
            givens = "890000001401005200000004003007009000300040006000100300500800000003500907700000035",
            values = "890000001431085209070914003007009000300040096900100300500800000003500907700400035",
            solution = "895632741431785269276914853627359418318247596954168372569873124143526987782491635",
        ),
        Technique.XWing to Position(
            // From classic-tricky-6.
            givens = "070300000300005000009600100090084300820000071005270040008001700000500003000009080",
            values = "070302000300805007089647130090184300824953671135276040058431700000508013013709580",
            solution = "471392856362815497589647132796184325824953671135276948258431769947568213613729584",
        ),
        Technique.NakedTriple to Position(
            // From classic-deep-18.
            givens = "040030900030906400000200005700004000009000800000700001900003000008107060003050010",
            values = "842531976030976400697248135700004000009000807000700001900003000008107060003050010",
            solution = "842531976135976482697248135726814593519362847384795621971683254458127369263459718",
        ),
        Technique.HiddenTriple to Position(
            // From classic-deep-82.
            givens = "208000300000003140030090007000406700007030200004702000800010050049600000002000901",
            values = "208000300000003140430090007020406700007030204004702000800010450149605800002000901",
            solution = "218574396795263148436891527921486735657139284384752619863917452149625873572348961",
        ),
        Technique.Swordfish to Position(
            // From classic-deep-232.
            givens = "300051800109400006000007400020000008000205000700000090007500000600009503005630004",
            values = "300951800179400356050367419520090048490205030700040295037504060600009503005630004",
            solution = "346951872179428356852367419523796148498215637761843295237584961684179523915632784",
        ),
        Technique.XYWing to Position(
            // From classic-deep-5.
            givens = "008200050000010300010000267003024005020506090700930600386000040002040000090002500",
            values = "638297154274615389519483267963024005821576493745930620386050042152040006497062500",
            solution = "638297154274615389519483267963124875821576493745938621386751942152849736497362518",
        ),
    )

    private fun String.digitsList(): List<Int> = map { it - '0' }
}
