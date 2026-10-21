package com.titpea.papersudoku.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.FrauncesPen
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.engine.DigitMask
import com.titpea.papersudoku.game.model.GameState

enum class BoardAction { Notes, Erase, Coach }

/**
 * The board: the one custom-drawn surface in the app.
 *
 * Grid lines are a Canvas; each cell is a real semantics node (row/column, value, notes, state)
 * with custom actions, and digits are Text so accessibility services read them.
 *
 * Three writing layers: printed givens (Fraunces), the player's pen (Fraunces italic, ink blue),
 * and pencil notes (system sans). Conflicts get a second, non-color cue: an underline.
 */
@Composable
fun SudokuBoard(
    game: GameState,
    conflicts: Set<Int>,
    mistakes: Set<Int>,
    marks: CoachMarks,
    hidden: Boolean,
    onTap: (Int) -> Unit,
    onCellAction: (Int, BoardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = PaperTheme.ink
    val size = game.size
    val geometry = game.spec.geometry
    val selected = game.selected
    val highlightDigit = game.lockedDigit ?: selected?.let { game.values[it] }?.takeIf { it != 0 }
    val boardDescription = stringResource(R.string.board_description, size)

    BoxWithConstraints(
        modifier = modifier
            .testTag("sudoku-board")
            .semantics {
                contentDescription = boardDescription
                collectionInfo = CollectionInfo(size, size)
            },
        contentAlignment = Alignment.Center,
    ) {
        val cellSize = minOf(maxWidth, maxHeight) / size
        Box(
            Modifier
                .fillMaxSize()
                .background(ink.sheet, RoundedCornerShape(6.dp))
                .border(1.5.dp, ink.boxLine, RoundedCornerShape(6.dp)),
        ) {
            Column(Modifier.fillMaxSize()) {
                for (row in 0 until size) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (column in 0 until size) {
                            val index = row * size + column
                            val related = selected != null && selected != index && geometry.sees(selected, index)
                            BoardCell(
                                index = index,
                                row = row,
                                column = column,
                                value = if (hidden) 0 else game.values[index],
                                notes = if (hidden) 0 else game.notes[index],
                                given = game.isGiven(index),
                                size = size,
                                cellSize = cellSize,
                                isSelected = selected == index,
                                related = related && !hidden,
                                sameDigit = !hidden && highlightDigit != null && game.values[index] == highlightDigit,
                                highlightDigit = highlightDigit,
                                conflict = index in conflicts && !hidden,
                                mistake = index in mistakes && !hidden,
                                marks = marks,
                                enabled = !hidden && !game.solved,
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
        }
    }
}

@Composable
private fun BoardCell(
    index: Int,
    row: Int,
    column: Int,
    value: Int,
    notes: Int,
    given: Boolean,
    size: Int,
    cellSize: Dp,
    isSelected: Boolean,
    related: Boolean,
    sameDigit: Boolean,
    highlightDigit: Int?,
    conflict: Boolean,
    mistake: Boolean,
    marks: CoachMarks,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onCellAction: (Int, BoardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ink = PaperTheme.ink
    val isTarget = marks.target == index
    val inPattern = index in marks.pattern
    val inFocus = index in marks.focus
    val removed = marks.removals[index].orEmpty()

    val background = when {
        isTarget -> colors.primary.copy(alpha = 0.20f)
        isSelected -> colors.primary.copy(alpha = 0.16f)
        inPattern -> colors.primaryContainer.copy(alpha = 0.85f)
        inFocus -> colors.tertiaryContainer.copy(alpha = 0.55f)
        sameDigit -> colors.secondaryContainer.copy(alpha = 0.75f)
        related -> colors.surfaceVariant.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val wrong = conflict || mistake
    val digitColor = when {
        wrong -> colors.error
        given -> ink.printed
        else -> ink.pen
    }

    val density = LocalDensity.current
    val digitSize: TextUnit = with(density) { (cellSize.toPx() * 0.56f).toSp() }
    val noteSize: TextUnit = with(density) { (cellSize.toPx() / 3f * 0.66f).toSp() }

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

    val description = cellDescription(row, column, value, notes, given, conflict, mistake)
    val notesLabel = stringResource(R.string.notes)
    val eraseLabel = stringResource(R.string.erase)
    val coachLabel = stringResource(R.string.coach)
    val outline = colors.primary

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
            .semantics {
                collectionItemInfo = CollectionItemInfo(row, 1, column, 1)
                role = Role.Button
                contentDescription = description
                selected = isSelected
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(notesLabel) { onCellAction(index, BoardAction.Notes); true },
                        CustomAccessibilityAction(eraseLabel) { onCellAction(index, BoardAction.Erase); true },
                        CustomAccessibilityAction(coachLabel) { onCellAction(index, BoardAction.Coach); true },
                    )
                }
            }
            .clickable(enabled = enabled) { onTap(index) },
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
                    fontWeight = if (given) FontWeight.SemiBold else FontWeight.Normal,
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
                        if (wrong) {
                            Modifier.drawBehind {
                                val y = this.size.height * 0.92f
                                drawLine(
                                    color = digitColor,
                                    start = Offset(this.size.width * 0.15f, y),
                                    end = Offset(this.size.width * 0.85f, y),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
            isTarget && marks.ghost != null -> Text(
                text = marks.ghost.toString(),
                color = colors.primary.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = FrauncesPen, fontSize = digitSize),
            )
            notes != 0 || removed.isNotEmpty() -> NoteGrid(
                notes = notes,
                removed = removed,
                size = size,
                noteSize = noteSize,
                highlightDigit = highlightDigit,
            )
        }
    }
}

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
                            Text(
                                text = digit.toString(),
                                color = when {
                                    struck -> colors.error
                                    emphasised -> colors.secondary
                                    else -> ink.pencil
                                },
                                style = TextStyle(
                                    fontSize = noteSize,
                                    fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Medium,
                                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                                    textAlign = TextAlign.Center,
                                ),
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
    value: Int,
    notes: Int,
    given: Boolean,
    conflict: Boolean,
    mistake: Boolean,
): String {
    val state = when {
        value == 0 && notes != 0 -> stringResource(
            R.string.cell_notes,
            DigitMask.digits(notes).joinToString(" "),
        )
        value == 0 -> stringResource(R.string.cell_empty)
        given -> stringResource(R.string.cell_given, value)
        else -> stringResource(R.string.cell_entered, value)
    }
    val suffix = when {
        conflict -> ", " + stringResource(R.string.cell_conflict)
        mistake -> ", " + stringResource(R.string.cell_mistake)
        else -> ""
    }
    return stringResource(R.string.cell_description, row + 1, column + 1, state) + suffix
}
