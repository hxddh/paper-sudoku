package com.titpea.papersudoku.page

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.FrauncesPen
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.engine.DigitMask
import com.titpea.papersudoku.game.model.GameState
import kotlin.math.roundToInt

enum class BoardAction { Erase }

/**
 * The page: the one custom-drawn surface in the app.
 *
 * Touch: press and slide to aim. The cell under the finger is previewed (and magnified in a loupe
 * once the finger moves), and the tap is committed where the finger lifts. A plain tap behaves as
 * usual; a touch the system takes over (an edge back swipe, palm rejection) commits nothing. This
 * keeps 9×9 precise on phones without shrinking the grid or zooming.
 *
 * Drawing: one layer draws the tints, the digits and pencil marks (from text laid out once per
 * board size), the grid and the Coach's marks. It reads the page in the draw phase, so a tap
 * redraws the board without composing it; the only animation is the ink settling where a digit
 * was just written.
 *
 * Accessibility: every cell is one node (one swipe) with row/column, value or notes, the Coach's
 * marks and state, a click action and an Erase custom action. The nodes draw nothing; their
 * descriptions are built only when the semantics are read.
 *
 * Writing layers: printed givens (Fraunces), the pen (Fraunces italic, ink blue), pencil notes
 * (system sans). Clashes carry an underline as well as color.
 */
@Composable
fun Board(
    game: GameState,
    conflicts: Set<Int>,
    marks: CoachMarks,
    onTap: (Int) -> Unit,
    onCellAction: (Int, BoardAction) -> Unit,
    modifier: Modifier = Modifier,
    onAimMove: () -> Unit = {},
    showContent: Boolean = true,
    /** False for a drawing to look at (the handbook's examples): no taps, aiming or actions. */
    interactive: Boolean = true,
) {
    val ink = PaperTheme.ink
    val colors = MaterialTheme.colorScheme
    val size = game.size
    val boardDescription = stringResource(R.string.board_description, size)
    val enabled = interactive && !game.solved && showContent
    var aimed by remember { mutableStateOf<Int?>(null) }
    var loupeAt by remember { mutableStateOf<Offset?>(null) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnAimMove by rememberUpdatedState(onAimMove)
    val currentOnCellAction by rememberUpdatedState(onCellAction)

    // The drawing reads these in the draw phase: a tap or a slide redraws, it does not recompose.
    val currentGame = rememberUpdatedState(game)
    val currentShowContent = rememberUpdatedState(showContent)
    val currentConflicts = rememberUpdatedState(conflicts)
    val currentMarks = rememberUpdatedState(marks)
    // Worked out once per change of the page, the aim or the Coach's marks, not once per frame.
    val tints = remember {
        derivedStateOf { boardTints(currentGame.value, aimed, currentShowContent.value, currentMarks.value) }
    }
    val settle = rememberSettle(game, showContent)
    // Stable for the board's lifetime: the cell nodes skip on them.
    val tapCell = remember { { index: Int -> currentOnTap(index) } }
    val cellAction = remember { { index: Int, action: BoardAction -> currentOnCellAction(index, action) } }

    val measurer = rememberTextMeasurer(cacheSize = 0)
    val night = colors.background.luminance() < 0.5f
    val palette = BoardPalette(
        printed = ink.printed,
        pen = ink.pen,
        pencil = ink.pencil,
        gridLine = ink.gridLine,
        boxLine = ink.boxLine,
        error = colors.error,
        coach = colors.primary,
        emphasis = colors.secondary,
        selectedTint = colors.secondary.copy(alpha = 0.20f),
        sameDigitTint = colors.secondary.copy(alpha = if (night) 0.12f else 0.08f),
        coachTint = if (night) colors.primary.copy(alpha = 0.22f) else colors.primaryContainer.copy(alpha = 0.85f),
        focusWash = colors.primary.copy(alpha = if (night) 0.14f else 0.08f),
    )
    val drawing = remember(palette, measurer, size, game.spec.boxRows, game.spec.boxCols) {
        Modifier.drawWithCache {
            if (this.size.minDimension <= 0f) return@drawWithCache onDrawBehind {}
            val board = BoardInk.of(this, measurer, size)
            onDrawBehind {
                drawBoard(
                    board, palette, currentGame.value, currentShowContent.value, currentConflicts.value,
                    currentMarks.value, tints.value, settle.value,
                )
            }
        }
    }

    // A sudoku is never mirrored: row 1 column 1 is top left in every language. Hit-testing, the
    // loupe and the cells all use the same left-to-right layout (RTL used to flip only the cells).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(
        modifier = modifier
            .testTag("sudoku-board")
            .semantics {
                contentDescription = boardDescription
                collectionInfo = CollectionInfo(size, size)
            },
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        val density = LocalDensity.current
        val cellPx = with(density) { (side / size).toPx() }
        // Android honours at most 200dp of back-gesture exclusion per edge: spend it on the lower
        // part of each side, where a thumb starts slides from the edge cells.
        val stripPx = with(density) { 24.dp.toPx() }
        val budgetPx = with(density) { 200.dp.toPx() }
        Box(
            Modifier
                .size(side)
                .background(ink.sheet, RoundedCornerShape(6.dp))
                // The frame is the heaviest line, then box lines (2dp), then cells (1dp).
                .border(2.5.dp, ink.boxLine, RoundedCornerShape(6.dp))
                .systemGestureExclusion { it.edgeStrip(left = true, stripPx, budgetPx) }
                .systemGestureExclusion { it.edgeStrip(left = false, stripPx, budgetPx) }
                .pointerInput(enabled, size, cellPx) {
                    if (!enabled) return@pointerInput
                    fun cellAt(p: Offset): Int {
                        val c = (p.x / cellPx).toInt().coerceIn(0, size - 1)
                        val r = (p.y / cellPx).toInt().coerceIn(0, size - 1)
                        return r * size + c
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var current = cellAt(down.position)
                        aimed = current
                        var sliding = false
                        var lifted = false
                        // Cleared in finally: if the gesture is cut off (the board resizes under the
                        // finger), no highlight or loupe is left behind.
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    // A cancelled touch arrives as an already-consumed lift.
                                    lifted = !change.isConsumed
                                    break
                                }
                                if (!sliding && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    sliding = true
                                }
                                if (sliding) {
                                    change.consume()
                                    val next = cellAt(change.position)
                                    if (next != current) {
                                        current = next
                                        aimed = next
                                        currentOnAimMove()
                                    }
                                    loupeAt = change.position
                                }
                            }
                        } finally {
                            aimed = null
                            loupeAt = null
                        }
                        if (lifted) currentOnTap(current)
                    }
                }
                .then(drawing),
        ) {
            CellNodes(game, showContent, conflicts, marks, enabled, tapCell, cellAction)
            // Read through lambdas: following the finger only moves the loupe (its offset is read
            // at layout), and the loupe recomposes only when it appears, disappears or changes cell.
            Loupe(
                current = currentGame,
                index = { aimed },
                finger = { loupeAt },
                boardPx = with(density) { side.toPx() },
            )
        }
    }
    }
}

/** The board's colours, resolved from the theme once per composition. */
private data class BoardPalette(
    val printed: Color,
    val pen: Color,
    val pencil: Color,
    val gridLine: Color,
    val boxLine: Color,
    val error: Color,
    /** The Coach's ink: its marks, crossed-out candidates and answer. */
    val coach: Color,
    /** Pencil marks of the digit in hand: a change of colour only. */
    val emphasis: Color,
    val selectedTint: Color,
    val sameDigitTint: Color,
    val coachTint: Color,
    val focusWash: Color,
)

/**
 * Everything the drawing needs that depends only on the board's size: one text layout per digit
 * for the printed cut, the pen and pencil marks (drawn in any colour), and the line widths.
 */
private class BoardInk(
    val size: Int,
    val cell: Float,
    val printed: Array<TextLayoutResult>,
    val pen: Array<TextLayoutResult>,
    val pencil: Array<TextLayoutResult>,
    val cellLine: Float,
    val boxLine: Float,
    val markInset: Float,
    val slash: Float,
    val underline: Float,
    val dots: PathEffect,
    val ring: Float,
    val ringCorner: Float,
    val focusInset: Float,
    val focusCorner: Float,
    val focusStroke: Float,
) {
    companion object {
        fun of(scope: CacheDrawScope, measurer: TextMeasurer, size: Int): BoardInk =
            with(scope) {
                val cell = this.size.width / size
                // 6×6 cells are large: their digits are a little lighter in proportion. Pencil marks
                // are as large as their third of the cell allows. Sized from the cell, so large text
                // settings never overflow it.
                val digitSize = (cell * if (size == 6) 0.50f else 0.56f).toSp()
                val noteSize = (cell / 3f * 0.76f).toSp()
                fun layouts(style: TextStyle) = Array(size) { measurer.measure((it + 1).toString(), style) }
                BoardInk(
                    size = size,
                    cell = cell,
                    printed = layouts(
                        // The printed cut's own weight, so nothing asks for a bolder one it would fake.
                        TextStyle(fontFamily = FrauncesPrinted, fontSize = digitSize, fontWeight = FontWeight(560), fontFeatureSettings = "lnum, tnum"),
                    ),
                    pen = layouts(
                        TextStyle(fontFamily = FrauncesPen, fontSize = digitSize, fontWeight = FontWeight(420), fontFeatureSettings = "lnum, tnum"),
                    ),
                    pencil = layouts(TextStyle(fontSize = noteSize, fontFeatureSettings = "tnum")),
                    cellLine = 1.dp.toPx(),
                    boxLine = 2.dp.toPx(),
                    markInset = 1.dp.toPx(),
                    slash = 1.5.dp.toPx(),
                    underline = 2.dp.toPx(),
                    // Dotted, so a clash differs from a mark by shape, not only colour.
                    dots = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx())),
                    ring = 2.dp.toPx(),
                    ringCorner = 2.dp.toPx(),
                    focusInset = 3.dp.toPx(),
                    focusCorner = 4.dp.toPx(),
                    focusStroke = 1.5.dp.toPx(),
                )
            }
    }
}

private const val TINT_NONE = 0
private const val TINT_SAME_DIGIT = 1
private const val TINT_SELECTED = 2
private const val TINT_COACH = 3

/**
 * The three tints: the selected (or aimed) cell, the cells holding its digit, and the Coach's area
 * (the cell to fill and the pattern of the step). [noteDigit] is the digit whose pencil marks are
 * drawn in the emphasis colour, 0 for none.
 */
private class BoardTints(val cells: IntArray, val noteDigit: Int)

private fun boardTints(game: GameState, aimed: Int?, showContent: Boolean, marks: CoachMarks): BoardTints {
    val selected = aimed ?: game.selected
    val digit = if (showContent) game.lockedDigit ?: selected?.let { game.values[it] }?.takeIf { it != 0 } else null
    val cells = IntArray(game.values.size) { index ->
        when {
            // Red is the Coach's; the player's own selection is in pen blue, stronger than its echo.
            marks.target == index -> TINT_COACH
            selected == index -> TINT_SELECTED
            index in marks.pattern -> TINT_COACH
            digit != null && game.values[index] == digit -> TINT_SAME_DIGIT
            else -> TINT_NONE
        }
    }
    return BoardTints(cells, digit ?: 0)
}

/** The digit just written settles into the paper: one cell, one short spring, no bounce. */
private class Settle(val index: Int, val scale: Animatable<Float, AnimationVector1D>)

@Composable
private fun rememberSettle(game: GameState, showContent: Boolean): State<Settle> {
    // The page as last composed: a plain holder, read only to tell which cell was just written.
    val seen = remember { arrayOfNulls<GameState>(1) }
    val settle = remember(game.values) {
        val written = writtenCell(seen[0], game).takeIf { showContent } ?: -1
        Settle(written, Animatable(if (written >= 0) SETTLE_FROM else 1f))
    }
    SideEffect { seen[0] = game }
    LaunchedEffect(settle) {
        if (settle.index >= 0) {
            settle.scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = SETTLE_STIFFNESS))
        }
    }
    return rememberUpdatedState(settle)
}

/** The one cell the player just wrote a digit into, or -1 (no write, several, another page). */
private fun writtenCell(before: GameState?, after: GameState): Int {
    if (before == null || before.spec.key != after.spec.key) return -1
    var written = -1
    for (i in after.values.indices) {
        val value = after.values[i]
        if (value == 0 || value == before.values[i] || after.isGiven(i)) continue
        if (written >= 0) return -1
        written = i
    }
    return written
}

private const val SETTLE_FROM = 1.08f

/** Critically damped, settling in about 150 ms. */
private const val SETTLE_STIFFNESS = 1_600f

private fun DrawScope.drawBoard(
    ink: BoardInk,
    palette: BoardPalette,
    game: GameState,
    showContent: Boolean,
    conflicts: Set<Int>,
    marks: CoachMarks,
    tints: BoardTints,
    settle: Settle,
) {
    val size = ink.size
    val cell = ink.cell
    val cellSize = Size(cell, cell)
    for (index in 0 until size * size) {
        val left = (index % size) * cell
        val top = (index / size) * cell
        when (tints.cells[index]) {
            TINT_SELECTED -> drawRect(palette.selectedTint, Offset(left, top), cellSize)
            TINT_SAME_DIGIT -> drawRect(palette.sameDigitTint, Offset(left, top), cellSize)
            TINT_COACH -> drawRect(palette.coachTint, Offset(left, top), cellSize)
        }
        val value = if (showContent) game.values[index] else 0
        val ghost = marks.ghost.takeIf { marks.target == index }
        when {
            value != 0 -> {
                val given = game.isGiven(index)
                val conflict = showContent && index in conflicts
                val layout = (if (given) ink.printed else ink.pen)[value - 1]
                val color = when {
                    conflict -> palette.error
                    given -> palette.printed
                    else -> palette.pen
                }
                val at = centred(layout, left, top, cell, cell)
                val grow = if (index == settle.index) settle.scale.value else 1f
                if (grow != 1f) {
                    scale(grow, pivot = Offset(left + cell / 2f, top + cell / 2f)) { drawText(layout, color, at) }
                } else {
                    drawText(layout, color, at)
                }
                if (conflict) {
                    val y = at.y + layout.size.height * 0.92f
                    drawLine(
                        color = color,
                        start = Offset(at.x + layout.size.width * 0.15f, y),
                        end = Offset(at.x + layout.size.width * 0.85f, y),
                        strokeWidth = ink.underline,
                        cap = StrokeCap.Round,
                        pathEffect = ink.dots,
                    )
                }
            }
            ghost != null -> {
                val layout = ink.pen[ghost - 1]
                drawText(layout, palette.coach.copy(alpha = 0.78f), centred(layout, left, top, cell, cell))
            }
            else -> {
                val notes = if (showContent) game.notes[index] else 0
                val removed = marks.removals[index]
                if (notes != 0 || removed != null) drawMarks(ink, palette, notes, removed, tints.noteDigit, left, top)
            }
        }
        if (marks.target == index) {
            val half = ink.ring / 2f
            drawRoundRect(
                color = palette.coach,
                topLeft = Offset(left + half, top + half),
                size = Size(cell - ink.ring, cell - ink.ring),
                cornerRadius = CornerRadius(ink.ringCorner),
                style = Stroke(width = ink.ring),
            )
        }
    }
    drawGrid(ink, palette, game.spec.boxRows, game.spec.boxCols)
    // The units the Coach is talking about, in the Coach's ink: a light wash, and an outline set
    // inside the cells so it never sits on the box lines (shape as well as colour, for colour-blind
    // eyes).
    for (unit in marks.focusUnits) {
        val cells = game.spec.geometry.cells(unit)
        var minRow = size
        var maxRow = -1
        var minCol = size
        var maxCol = -1
        for (i in cells) {
            minRow = minOf(minRow, i / size)
            maxRow = maxOf(maxRow, i / size)
            minCol = minOf(minCol, i % size)
            maxCol = maxOf(maxCol, i % size)
        }
        val topLeft = Offset(minCol * cell + ink.focusInset, minRow * cell + ink.focusInset)
        val area = Size((maxCol - minCol + 1) * cell - 2 * ink.focusInset, (maxRow - minRow + 1) * cell - 2 * ink.focusInset)
        drawRoundRect(palette.focusWash, topLeft, area, CornerRadius(ink.focusCorner))
        drawRoundRect(
            color = palette.coach.copy(alpha = 0.75f),
            topLeft = topLeft,
            size = area,
            cornerRadius = CornerRadius(ink.focusCorner),
            style = Stroke(width = ink.focusStroke),
        )
    }
}

/** Pencil marks in a 3-column grid (two rows on 6×6), and the candidates the Coach crosses out. */
private fun DrawScope.drawMarks(
    ink: BoardInk,
    palette: BoardPalette,
    notes: Int,
    removed: Set<Int>?,
    emphasised: Int,
    left: Float,
    top: Float,
) {
    val rows = if (ink.size == 6) 2 else 3
    val width = (ink.cell - 2 * ink.markInset) / 3f
    val height = (ink.cell - 2 * ink.markInset) / rows
    for (digit in 1..ink.size) {
        val struck = removed != null && digit in removed
        if (!struck && !DigitMask.contains(notes, digit)) continue
        val layout = ink.pencil[digit - 1]
        val color = when {
            struck -> palette.coach
            digit == emphasised -> palette.emphasis
            else -> palette.pencil
        }
        val at = centred(
            layout,
            left + ink.markInset + ((digit - 1) % 3) * width,
            top + ink.markInset + ((digit - 1) / 3) * height,
            width,
            height,
        )
        drawText(layout, color, at)
        if (struck) {
            // A firm diagonal slash across the mark, readable at arm's length (a text
            // strike-through is a hairline at this size).
            val w = layout.size.width.toFloat()
            val h = layout.size.height.toFloat()
            val inset = minOf(w, h) * 0.18f
            drawLine(
                palette.coach,
                Offset(at.x + inset, at.y + h - inset),
                Offset(at.x + w - inset, at.y + inset),
                strokeWidth = ink.slash,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawGrid(ink: BoardInk, palette: BoardPalette, boxRows: Int, boxCols: Int) {
    val extent = ink.cell * ink.size
    for (i in 1 until ink.size) {
        val at = i * ink.cell
        val verticalBox = i % boxCols == 0
        val horizontalBox = i % boxRows == 0
        drawLine(
            color = if (verticalBox) palette.boxLine else palette.gridLine,
            start = Offset(at, 0f),
            end = Offset(at, extent),
            strokeWidth = if (verticalBox) ink.boxLine else ink.cellLine,
        )
        drawLine(
            color = if (horizontalBox) palette.boxLine else palette.gridLine,
            start = Offset(0f, at),
            end = Offset(extent, at),
            strokeWidth = if (horizontalBox) ink.boxLine else ink.cellLine,
        )
    }
}

private fun centred(layout: TextLayoutResult, left: Float, top: Float, width: Float, height: Float) =
    Offset(left + (width - layout.size.width) / 2f, top + (height - layout.size.height) / 2f)

/**
 * One node per cell, in the cell's place: what TalkBack, tests and UI Automator find. They draw
 * nothing, and each recomposes only when what it says changes.
 */
@Composable
private fun CellNodes(
    game: GameState,
    showContent: Boolean,
    conflicts: Set<Int>,
    marks: CoachMarks,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onCellAction: (Int, BoardAction) -> Unit,
) {
    val size = game.size
    val geometry = game.spec.geometry
    val resources = LocalResources.current
    val eraseLabel = stringResource(R.string.erase)
    Layout(
        content = {
            for (index in 0 until size * size) {
                val value = if (showContent) game.values[index] else 0
                val isTarget = marks.target == index
                CellNode(
                    index = index,
                    row = geometry.row(index),
                    column = geometry.column(index),
                    box = boxName(geometry, geometry.box(index)),
                    value = value,
                    notes = if (showContent) game.notes[index] else 0,
                    given = game.isGiven(index),
                    conflict = showContent && index in conflicts,
                    ghost = marks.ghost?.takeIf { isTarget && value == 0 } ?: 0,
                    removed = marks.removals[index]?.let(DigitMask::of) ?: 0,
                    isSelected = game.selected == index,
                    enabled = enabled,
                    onTap = onTap,
                    onCellAction = onCellAction,
                    resources = resources,
                    eraseLabel = eraseLabel,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // Edges rounded once, so the cells tile the board without gaps or overlaps.
        fun edge(i: Int, extent: Int) = i * extent / size
        val placeables = measurables.mapIndexed { i, measurable ->
            val column = i % size
            val row = i / size
            measurable.measure(
                Constraints.fixed(
                    edge(column + 1, width) - edge(column, width),
                    edge(row + 1, height) - edge(row, height),
                ),
            )
        }
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                placeable.place(edge(i % size, width), edge(i / size, height))
            }
        }
    }
}

@Composable
private fun CellNode(
    index: Int,
    row: Int,
    column: Int,
    /** The box by name, as the Coach says it ("the top-right box"), so its notes can be followed. */
    box: Int,
    value: Int,
    notes: Int,
    given: Boolean,
    conflict: Boolean,
    /** The Coach's answer, shown faintly in the target cell; 0 for none. */
    ghost: Int,
    /** The candidates the Coach crosses out here, as a digit mask. */
    removed: Int,
    isSelected: Boolean,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onCellAction: (Int, BoardAction) -> Unit,
    resources: Resources,
    eraseLabel: String,
) {
    Box(
        Modifier
            .testTag("cell-$index")
            .clearAndSetSemantics {
                collectionItemInfo = CollectionItemInfo(row, 1, column, 1)
                contentDescription = cellDescription(resources, row, column, box, value, notes, given, conflict, ghost, removed)
                selected = isSelected
                if (enabled) {
                    role = Role.Button
                    onClick {
                        onTap(index)
                        true
                    }
                    if (!given && (value != 0 || notes != 0)) {
                        customActions = listOf(
                            CustomAccessibilityAction(eraseLabel) { onCellAction(index, BoardAction.Erase); true },
                        )
                    }
                }
            },
    )
}

/** A magnified view of the aimed cell, held above the finger so the finger never hides it. */
@Composable
private fun Loupe(current: State<GameState>, index: () -> Int?, finger: () -> Offset?, boardPx: Float) {
    val visible by remember(finger) { derivedStateOf { finger() != null } }
    val target = index() ?: return
    if (!visible) return
    val game = current.value
    val density = LocalDensity.current
    val diameter = 76.dp
    val lift = 96.dp
    val d = with(density) { diameter.toPx() }
    val liftPx = with(density) { lift.toPx() }
    val value = game.values[target]
    val size = game.size
    Surface(
        modifier = Modifier
            .offset {
                val at = finger() ?: Offset.Zero
                val x = (at.x - d / 2f).coerceIn(-d / 4f, boardPx - d * 0.75f)
                val y = (at.y - liftPx - d / 2f).coerceAtLeast(-d * 0.9f)
                IntOffset(x.roundToInt(), y.roundToInt())
            }
            .size(diameter)
            .testTag("loupe")
            // A sighted aid that lives only while a finger is down: TalkBack never aims, and the
            // selected cell already says where the finger is. Hidden, it does not count as
            // covering the cells under it.
            .clearAndSetSemantics { hideFromAccessibility() },
        shape = CircleShape,
        color = PaperTheme.ink.sheet,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (value != 0) {
                Text(
                    value.toString(),
                    style = TextStyle(
                        fontFamily = if (game.isGiven(target)) FrauncesPrinted else FrauncesPen,
                        fontSize = 36.sp,
                        color = if (game.isGiven(target)) PaperTheme.ink.printed else PaperTheme.ink.pen,
                    ),
                )
            } else if (game.notes[target] != 0) {
                Text(
                    DigitMask.digits(game.notes[target]).joinToString(" "),
                    style = MaterialTheme.typography.labelLarge,
                    color = PaperTheme.ink.pencil,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Text(
                stringResource(R.string.loupe_label, target / size + 1, target % size + 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }
    }
}

private fun cellDescription(
    resources: Resources,
    row: Int,
    column: Int,
    box: Int,
    value: Int,
    notes: Int,
    given: Boolean,
    conflict: Boolean,
    ghost: Int,
    removed: Int,
): String {
    val state = when {
        value == 0 && notes != 0 -> resources.getString(R.string.cell_notes, DigitMask.digits(notes).joinToString(" "))
        value == 0 -> resources.getString(R.string.cell_empty)
        given -> resources.getString(R.string.cell_given, value)
        else -> resources.getString(R.string.cell_entered, value)
    }
    val separator = resources.getString(R.string.list_separator)
    var suffix = if (conflict) separator + resources.getString(R.string.cell_conflict) else ""
    if (ghost != 0) suffix += separator + resources.getString(R.string.cell_coach_answer, ghost)
    if (removed != 0) suffix += separator + resources.getString(R.string.cell_coach_removes, DigitMask.digits(removed).joinToString(" "))
    return resources.getString(R.string.cell_description, row + 1, column + 1, state, resources.getString(box)) + suffix
}

/** A strip along one side of the board, at most [budget] tall and anchored at its bottom. */
private fun LayoutCoordinates.edgeStrip(left: Boolean, strip: Float, budget: Float): Rect {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val top = (height - budget).coerceAtLeast(0f)
    return if (left) Rect(0f, top, strip, height) else Rect(width - strip, top, width, height)
}
