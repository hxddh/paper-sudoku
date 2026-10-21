package com.titpea.papersudoku.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Elimination
import com.titpea.papersudoku.engine.Geometry
import com.titpea.papersudoku.engine.Placement
import com.titpea.papersudoku.engine.Step
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.engine.UnitKind
import com.titpea.papersudoku.engine.UnitRef

/**
 * The Coach teaches in three steps — look, why, answer — and never jumps to the answer unasked.
 */
@Composable
fun CoachPanel(
    coach: CoachUi,
    geometry: Geometry,
    onEvent: (PlayEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val text = coachText(coach, geometry)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("coach-panel"),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text.tag,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                )
                if (coach.hint != null) LevelSteps(coach.level)
            }
            Spacer(Modifier.height(6.dp))
            Text(text.title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(text.body, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onEvent(PlayEvent.DismissCoach) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.coach_not_now)) }
                val hint = coach.hint
                if (hint != null) {
                    Button(
                        onClick = { onEvent(if (coach.level < 3) PlayEvent.CoachDeeper else PlayEvent.CoachApply) },
                        modifier = Modifier
                            .weight(1.5f)
                            .heightIn(min = 48.dp)
                            .testTag("coach-next"),
                    ) {
                        Text(
                            when {
                                coach.level == 1 -> stringResource(R.string.coach_show_why)
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
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val reached = i < level
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (reached) colors.primary else colors.outlineVariant, CircleShape),
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
            val lesson = hint.lesson
            val placement = hint.placement
            val tag = techniqueName(hint.technique)
            val cell = cellLabel(geometry, placement.index)
            when (coach.level) {
                1 -> CoachText(
                    tag = tag,
                    title = if (lesson is Placement && lesson.technique == Technique.NakedSingle) {
                        stringResource(R.string.coach_look_title, cell)
                    } else {
                        stringResource(R.string.coach_look_title, unitLabel(focusUnit(geometry, lesson)))
                    },
                    body = lookTip(lesson.technique),
                )
                2 -> CoachText(
                    tag = tag,
                    title = techniqueName(lesson.technique),
                    body = whyText(geometry, lesson) +
                        if (lesson is Elimination) " " + stringResource(R.string.coach_then, cell) else "",
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
        Technique.Pointing, Technique.Claiming -> R.string.look_locked
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
