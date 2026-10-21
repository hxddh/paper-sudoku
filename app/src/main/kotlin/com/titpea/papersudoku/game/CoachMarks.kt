package com.titpea.papersudoku.game

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
 * Level 1 tints where to look; level 2 adds the pattern, crossed-out candidates and (for singles)
 * the cell; level 3 adds the answer in faint ink.
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
            val lesson = hint.lesson
            val placement = hint.placement
            val focus = when (lesson) {
                is Elimination -> lesson.units.flatMap { geometry.cells(it).toList() }.toSet()
                is Placement -> lessonUnit(geometry, lesson).let { geometry.cells(it).toSet() }
            }
            if (level == 1) return CoachMarks(focus = focus)
            val pattern = when (lesson) {
                is Elimination -> lesson.patternCells.toSet()
                is Placement -> blockers(geometry, values, lesson)
            }
            val removals = (lesson as? Elimination)?.removals
                ?.groupBy({ it.index }, { it.digit })
                ?.mapValues { it.value.toSet() }
                .orEmpty()
            CoachMarks(
                focus = focus,
                pattern = pattern,
                removals = removals,
                target = placement.index.takeIf { lesson is Placement || level >= 3 },
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
