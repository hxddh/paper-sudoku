package com.titpea.papersudoku.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.game.model.Mastery
import com.titpea.papersudoku.game.model.Learner
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.designsystem.SheetShape
import com.titpea.papersudoku.designsystem.Space
import com.titpea.papersudoku.designsystem.hairline
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Elimination
import com.titpea.papersudoku.engine.Geometry
import com.titpea.papersudoku.engine.Placement
import com.titpea.papersudoku.engine.Step
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.engine.UnitKind
import com.titpea.papersudoku.engine.UnitRef

/** The note's pen rule hangs in the page margin; its words sit on the page's one text edge. */
private val RuleWidth = 2.dp
private val RuleGap = Space.TextEdge - Space.S - RuleWidth

/**
 * The Coach writes in the margin, above the keys, which stay in use: no card, no dialog, a pen rule
 * in the margin and the note in ink. At rest it is the technique and one line, where to look;
 * the line opens into why (step by step, when a placement needs eliminations first), and only then
 * is the answer offered, as a small text action that says what it costs. Its marks stay on the
 * board until the step is done; × (or back) puts it away.
 *
 * Laid out from the screen's edge: the rule sits 8dp in, the words at 20dp.
 */
@Composable
fun MarginNote(
    coach: CoachUi,
    geometry: Geometry,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** What the player has shown they know: the Coach is brief about it, and introduces the rest. */
    learner: Learner = Learner(),
    /** The first Coach ever: one line on how the note behaves. */
    firstTime: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val ink = PaperTheme.ink
    val text = coachText(coach, geometry, learner)
    val hint = coach.hint
    // Look opens into Why; once open, the line folds it away again (the Coach has still said it).
    val opens = hint is CoachHint.Next && coach.level == 1
    var folded by rememberSaveable(hint, coach.level, coach.step) { mutableStateOf(false) }
    val foldable = hint is CoachHint.Next && coach.level >= 2
    val showBody = text.body.isNotEmpty() && !(foldable && folded)
    val large = LocalDensity.current.fontScale > 1.3f
    // As tall as the note needs; the rule runs the note's full height.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("coach-panel")) {
        Spacer(Modifier.width(Space.S))
        Box(
            Modifier
                .padding(vertical = Space.XS)
                .width(RuleWidth)
                .fillMaxHeight()
                .background(ink.margin),
        )
        Spacer(Modifier.width(RuleGap))
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().padding(end = Space.L)) {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    // The technique, in the Coach's red, and (walking through Why) which step.
                    Row(Modifier.padding(end = Space.XXL), verticalAlignment = Alignment.CenterVertically) {
                        Text(text.tag, style = MaterialTheme.typography.labelMedium, color = ink.margin)
                        val total = coach.steps.size
                        if (coach.level == 2 && total > 1) {
                            val spoken = stringResource(R.string.coach_step_why_spoken, coach.step + 1, total)
                            Text(
                                DOT + stringResource(R.string.coach_step_why_count, coach.step + 1, total),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.XS))
                    // The one line at rest: where to look. It opens into why.
                    val lineModifier = when {
                        opens -> Modifier.clickable(
                            onClickLabel = stringResource(R.string.coach_show_why),
                            role = Role.Button,
                        ) { onEvent(PageEvent.CoachDeeper) }
                        foldable -> Modifier.clickable(role = Role.Button) { folded = !folded }
                        else -> Modifier
                    }
                    Row(
                        lineModifier.fillMaxWidth().heightIn(min = 48.dp).testTag("coach-why"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        if (opens || foldable) {
                            // Down to open, up to fold; the chevron mirrors in right-to-left text.
                            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                            val down = if (rtl) -90f else 90f
                            Icon(
                                painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).rotate(if (opens || folded) down else -down),
                            )
                        }
                    }
                    // The Coach's own voice: upright, in ink, not the player's pen.
                    if (showBody) {
                        Text(
                            text.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            modifier = Modifier.padding(top = Space.XS),
                        )
                    }
                }
                CoachActions(coach, onEvent, large)
                if (firstTime) {
                    Text(
                        stringResource(R.string.first_hint_coach),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.XS).testTag("first-hint"),
                    )
                }
            }
            // Put away: also back, or Esc. Its target reaches above the note, not into the words.
            IconButton(
                onClick = { onEvent(PageEvent.DismissCoach) },
                modifier = Modifier.align(Alignment.TopEnd).offset(y = -Space.M).testTag("coach-close"),
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.coach_close),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * At most one step forward: the next step of Why (an ink text button), the answer once Why is
 * done (a small text action, with its cost said beside it), or, with the answer shown, writing it
 * (the one hairline-outlined button). The player can always write it with the keys instead.
 */
@Composable
private fun CoachActions(coach: CoachUi, onEvent: (PageEvent) -> Unit, large: Boolean) {
    val hint = coach.hint ?: return
    val colors = MaterialTheme.colorScheme
    val deeper = { onEvent(PageEvent.CoachDeeper) }
    val buttonModifier = Modifier.heightIn(min = 48.dp).testTag("coach-next")
    val ink = ButtonDefaults.textButtonColors(contentColor = colors.onSurface)
    val padding = PaddingValues(horizontal = Space.S)
    when {
        coach.level == 1 && hint !is CoachHint.Next -> TextButton(
            onClick = deeper,
            colors = ink,
            shape = SheetShape,
            contentPadding = padding,
            modifier = buttonModifier.offset(x = -Space.S),
        ) { Text(stringResource(R.string.coach_show_why)) }
        coach.level == 1 -> Unit
        coach.level == 2 && hint is CoachHint.Next && !coach.lastStep -> TextButton(
            onClick = deeper,
            colors = ink,
            shape = SheetShape,
            contentPadding = padding,
            modifier = buttonModifier.offset(x = -Space.S),
        ) { Text(stringResource(R.string.coach_next_step)) }
        // Asking for the answer costs the clean stamp: the quiet choice, and it says so. Being
        // shown a wrong digit or a missing mark costs nothing.
        coach.level == 2 -> {
            val action = @Composable {
                TextButton(
                    onClick = deeper,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
                    shape = SheetShape,
                    contentPadding = padding,
                    modifier = buttonModifier.offset(x = -Space.S),
                ) { Text(stringResource(R.string.coach_show_answer), style = MaterialTheme.typography.bodyMedium) }
            }
            if (hint is CoachHint.Next) {
                val cost = @Composable { modifier: Modifier ->
                    Text(
                        stringResource(R.string.coach_answer_cost),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = modifier,
                    )
                }
                if (large) {
                    Column { action(); cost(Modifier) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) { action(); cost(Modifier.weight(1f)) }
                }
            } else {
                action()
            }
        }
        else -> OutlinedButton(
            onClick = { onEvent(PageEvent.CoachApply) },
            shape = SheetShape,
            border = hairline(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
            modifier = buttonModifier.padding(top = Space.S),
        ) {
            Text(
                when (hint) {
                    is CoachHint.Next -> stringResource(R.string.coach_place, hint.placement.digit)
                    is CoachHint.MissingMark -> stringResource(R.string.coach_add_mark)
                    is CoachHint.Mistake -> stringResource(R.string.coach_erase)
                },
            )
        }
    }
}

private data class CoachText(val tag: String, val title: String, val body: String)

@Composable
private fun coachText(coach: CoachUi, geometry: Geometry, learner: Learner): CoachText {
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
        is CoachHint.MissingMark -> {
            val row = geometry.row(hint.index) + 1
            val column = geometry.column(hint.index) + 1
            CoachText(
                tag = stringResource(R.string.coach),
                title = stringResource(R.string.coach_missing_mark_title),
                body = when (coach.level) {
                    1 -> stringResource(R.string.coach_missing_mark_look, unitLabel(geometry, UnitRef(UnitKind.Row, row - 1)))
                    2 -> stringResource(R.string.coach_missing_mark_why, row, column, hint.digit)
                    else -> stringResource(R.string.coach_missing_mark_answer, row, column, hint.digit)
                },
            )
        }
        is CoachHint.Next -> {
            val lesson = checkNotNull(coach.lesson)
            val technique = lesson.technique
            // Known (found on enough pages): the title and the tag say it all. Never found nor
            // explained: Why starts with what the technique is, as the handbook does.
            val known = learner.found(technique) >= Mastery.MASTERED
            val first = technique.tier >= 2 && learner.found(technique) == 0 && (learner.helped[technique] ?: 0) == 0
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
            // At rest the note is one line, where to look; what to look for opens with the why.
            val opening = when {
                coach.step != 0 -> ""
                first -> techniqueSummary(technique) + stringResource(R.string.sentence_space)
                !known -> lookTip(lesson.technique) + stringResource(R.string.sentence_space)
                else -> ""
            }
            when (coach.level) {
                1 -> CoachText(tag = tag, title = lookTitle, body = "")
                2 -> CoachText(
                    tag = tag,
                    title = lookTitle,
                    body = opening +
                        whyText(geometry, lesson) + when {
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
internal fun boxName(geometry: Geometry, index: Int): Int {
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
        Technique.NakedTriple -> R.string.technique_naked_triple
        Technique.HiddenTriple -> R.string.technique_hidden_triple
        Technique.Swordfish -> R.string.technique_swordfish
        Technique.XYWing -> R.string.technique_xy_wing
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
        Technique.NakedTriple -> R.string.technique_desc_naked_triple
        Technique.HiddenTriple -> R.string.technique_desc_hidden_triple
        Technique.Swordfish -> R.string.technique_desc_swordfish
        Technique.XYWing -> R.string.technique_desc_xy_wing
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
        Technique.NakedTriple -> R.string.look_naked_triple
        Technique.HiddenTriple -> R.string.look_hidden_triple
        Technique.Swordfish -> R.string.look_swordfish
        Technique.XYWing -> R.string.look_xy_wing
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
            Technique.NakedTriple -> stringResource(R.string.why_naked_triple, unitLabel(geometry, u[0]), d[0], d[1], d[2])
            Technique.HiddenTriple -> stringResource(R.string.why_hidden_triple, unitLabel(geometry, u[0]), d[0], d[1], d[2])
            Technique.Swordfish -> stringResource(
                R.string.why_swordfish,
                unitLabel(geometry, u[0]),
                unitLabel(geometry, u[1]),
                unitLabel(geometry, u[2]),
                unitLabel(geometry, u[3]),
                unitLabel(geometry, u[4]),
                unitLabel(geometry, u[5]),
                d[0],
            )
            // Digits are the third digit first, then the pivot's two; cells are the pivot, then the pincers.
            Technique.XYWing -> stringResource(
                R.string.why_xy_wing,
                cellLabel(geometry, lesson.patternCells[0]),
                cellLabel(geometry, lesson.patternCells[1]),
                cellLabel(geometry, lesson.patternCells[2]),
                d[1],
                d[2],
                d[0],
            )
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
