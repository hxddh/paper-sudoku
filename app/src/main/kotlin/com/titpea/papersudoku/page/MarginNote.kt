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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = text.tag,
                        style = MaterialTheme.typography.labelLarge,
                        color = ink.margin,
                        modifier = Modifier.weight(1f),
                    )
                    if (coach.hint != null) LevelSteps(coach.level)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text.body,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = ink.pen,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onEvent(PageEvent.DismissCoach) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.coach_not_now)) }
                val hint = coach.hint
                if (hint != null) {
                    Button(
                        onClick = { onEvent(if (coach.level < 3) PageEvent.CoachDeeper else PageEvent.CoachApply) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("coach-next"),
                    ) {
                        Text(
                            when {
                                coach.level == 1 -> stringResource(R.string.coach_show_why)
                                coach.level == 2 && !coach.lastStep -> stringResource(R.string.coach_next_step)
                                coach.level == 2 -> stringResource(R.string.coach_show_answer)
                                hint is CoachHint.Next -> stringResource(R.string.coach_place, hint.placement.digit)
                                else -> stringResource(R.string.coach_erase)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelSteps(level: Int) {
    val colors = MaterialTheme.colorScheme
    val labels = listOf(R.string.coach_step_look, R.string.coach_step_why, R.string.coach_step_answer)
    val progress = stringResource(R.string.coach_step_progress, level, labels.size, stringResource(labels[level - 1]))
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                Spacer(Modifier.width(3.dp))
                Text(
                    stringResource(label),
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
                    1 -> stringResource(R.string.coach_mistake_look, unitLabel(UnitRef(UnitKind.Row, row - 1)))
                    2 -> stringResource(R.string.coach_mistake_why, row, column, hint.digit)
                    else -> stringResource(R.string.coach_mistake_answer, row, column, hint.digit)
                },
            )
        }
        is CoachHint.Next -> {
            val lesson = checkNotNull(coach.lesson)
            val placement = hint.placement
            // The technique of the step on the page, with where it falls when there are several.
            val total = coach.steps.size
            val tag = if (total > 1) {
                stringResource(R.string.coach_tag_step, techniqueName(lesson.technique), coach.step + 1, total)
            } else {
                techniqueName(lesson.technique)
            }
            val cell = cellLabel(geometry, placement.index)
            // The step's anchor stays the same from Look to Why; the tag already names the technique.
            val lookTitle = if (lesson is Placement && lesson.technique == Technique.NakedSingle) {
                stringResource(R.string.coach_look_title, cell)
            } else {
                stringResource(R.string.coach_look_title, unitLabel(focusUnit(geometry, lesson)))
            }
            when (coach.level) {
                1 -> CoachText(tag = tag, title = lookTitle, body = lookTip(lesson.technique))
                2 -> CoachText(
                    tag = tag,
                    title = lookTitle,
                    body = whyText(geometry, lesson) + when {
                        lesson !is Elimination -> ""
                        // Only after the last elimination does the cell have a single answer.
                        coach.lastStep -> " " + stringResource(R.string.coach_then, cell)
                        else -> " " + stringResource(R.string.coach_more)
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

private fun focusUnit(geometry: Geometry, lesson: Step): UnitRef = when (lesson) {
    is Elimination -> lesson.units.first()
    is Placement -> lessonUnit(geometry, lesson)
}

@Composable
fun unitLabel(unit: UnitRef): String = when (unit.kind) {
    UnitKind.Row -> stringResource(R.string.unit_row, unit.index + 1)
    UnitKind.Column -> stringResource(R.string.unit_column, unit.index + 1)
    UnitKind.Box -> stringResource(R.string.unit_box, unit.index + 1)
}

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
            stringResource(R.string.why_hidden_single, unitLabel(unit), lesson.digit)
        } else {
            stringResource(R.string.why_naked_single, cellLabel(geometry, lesson.index), lesson.digit)
        }
    }
    is Elimination -> {
        val d = lesson.digits
        val u = lesson.units
        when (lesson.technique) {
            Technique.Pointing -> stringResource(R.string.why_pointing, unitLabel(u[0]), d[0], unitLabel(u[1]))
            Technique.Claiming -> stringResource(R.string.why_claiming, unitLabel(u[0]), d[0], unitLabel(u[1]))
            Technique.NakedPair -> stringResource(R.string.why_naked_pair, unitLabel(u[0]), d[0], d[1])
            Technique.HiddenPair -> stringResource(R.string.why_hidden_pair, unitLabel(u[0]), d[0], d[1])
            Technique.XWing -> stringResource(
                R.string.why_x_wing,
                unitLabel(u[0]),
                unitLabel(u[1]),
                unitLabel(u[2]),
                unitLabel(u[3]),
                d[0],
            )
            else -> techniqueSummary(lesson.technique)
        }
    }
}
