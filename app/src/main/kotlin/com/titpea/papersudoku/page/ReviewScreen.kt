package com.titpea.papersudoku.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.Move
import com.titpea.papersudoku.game.model.PuzzleSpec
import com.titpea.papersudoku.game.model.Review
import com.titpea.papersudoku.game.model.ReviewStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The page's work area: at least this tall, as tall as tools, status line and keys (see PageScreen). */
private val WorkAreaMinHeight: Dp = 176.dp
private val StatusLineHeight: Dp = 48.dp

/**
 * Reads a page back from its move record, as the page itself: the same header, the board at the
 * page's size showing the digits after each move (the move's cell selected), and, where the keys
 * were, the move and what justified it there and then, over the slider and previous/next. "This
 * page" in the header swaps the note for the page's counts. Opens at the last move. The reading is
 * worked out off the main thread; until it is ready the page stays blank rather than flashing a
 * half state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(spec: PuzzleSpec, moves: List<Move>, onBack: () -> Unit) {
    val steps by produceState<List<ReviewStep>?>(null, spec, moves) {
        value = withContext(Dispatchers.Default) { Review.of(spec, moves) }
    }
    var index by rememberSaveable(spec.key, moves.size) { mutableIntStateOf(moves.lastIndex) }
    var showSummary by rememberSaveable(spec.key) { mutableStateOf(false) }
    val ready = steps?.takeIf { it.isNotEmpty() }
    val at = if (ready == null) 0 else index.coerceIn(0, ready.lastIndex)
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = Modifier.testTag("review"),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("review-back")) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
                // The page's own header, with "Read back" before where it comes from.
                title = {
                    Column {
                        Text(
                            stringResource(R.string.review_title) + DOT + pageOverline(spec, locale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (LocalDensity.current.fontScale > 1.3f) 2 else 1,
                        )
                        Text(pageTitle(spec, locale), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    }
                },
                actions = {
                    if (ready != null) {
                        TextButton(
                            onClick = { showSummary = !showSummary },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.padding(end = 4.dp).heightIn(min = 48.dp).testTag("review-this-page"),
                        ) {
                            Text(stringResource(if (showSummary) R.string.review_show_moves else R.string.review_this_page))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                expandedHeight = LocalDensity.current.fontScale.let { scale ->
                    val lines = if (scale > 1.3f) 3 else 2
                    maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, (24 * lines * scale + 12).dp)
                },
            )
        },
    ) { padding ->
        val step = ready?.get(at)
        // The moved cell is selected, in the player's blue; a slip shows as a clash would.
        // Terracotta is the Coach's, so it is not used for the player's own moves.
        val game = remember(spec, step) {
            if (step == null) GameState.new(spec) else GameState.new(spec).copy(values = step.values, selected = step.move.cell)
        }
        // Blank while the reading is worked out; a page with no record shows its printed digits.
        val showBoard = step != null || moves.isEmpty()
        val conflicts = if (step != null && step.kind == ReviewStep.Kind.Mistake) setOf(step.move.cell) else emptySet()
        val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
        val workHeight = maxOf(WorkAreaMinHeight * fontScale, ToolbarHeight + StatusLineHeight + keypadHeight(spec.size))
        val panel = @Composable { modifier: Modifier ->
            ReviewPanel(
                spec = spec,
                steps = ready,
                at = at,
                empty = moves.isEmpty(),
                showSummary = showSummary,
                onMove = { index = it },
                modifier = modifier,
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // As the page lays itself out: beside the board on a wide window, under it otherwise.
            if (maxWidth > maxHeight && maxWidth >= 560.dp) {
                val side = (maxWidth * 0.4f).coerceIn(320.dp, 440.dp)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ReviewBoard(
                        game, conflicts, showBoard,
                        Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        Alignment.CenterEnd,
                    )
                    Column(
                        Modifier.width(side).fillMaxHeight().padding(end = 24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) { panel(Modifier.fillMaxWidth().height(workHeight)) }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ReviewBoard(
                        game, conflicts, showBoard,
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .padding(horizontal = if (spec.size == 9) 8.dp else 12.dp),
                        BiasAlignment(0f, 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    panel(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .height(workHeight),
                    )
                }
            }
        }
    }
}

/** The board at the page's size: as large as the space allows, square, at most 720dp. */
@Composable
private fun ReviewBoard(game: GameState, conflicts: Set<Int>, show: Boolean, modifier: Modifier, alignment: Alignment) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val side = minOf(maxWidth, maxHeight).coerceAtMost(720.dp)
        Box(Modifier.size(side).testTag("review-board"), contentAlignment = Alignment.Center) {
            if (show) {
                Board(
                    game = game,
                    conflicts = conflicts,
                    marks = CoachMarks.None,
                    onTap = {},
                    onCellAction = { _, _ -> },
                    interactive = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Where the keys are on the page: the move and why (or the page's counts), and under it, near the
 * hand, the slider with previous and next. One height, so the board never moves.
 */
@Composable
private fun ReviewPanel(
    spec: PuzzleSpec,
    steps: List<ReviewStep>?,
    at: Int,
    empty: Boolean,
    showSummary: Boolean,
    onMove: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.testTag("review-panel")) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (empty) {
                ReviewColumn("review-empty") { Body(stringResource(R.string.review_empty)) }
            } else if (steps != null) {
                if (showSummary) {
                    Summary(steps)
                } else {
                    val before = if (at > 0) steps[at - 1].values else spec.givens
                    StepNote(spec, steps[at], before[steps[at].move.cell])
                }
            }
        }
        if (steps != null) Controls(at = at, count = steps.size, onMove = onMove)
    }
}

/** A block of the panel; its text sits 20dp in from the screen, with the page's own margins. */
@Composable
private fun ReviewColumn(tag: String, live: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            // The note is read out as each move comes up.
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
