package com.titpea.papersudoku.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Elimination
import com.titpea.papersudoku.engine.Geometry
import com.titpea.papersudoku.engine.Placement
import com.titpea.papersudoku.engine.Step
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.engine.UnitKind
import com.titpea.papersudoku.engine.UnitRef

/**
 * The Coach writes in the margin: no card, no dialog — a pen rule down the side, the note in ink,
 * and three steps (look, why, answer). It fills exactly the space the tools use, so the page
 * never moves when it appears; with very large text it grows rather than hide words.
 */
@Composable
fun MarginNote(
    coach: CoachUi,
    geometry: Geometry,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ink = PaperTheme.ink
    val text = coachText(coach, geometry)
    // As tall as the note needs (at least what the caller asks), so large text is never hidden
    // behind the actions; the rule runs the note's full height.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("coach-panel")) {
        Box(
            Modifier
                .padding(vertical = 4.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(ink.margin),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                // With large text the steps take their own line, so the tag is never squeezed.
                val stacked = LocalDensity.current.fontScale > 1.15f
                if (stacked) {
                    Text(text.tag, style = MaterialTheme.typography.labelLarge, color = ink.margin)
                    if (coach.hint != null) {
                        Spacer(Modifier.height(4.dp))
                        LevelSteps(coach)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = text.tag,
                            style = MaterialTheme.typography.labelLarge,
                            color = ink.margin,
                            modifier = Modifier.weight(1f),
                        )
                        if (coach.hint != null) LevelSteps(coach)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(2.dp))
                // The Coach's own voice: upright and quiet, not the player's pen.
                Text(
                    text.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            val hint = coach.hint
            // Asking for the answer costs the clean stamp: it is the quiet choice, and says so.
            val answerNext = hint is CoachHint.Next && coach.level == 2 && coach.lastStep
            if (answerNext) {
                Text(
                    stringResource(R.string.coach_answer_cost),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Once the Coach has said something, closing the note is going back to the page.
            val dismissLabel = stringResource(if (hint == null) R.string.coach_not_now else R.string.coach_back)
            val dismiss = @Composable { modifier: Modifier ->
                TextButton(
                    onClick = { onEvent(PageEvent.DismissCoach) },
                    modifier = modifier.heightIn(min = 48.dp),
                ) { Text(dismissLabel) }
            }
            val next = @Composable { modifier: Modifier ->
                if (hint != null) {
                    val label = when {
                        coach.level == 1 -> stringResource(R.string.coach_show_why)
                        coach.level == 2 && !coach.lastStep -> stringResource(R.string.coach_next_step)
                        coach.level == 2 -> stringResource(R.string.coach_show_answer)
                        hint is CoachHint.Next -> stringResource(R.string.coach_place, hint.placement.digit)
                        else -> stringResource(R.string.coach_erase)
                    }
                    val onClick = { onEvent(if (coach.level < 3) PageEvent.CoachDeeper else PageEvent.CoachApply) }
                    val buttonModifier = modifier.heightIn(min = 48.dp).testTag("coach-next")
                    if (answerNext) {
                        OutlinedButton(onClick = onClick, modifier = buttonModifier) { Text(label) }
                    } else {
                        Button(onClick = onClick, modifier = buttonModifier) { Text(label) }
                    }
                }
            }
            if (LocalDensity.current.fontScale > 1.3f) {
                // Very large text: one action per line, full width, the step forward first, except
                // when it is the answer: what costs the clean stamp never takes the top spot.
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (answerNext) {
                        dismiss(Modifier.fillMaxWidth())
                        next(Modifier.fillMaxWidth())
                    } else {
                        next(Modifier.fillMaxWidth())
                        dismiss(Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismiss(Modifier)
                    next(Modifier)
                }
            }
        }
    }
}

/**
 * Look, Why, Answer as pips. When Why walks through several steps, its label counts them
 * ("Why 2/3"), so there is one progress marker, not two.
 */
@Composable
private fun LevelSteps(coach: CoachUi) {
    val colors = MaterialTheme.colorScheme
    val level = coach.level
    val total = coach.steps.size
    val why = if (total > 1) {
        stringResource(R.string.coach_step_why_count, coach.step + 1, total)
    } else {
        stringResource(R.string.coach_step_why)
    }
    val labels = listOf(stringResource(R.string.coach_step_look), why, stringResource(R.string.coach_step_answer))
    // Said once, in words: "Why, step 2 of 3" (a written "2/3" is read as a fraction).
    val progress = if (level == 2 && total > 1) {
        stringResource(R.string.coach_step_why_spoken, coach.step + 1, total)
    } else {
        labels[level - 1]
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = progress },
    ) {
        labels.forEachIndexed { i, label ->
            val reached = i < level
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Filled for steps taken, hollow for steps ahead: shape as well as colour.
                Box(
                    Modifier
                        .size(7.dp)
                        .then(
                            if (reached) {
                                Modifier.background(colors.primary, CircleShape)
                            } else {
                                Modifier.border(1.dp, colors.onSurfaceVariant, CircleShape)
                            },
                        ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reached) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }
}

private data class CoachText(val tag: String, val title: String, val body: String)

@Composable
private fun coachText(coach: CoachUi, geometry: Geometry): CoachText {
    return when (val hint = coach.hint) {
        null -> CoachText(
            tag = stringResource(R.string.coach),
            title = stringResource(R.string.coach_none_title),
            body = stringResource(R.string.coach_none_body),
        )
        is CoachHint.Mistake -> {
            val row = geometry.row(hint.index) + 1
            val column = geometry.column(hint.index) + 1
            CoachText(
                tag = stringResource(R.string.coach),
                title = stringResource(R.string.coach_mistake_title),
                body = when (coach.level) {
                    1 -> stringResource(R.string.coach_mistake_look, unitLabel(geometry, UnitRef(UnitKind.Row, row - 1)))
                    2 -> stringResource(R.string.coach_mistake_why, row, column, hint.digit)
                    else -> stringResource(R.string.coach_mistake_answer, row, column, hint.digit)
                },
            )
        }
        is CoachHint.Next -> {
            val lesson = checkNotNull(coach.lesson)
            val placement = hint.placement
            // The technique of the step on the page; the Why pip counts the steps.
            val tag = techniqueName(lesson.technique)
            val cell = cellLabel(geometry, placement.index)
            // The step's anchor stays the same from Look to Why; the tag already names the technique.
            val lookTitle = if (lesson is Placement && lesson.technique == Technique.NakedSingle) {
                stringResource(R.string.coach_look_title, cell)
            } else {
                stringResource(R.string.coach_look_title, unitLabel(geometry, focusUnit(geometry, lesson)))
            }
            when (coach.level) {
                1 -> CoachText(tag = tag, title = lookTitle, body = lookTip(lesson.technique))
                2 -> CoachText(
                    tag = tag,
                    title = lookTitle,
                    body = whyText(geometry, lesson) + when {
                        lesson !is Elimination -> ""
                        // After the last elimination, say what became single, the way it is single:
                        // the cell's last candidate, or the digit's last place in its unit.
                        coach.lastStep -> stringResource(R.string.sentence_space) + thenText(geometry, placement, cell)
                        else -> stringResource(R.string.sentence_space) + stringResource(R.string.coach_more)
                    },
                )
                else -> CoachText(
                    tag = tag,
                    title = stringResource(R.string.coach_answer, placement.digit, cell),
                    body = whyText(geometry, lesson),
                )
            }
        }
    }
}

/**
 * The Coach's whole explanation of [hint] at once, for the handbook's worked examples: what to
 * look for, why, and what it leaves.
 */
@Composable
internal fun workedExample(geometry: Geometry, hint: CoachHint.Next): List<String> {
    val lesson = hint.lesson
    val placement = hint.placement
    val why = whyText(geometry, lesson)
    val then = if (lesson is Elimination) {
        stringResource(R.string.sentence_space) + thenText(geometry, placement, cellLabel(geometry, placement.index))
    } else {
        ""
    }
    return listOf(lookTip(lesson.technique), why + then)
}

@Composable
private fun thenText(geometry: Geometry, placement: Placement, cell: String): String {
    val unit = placement.unit
    return if (placement.technique == Technique.HiddenSingle && unit != null) {
        stringResource(R.string.coach_then_hidden, placement.digit, unitLabel(geometry, unit))
    } else {
        stringResource(R.string.coach_then, cell)
    }
}

private fun focusUnit(geometry: Geometry, lesson: Step): UnitRef = when (lesson) {
    is Elimination -> lesson.units.first()
    is Placement -> lessonUnit(geometry, lesson)
}

/**
 * Rows and columns by number; boxes by where they sit ("the top-right box", 右上宫), since the
 * board never shows box numbers.
 */
@Composable
fun unitLabel(geometry: Geometry, unit: UnitRef): String = when (unit.kind) {
    UnitKind.Row -> stringResource(R.string.unit_row, unit.index + 1)
    UnitKind.Column -> stringResource(R.string.unit_column, unit.index + 1)
    UnitKind.Box -> stringResource(boxName(geometry, unit.index))
}

/** The name of box [index] by its place: 3 × 3 boxes on 9×9, 3 rows × 2 columns on 6×6. */
private fun boxName(geometry: Geometry, index: Int): Int {
    val across = geometry.size / geometry.boxCols
    val down = geometry.size / geometry.boxRows
    val vertical = position(index / across, down)
    val horizontal = position(index % across, across)
    return BOX_NAMES[vertical][horizontal]
}

/** 0 top/left, 1 middle, 2 bottom/right; two boxes have no middle. */
private fun position(i: Int, count: Int): Int = if (count == 2) i * 2 else i

private val BOX_NAMES = listOf(
    listOf(R.string.box_top_left, R.string.box_top_middle, R.string.box_top_right),
    listOf(R.string.box_middle_left, R.string.box_centre, R.string.box_middle_right),
    listOf(R.string.box_bottom_left, R.string.box_bottom_middle, R.string.box_bottom_right),
)

@Composable
private fun cellLabel(geometry: Geometry, index: Int): String =
    stringResource(R.string.cell_label, geometry.row(index) + 1, geometry.column(index) + 1)

@Composable
fun techniqueName(technique: Technique): String = stringResource(
    when (technique) {
        Technique.HiddenSingle -> R.string.technique_hidden_single
        Technique.NakedSingle -> R.string.technique_naked_single
        Technique.Pointing -> R.string.technique_pointing
        Technique.Claiming -> R.string.technique_claiming
        Technique.NakedPair -> R.string.technique_naked_pair
        Technique.HiddenPair -> R.string.technique_hidden_pair
        Technique.XWing -> R.string.technique_x_wing
    },
)

@Composable
fun techniqueSummary(technique: Technique): String = stringResource(
    when (technique) {
        Technique.HiddenSingle -> R.string.technique_desc_hidden_single
        Technique.NakedSingle -> R.string.technique_desc_naked_single
        Technique.Pointing -> R.string.technique_desc_pointing
        Technique.Claiming -> R.string.technique_desc_claiming
        Technique.NakedPair -> R.string.technique_desc_naked_pair
        Technique.HiddenPair -> R.string.technique_desc_hidden_pair
        Technique.XWing -> R.string.technique_desc_x_wing
    },
)

@Composable
private fun lookTip(technique: Technique): String = stringResource(
    when (technique) {
        Technique.HiddenSingle -> R.string.look_hidden_single
        Technique.NakedSingle -> R.string.look_naked_single
        Technique.Pointing -> R.string.look_locked
        Technique.Claiming -> R.string.look_claiming
        Technique.NakedPair -> R.string.look_naked_pair
        Technique.HiddenPair -> R.string.look_hidden_pair
        Technique.XWing -> R.string.look_x_wing
    },
)

@Composable
private fun whyText(geometry: Geometry, lesson: Step): String = when (lesson) {
    is Placement -> {
        val unit = lesson.unit
        if (lesson.technique == Technique.HiddenSingle && unit != null) {
            stringResource(R.string.why_hidden_single, unitLabel(geometry, unit), lesson.digit)
        } else {
            stringResource(R.string.why_naked_single, cellLabel(geometry, lesson.index), lesson.digit)
        }
    }
    is Elimination -> {
        val d = lesson.digits
        val u = lesson.units
        when (lesson.technique) {
            Technique.Pointing -> stringResource(R.string.why_pointing, unitLabel(geometry, u[0]), d[0], unitLabel(geometry, u[1]))
            Technique.Claiming -> stringResource(R.string.why_claiming, unitLabel(geometry, u[0]), d[0], unitLabel(geometry, u[1]))
            Technique.NakedPair -> stringResource(R.string.why_naked_pair, unitLabel(geometry, u[0]), d[0], d[1])
            Technique.HiddenPair -> stringResource(R.string.why_hidden_pair, unitLabel(geometry, u[0]), d[0], d[1])
            Technique.XWing -> stringResource(
                R.string.why_x_wing,
                unitLabel(geometry, u[0]),
                unitLabel(geometry, u[1]),
                unitLabel(geometry, u[2]),
                unitLabel(geometry, u[3]),
                d[0],
            )
            else -> techniqueSummary(lesson.technique)
        }
    }
}
