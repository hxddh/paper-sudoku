package com.titpea.papersudoku.page

import androidx.compose.runtime.Immutable
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Elimination
import com.titpea.papersudoku.engine.Geometry
import com.titpea.papersudoku.engine.Placement
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.engine.UnitKind
import com.titpea.papersudoku.engine.UnitRef

/**
 * What the board draws for a Coach session.
 * Level 1 tints where to look (and marks the cell, when the note names one); level 2 adds the
 * pattern of the step being explained, every candidate crossed out so far and, at the last step,
 * the cell to fill; level 3 adds the answer in faint ink. Whatever the note names is on the board.
 */
@Immutable
data class CoachMarks(
    val focus: Set<Int> = emptySet(),
    val pattern: Set<Int> = emptySet(),
    val removals: Map<Int, Set<Int>> = emptyMap(),
    val target: Int? = null,
    val ghost: Int? = null,
) {
    companion object {
        val None = CoachMarks()
    }
}

fun coachMarks(geometry: Geometry, values: List<Int>, coach: CoachUi?): CoachMarks {
    val hint = coach?.hint ?: return CoachMarks.None
    val level = coach.level
    return when (hint) {
        is CoachHint.Mistake -> CoachMarks(
            focus = geometry.rows[geometry.row(hint.index)].toSet(),
            target = hint.index.takeIf { level >= 2 },
        )
        is CoachHint.Next -> {
            val lesson = coach.lesson ?: return CoachMarks.None
            val placement = hint.placement
            val focus = when (lesson) {
                is Elimination -> lesson.units.flatMap { geometry.cells(it).toList() }.toSet()
                is Placement -> lessonUnit(geometry, lesson).let { geometry.cells(it).toSet() }
            }
            // A naked single's note names its cell from the first step.
            val namesCell = lesson is Placement && lesson.technique == Technique.NakedSingle
            if (level == 1) return CoachMarks(focus = focus, target = placement.index.takeIf { namesCell })
            val pattern = when (lesson) {
                is Elimination -> lesson.patternCells.toSet()
                is Placement -> blockers(geometry, values, lesson)
            }
            // Candidates ruled out so far stay crossed out as the note moves on to the next step.
            val removals = coach.steps.take(coach.step + 1)
                .filterIsInstance<Elimination>()
                .flatMap { it.removals }
                .groupBy({ it.index }, { it.digit })
                .mapValues { it.value.toSet() }
            CoachMarks(
                focus = focus,
                pattern = pattern,
                removals = removals,
                // Once the note reaches the cell to fill, it is marked too.
                target = placement.index.takeIf { coach.lastStep },
                ghost = placement.digit.takeIf { level >= 3 },
            )
        }
    }
}

/** The unit a placement lesson is about; a naked single is explained from its box. */
fun lessonUnit(geometry: Geometry, placement: Placement): UnitRef =
    placement.unit ?: UnitRef(UnitKind.Box, geometry.box(placement.index))

/**
 * The givens/entries that make the single work: for a hidden single, the copies of the digit that
 * block the other cells of the unit; for a naked single, the peers that rule out every other digit.
 */
private fun blockers(geometry: Geometry, values: List<Int>, placement: Placement): Set<Int> {
    val unit = placement.unit
    return if (placement.technique == Technique.HiddenSingle && unit != null) {
        val unitCells = geometry.cells(unit).toSet()
        val empties = unitCells.filter { values[it] == 0 && it != placement.index }
        values.indices.filter { cell ->
            values[cell] == placement.digit && cell !in unitCells && empties.any { geometry.sees(cell, it) }
        }.toSet()
    } else {
        geometry.peers[placement.index].filter { values[it] != 0 }.toSet()
    }
}
