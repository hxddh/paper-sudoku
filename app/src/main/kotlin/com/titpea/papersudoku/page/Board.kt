package com.titpea.papersudoku.page

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.FrauncesPen
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.engine.DigitMask
import com.titpea.papersudoku.engine.Geometry
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
 * Accessibility: every cell is one node (one swipe) with row/column, value or notes, the Coach's
 * marks and state, a click action and an Erase custom action.
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
    val size = game.size
    val geometry = game.spec.geometry
    val boardDescription = stringResource(R.string.board_description, size)
    val enabled = interactive && !game.solved && showContent
    var aimed by remember { mutableStateOf<Int?>(null) }
    var loupeAt by remember { mutableStateOf<Offset?>(null) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnAimMove by rememberUpdatedState(onAimMove)

    // The aimed cell is read only inside the cells' highlight lambdas (and the loupe's): aiming
    // at another cell recomposes the cells whose highlight changes, not the whole board.
    val selected = { aimed ?: game.selected }

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
        val cellSize = side / size
        val density = LocalDensity.current
        val cellPx = with(density) { cellSize.toPx() }
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
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                for (row in 0 until size) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (column in 0 until size) {
                            val index = row * size + column
                            Cell(
                                index = index,
                                row = row,
                                column = column,
                                value = if (showContent) game.values[index] else 0,
                                notes = if (showContent) game.notes[index] else 0,
                                given = game.isGiven(index),
                                size = size,
                                cellSize = cellSize,
                                highlight = { cellHighlight(index, selected(), game, showContent) },
                                conflict = index in conflicts && showContent,
                                marks = marks,
                                enabled = enabled,
                                onTap = onTap,
                                onCellAction = onCellAction,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val cell = this.size.width / size
                for (i in 1 until size) {
                    val verticalBox = i % game.spec.boxCols == 0
                    val horizontalBox = i % game.spec.boxRows == 0
                    drawLine(
                        color = if (verticalBox) ink.boxLine else ink.gridLine,
                        start = Offset(i * cell, 0f),
                        end = Offset(i * cell, this.size.height),
                        strokeWidth = if (verticalBox) 2.dp.toPx() else 1.dp.toPx(),
                    )
                    drawLine(
                        color = if (horizontalBox) ink.boxLine else ink.gridLine,
                        start = Offset(0f, i * cell),
                        end = Offset(this.size.width, i * cell),
                        strokeWidth = if (horizontalBox) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }
            }
            // The units the Coach is talking about, in the Coach's ink: a light wash, and an
            // outline set inside the cells so it never sits on the box lines (shape as well as
            // colour, for colour-blind eyes).
            if (marks.focusUnits.isNotEmpty()) {
                val ink = MaterialTheme.colorScheme.primary
                val wash = ink.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.14f else 0.08f)
                Canvas(Modifier.fillMaxSize()) {
                    val cell = this.size.width / size
                    val inset = 3.dp.toPx()
                    for (unit in marks.focusUnits) {
                        val cells = geometry.cells(unit)
                        val rows = cells.map { it / size }
                        val cols = cells.map { it % size }
                        val topLeft = Offset(cols.min() * cell + inset, rows.min() * cell + inset)
                        val area = Size((cols.max() - cols.min() + 1) * cell - 2 * inset, (rows.max() - rows.min() + 1) * cell - 2 * inset)
                        drawRoundRect(color = wash, topLeft = topLeft, size = area, cornerRadius = CornerRadius(4.dp.toPx()))
                        drawRoundRect(
                            color = ink.copy(alpha = 0.75f),
                            topLeft = topLeft,
                            size = area,
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }
            // Read through lambdas: following the finger only moves the loupe (its offset is read
            // at layout), and the loupe recomposes only when it appears, disappears or changes cell.
            Loupe(
                game = game,
                index = { aimed },
                finger = { loupeAt },
                boardPx = with(density) { side.toPx() },
            )
        }
    }
    }
}

/** A magnified view of the aimed cell, held above the finger so the finger never hides it. */
@Composable
private fun Loupe(game: GameState, index: () -> Int?, finger: () -> Offset?, boardPx: Float) {
    val visible by remember(finger) { derivedStateOf { finger() != null } }
    val target = index() ?: return
    if (!visible) return
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

@Composable
private fun Cell(
    index: Int,
    row: Int,
    column: Int,
    value: Int,
    notes: Int,
    given: Boolean,
    size: Int,
    cellSize: Dp,
    /** Read in a derived state: the cell recomposes only when its own highlight changes. */
    highlight: () -> CellHighlight,
    conflict: Boolean,
    marks: CoachMarks,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onCellAction: (Int, BoardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown by remember(highlight) { derivedStateOf(structuralEqualityPolicy(), highlight) }
    val isSelected = shown.selected
    val related = shown.related
    val sameDigit = shown.sameDigit
    val noteHighlight = shown.noteHighlight
    val colors = MaterialTheme.colorScheme
    val ink = PaperTheme.ink
    val isTarget = marks.target == index
    val inPattern = index in marks.pattern
    val removed = marks.removals[index].orEmpty()

    val night = colors.background.luminance() < 0.5f
    val background = when {
        // Red is the Coach's; the player's own selection is in pen blue, stronger than its echoes.
        // The Coach's focus unit is outlined on the board, not filled.
        isTarget -> colors.primary.copy(alpha = 0.20f)
        isSelected -> colors.secondary.copy(alpha = 0.20f)
        inPattern -> if (night) colors.primary.copy(alpha = 0.22f) else colors.primaryContainer.copy(alpha = 0.85f)
        sameDigit -> colors.secondary.copy(alpha = if (night) 0.12f else 0.08f)
        related -> colors.surfaceVariant.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val digitColor = when {
        conflict -> colors.error
        given -> ink.printed
        else -> ink.pen
    }

    val density = LocalDensity.current
    // 6×6 cells are large: their digits are a little lighter in proportion. Pencil marks are as
    // large as their third of the cell allows.
    val digitSize: TextUnit = with(density) { (cellSize.toPx() * if (size == 6) 0.50f else 0.56f).toSp() }
    val noteSize: TextUnit = with(density) { (cellSize.toPx() / 3f * 0.76f).toSp() }

    // Ink lands with a small settle when the player writes a digit.
    val appear = remember { Animatable(1f) }
    var lastValue by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) {
        if (value != 0 && value != lastValue && !given) {
            appear.snapTo(0f)
            appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }
        lastValue = value
    }

    val description = cellDescription(
        row, column, boxName(geometryOf(size), geometryOf(size).box(index)),
        value, notes, given, conflict,
        ghost = marks.ghost.takeIf { isTarget && value == 0 },
        removed = removed,
    )
    val eraseLabel = stringResource(R.string.erase)
    val outline = if (isTarget) colors.primary else colors.secondary

    Box(
        modifier = modifier
            .testTag("cell-$index")
            .background(background)
            .then(
                if (isSelected || isTarget) {
                    Modifier.border(2.dp, outline, RoundedCornerShape(2.dp))
                } else {
                    Modifier
                },
            )
            // The digit and note texts below are drawing only; the cell speaks for itself, once.
            .clearAndSetSemantics {
                collectionItemInfo = CollectionItemInfo(row, 1, column, 1)
                contentDescription = description
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
        contentAlignment = Alignment.Center,
    ) {
        when {
            value != 0 -> Text(
                text = value.toString(),
                color = digitColor,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = if (given) FrauncesPrinted else FrauncesPen,
                    fontSize = digitSize,
                    // The printed cut's own weight, so nothing asks for a bolder one it would fake.
                    fontWeight = if (given) FontWeight(560) else FontWeight(420),
                    fontFeatureSettings = "lnum, tnum",
                ),
                modifier = Modifier
                    .graphicsLayer {
                        val s = 1.25f - 0.25f * appear.value
                        scaleX = s
                        scaleY = s
                        alpha = appear.value
                    }
                    .then(
                        if (conflict) {
                            Modifier.drawBehind {
                                val y = this.size.height * 0.92f
                                // Dotted, so a clash differs from a mark by shape, not only colour.
                                drawLine(
                                    color = digitColor,
                                    start = Offset(this.size.width * 0.15f, y),
                                    end = Offset(this.size.width * 0.85f, y),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx())),
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
            isTarget && marks.ghost != null -> Text(
                text = marks.ghost.toString(),
                color = colors.primary.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = FrauncesPen, fontSize = digitSize),
            )
            notes != 0 || removed.isNotEmpty() -> NoteGrid(
                notes = notes,
                removed = removed,
                size = size,
                noteSize = noteSize,
                highlightDigit = noteHighlight,
            )
        }
    }
}

/** How a cell echoes the selected (or aimed) cell. */
private data class CellHighlight(
    val selected: Boolean,
    val related: Boolean,
    val sameDigit: Boolean,
    /** The highlighted digit, when this cell's notes contain it. */
    val noteHighlight: Int?,
)

private fun cellHighlight(index: Int, selected: Int?, game: GameState, showContent: Boolean): CellHighlight {
    val highlightDigit = game.lockedDigit ?: selected?.let { game.values[it] }?.takeIf { it != 0 }
    return CellHighlight(
        selected = selected == index,
        related = showContent && selected != null && selected != index && game.spec.geometry.sees(selected, index),
        sameDigit = showContent && highlightDigit != null && game.values[index] == highlightDigit,
        noteHighlight = highlightDigit?.takeIf { showContent && DigitMask.contains(game.notes[index], it) },
    )
}

private fun geometryOf(size: Int): Geometry = if (size == 6) Geometry.Mini else Geometry.Classic

@Composable
private fun NoteGrid(
    notes: Int,
    removed: Set<Int>,
    size: Int,
    noteSize: TextUnit,
    highlightDigit: Int?,
) {
    val columns = 3
    val rows = if (size == 6) 2 else 3
    val ink = PaperTheme.ink
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(1.dp)) {
        repeat(rows) { r ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(columns) { c ->
                    val digit = r * columns + c + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val struck = digit in removed
                        val noted = DigitMask.contains(notes, digit)
                        if (digit <= size && (noted || struck)) {
                            val emphasised = digit == highlightDigit
                            // Struck: a firm diagonal slash across the mark, readable at arm's length
                            // (a text strike-through is a hairline at this size).
                            val slash = if (struck) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    val inset = this.size.minDimension * 0.18f
                                    drawLine(
                                        colors.primary,
                                        Offset(inset, this.size.height - inset),
                                        Offset(this.size.width - inset, inset),
                                        strokeWidth = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                    )
                                }
                            } else {
                                Modifier
                            }
                            Text(
                                text = digit.toString(),
                                color = when {
                                    struck -> colors.primary
                                    emphasised -> colors.secondary
                                    else -> ink.pencil
                                },
                                style = TextStyle(
                                    fontSize = noteSize,
                                    fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
                                    fontFeatureSettings = "tnum",
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = slash,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun cellDescription(
    row: Int,
    column: Int,
    /** The box by name, as the Coach says it ("the top-right box"), so its notes can be followed. */
    box: Int,
    value: Int,
    notes: Int,
    given: Boolean,
    conflict: Boolean,
    ghost: Int?,
    removed: Set<Int>,
): String {
    val state = when {
        value == 0 && notes != 0 -> stringResource(R.string.cell_notes, DigitMask.digits(notes).joinToString(" "))
        value == 0 -> stringResource(R.string.cell_empty)
        given -> stringResource(R.string.cell_given, value)
        else -> stringResource(R.string.cell_entered, value)
    }
    val separator = stringResource(R.string.list_separator)
    var suffix = if (conflict) separator + stringResource(R.string.cell_conflict) else ""
    if (ghost != null) suffix += separator + stringResource(R.string.cell_coach_answer, ghost)
    if (removed.isNotEmpty()) suffix += separator + stringResource(R.string.cell_coach_removes, removed.sorted().joinToString(" "))
    return stringResource(R.string.cell_description, row + 1, column + 1, state, stringResource(box)) + suffix
}

/** A strip along one side of the board, at most [budget] tall and anchored at its bottom. */
private fun LayoutCoordinates.edgeStrip(left: Boolean, strip: Float, budget: Float): Rect {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val top = (height - budget).coerceAtLeast(0f)
    return if (left) Rect(0f, top, strip, height) else Rect(width - strip, top, width, height)
}
