package com.titpea.papersudoku.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.Move
import com.titpea.papersudoku.game.model.PuzzleSpec
import com.titpea.papersudoku.game.model.Review
import com.titpea.papersudoku.game.model.ReviewStep
import com.titpea.papersudoku.settings.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Reads a page back from its move record: the board after each move, the move's cell marked, and
 * what justified the digit there and then. Opens at the last move. The reading is worked out off
 * the main thread; until it is ready the page stays blank rather than flashing a half state.
 */
@Composable
fun ReviewScreen(spec: PuzzleSpec, moves: List<Move>, onBack: () -> Unit) {
    val steps by produceState<List<ReviewStep>?>(null, spec, moves) {
        value = withContext(Dispatchers.Default) { Review.of(spec, moves) }
    }
    var index by rememberSaveable(spec.key, moves.size) { mutableIntStateOf(moves.lastIndex) }
    val ready = steps
    val at = if (ready.isNullOrEmpty()) 0 else index.coerceIn(0, ready.lastIndex)
    SettingsScaffold(
        title = stringResource(R.string.review_title),
        onBack = onBack,
        tag = "review",
    ) {
        if (moves.isEmpty()) {
            item(key = "empty") {
                ReviewColumn("review-empty") { Body(stringResource(R.string.review_empty)) }
            }
            return@SettingsScaffold
        }
        if (ready.isNullOrEmpty()) return@SettingsScaffold
        // The board first, with the move under it: the page, the slider and the note are seen together.
        item(key = "board") {
            val step = ready[at]
            // The moved cell is selected, in the player's blue; a slip shows as a clash would.
            // Terracotta is the Coach's, so it is not used for the player's own moves.
            val game = remember(spec, step) { GameState.new(spec).copy(values = step.values, selected = step.move.cell) }
            ReviewColumn("review-board") {
                Board(
                    game = game,
                    conflicts = if (step.kind == ReviewStep.Kind.Mistake) setOf(step.move.cell) else emptySet(),
                    marks = CoachMarks.None,
                    onTap = {},
                    onCellAction = { _, _ -> },
                    interactive = false,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally),
                )
            }
        }
        item(key = "controls") {
            Controls(at = at, count = ready.size, onMove = { index = it })
        }
        item(key = "why") {
            val before = if (at > 0) ready[at - 1].values else spec.givens
            StepNote(spec, ready[at], before[ready[at].move.cell])
        }
        item(key = "summary") { Summary(ready) }
    }
}

@Composable
private fun ReviewColumn(tag: String, live: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            // The note under the slider is read out as each move comes up.
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Counts, in calm words: which techniques placed the player's digits, the slips, the Coach. */
@Composable
private fun Summary(steps: List<ReviewStep>) {
    val counts = remember(steps) { SummaryCounts.of(steps) }
    ReviewColumn("review-summary") {
        Text(
            stringResource(R.string.review_summary_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        counts.byTechnique.forEach { (technique, count) ->
            Body(stringResource(R.string.review_summary_technique, techniqueName(technique), count))
        }
        if (counts.beyond > 0) {
            Body(pluralStringResource(R.plurals.review_summary_beyond, counts.beyond, counts.beyond))
        }
        Body(
            if (counts.mistakes > 0) {
                pluralStringResource(R.plurals.review_summary_mistakes, counts.mistakes, counts.mistakes)
            } else {
                stringResource(R.string.review_summary_no_mistakes)
            },
        )
        if (counts.coach > 0) {
            Body(pluralStringResource(R.plurals.review_summary_coach, counts.coach, counts.coach))
        }
    }
}

private class SummaryCounts(
    val byTechnique: List<Pair<Technique, Int>>,
    val beyond: Int,
    val mistakes: Int,
    val coach: Int,
) {
    companion object {
        fun of(steps: List<ReviewStep>): SummaryCounts {
            // The player's own digits: the Coach's answers say nothing about the player's techniques.
            val placed = steps.filter { it.kind == ReviewStep.Kind.Placed && !it.move.coach }
            val byTechnique = placed.mapNotNull { it.technique }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedBy { it.first.ordinal }
            return SummaryCounts(
                byTechnique = byTechnique,
                beyond = placed.count { it.technique == null },
                mistakes = steps.count { it.kind == ReviewStep.Kind.Mistake },
                coach = steps.count { it.move.coach },
            )
        }
    }
}

/** "Move 12 of 54", then previous, a slider over every move, next. */
@Composable
private fun Controls(at: Int, count: Int, onMove: (Int) -> Unit) {
    val position = stringResource(R.string.review_position, at + 1, count)
    val sliderLabel = stringResource(R.string.review_slider)
    ReviewColumn("review-controls") {
        Text(
            position,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("review-position"),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onMove(at - 1) },
                enabled = at > 0,
                modifier = Modifier.testTag("review-previous"),
            ) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.review_previous))
            }
            if (count > 1) {
                Slider(
                    value = at.toFloat(),
                    onValueChange = { onMove(it.roundToInt().coerceIn(0, count - 1)) },
                    valueRange = 0f..(count - 1).toFloat(),
                    // One step per move, so accessibility swipes go a move at a time; the ticks
                    // themselves would crowd a long page, so they are not drawn.
                    steps = (count - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = sliderLabel
                            stateDescription = position
                        }
                        .testTag("review-slider"),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(
                onClick = { onMove(at + 1) },
                enabled = at < count - 1,
                modifier = Modifier.testTag("review-next"),
            ) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.review_next))
            }
        }
    }
}

/** What the move was, and why: the technique it needed then, a mistake, or an erasure. */
@Composable
private fun StepNote(spec: PuzzleSpec, step: ReviewStep, previousDigit: Int) {
    val geometry = spec.geometry
    val cell = step.move.cell
    val row = geometry.row(cell) + 1
    val column = geometry.column(cell) + 1
    val what = when {
        step.kind != ReviewStep.Kind.Erased -> stringResource(R.string.review_written, row, column, step.move.digit)
        previousDigit != 0 -> stringResource(R.string.review_erased, row, column, previousDigit)
        else -> stringResource(R.string.review_cell, row, column)
    }
    val technique = step.technique
    val reason = when (step.kind) {
        ReviewStep.Kind.Placed ->
            if (technique != null) {
                stringResource(R.string.review_why_technique, techniqueName(technique))
            } else {
                stringResource(R.string.review_why_beyond)
            }
        ReviewStep.Kind.Mistake -> stringResource(R.string.review_why_mistake)
        ReviewStep.Kind.Erased -> stringResource(R.string.review_why_erased)
    }
    val coachNote = when {
        !step.move.coach -> null
        step.kind == ReviewStep.Kind.Erased -> stringResource(R.string.review_coach_took_back)
        else -> stringResource(R.string.review_coach_answer)
    }
    ReviewColumn("review-step", live = true) {
        Text(what, style = MaterialTheme.typography.titleMedium)
        Body(reason, Modifier.testTag("review-why"))
        if (coachNote != null) Body(coachNote, Modifier.testTag("review-coach"))
    }
}
