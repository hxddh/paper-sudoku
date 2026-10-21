package com.example.sudoku.game

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sudoku.R

/**
 * The board is the one custom-drawn component in the app.
 *
 * Grid lines are drawn once in a Canvas; each cell remains a real semantics node with a
 * CollectionItemInfo so TalkBack can announce its row/column/value. Digits are Text, not
 * Canvas text, so they are selectable/readable by accessibility services.
 *
 * 9×9 cells are 43.3dp on a 390dp phone, below the 48dp touch-target guideline. Production must
 * either use 6×6 on phones, offer a keyboard/row-column picker, or treat this as a documented
 * exception. 6×6 cells are 65dp and meet the guideline.
 */
@Composable
fun SudokuBoard(
    state: GameUiState,
    onCellSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val strongLineColor = MaterialTheme.colorScheme.outline
    val boardShape = RoundedCornerShape(3.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .testTag("sudoku-board")
            .semantics { collectionInfo = CollectionInfo(state.size, state.size) },
        contentAlignment = Alignment.Center,
    ) {
        val boardSize = minOf(maxWidth, maxHeight)
        val cellSize = boardSize / state.size

        Box(
            modifier = Modifier
                .size(boardSize)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), boardShape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / state.size
                for (i in 0..state.size) {
                    val x = i * cell
                    val y = i * cell
                    val verticalStroke = if (i % state.boxCols == 0) 2.dp.toPx() else 1.dp.toPx()
                    val horizontalStroke = if (i % state.boxRows == 0) 2.dp.toPx() else 1.dp.toPx()
                    drawLine(
                        color = if (i % state.boxCols == 0) strongLineColor else lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = verticalStroke,
                    )
                    drawLine(
                        color = if (i % state.boxRows == 0) strongLineColor else lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = horizontalStroke,
                    )
                }
            }

            Column(Modifier.fillMaxSize()) {
                for (row in 0 until state.size) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (column in 0 until state.size) {
                            val index = row * state.size + column
                            BoardCell(
                                state = state,
                                index = index,
                                row = row,
                                column = column,
                                cellSize = cellSize,
                                onCellSelected = onCellSelected,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCell(
    state: GameUiState,
    index: Int,
    row: Int,
    column: Int,
    cellSize: Dp,
    onCellSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cell = state.cells[index]
    val isSelected = state.selected == index
    val isRelated = isRelated(state, index)
    val isHintPeer = state.hint?.let { isRelatedTo(it.index, state, index) } == true
    val isHintFocus = state.hint?.index == index
    val isConflict = index in state.conflicts

    val background = when {
        isHintFocus -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        isHintPeer -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        isRelated -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val digitColor = when {
        isConflict -> MaterialTheme.colorScheme.error
        cell.given -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.secondary
    }
    val density = LocalDensity.current
    val digitSize = with(density) {
        (cellSize.toPx() * 0.46f).toSp()
    }
    val valueText = when {
        isConflict && cell.value != 0 -> stringResource(R.string.cell_conflict, cell.value)
        cell.given -> stringResource(R.string.cell_given, cell.value)
        cell.value != 0 -> stringResource(R.string.cell_entered, cell.value)
        else -> stringResource(R.string.cell_empty)
    }
    val description = stringResource(R.string.cell_description, row + 1, column + 1, valueText)
    val shape = RoundedCornerShape(2.dp)

    Box(
        modifier = modifier
            .testTag("cell-$index")
            .background(background)
            .then(
                if (isSelected || isHintFocus) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .semantics {
                collectionItemInfo = CollectionItemInfo(row, column, 1, 1)
                role = Role.Button
                contentDescription = description
                selected = isSelected
            }
            .clickable { onCellSelected(index) },
        contentAlignment = Alignment.Center,
    ) {
        if (cell.value != 0) {
            Text(
                text = cell.value.toString(),
                color = digitColor,
                fontSize = digitSize,
                fontWeight = if (cell.given) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = digitSize,
                    fontFeatureSettings = "tnum",
                ),
            )
        } else if (cell.notes.isNotEmpty()) {
            NoteGrid(
                notes = cell.notes,
                size = state.size,
                cellSize = cellSize,
            )
        }
    }
}

@Composable
private fun NoteGrid(
    notes: Set<Int>,
    size: Int,
    cellSize: Dp,
) {
    val columns = 3
    val rows = if (size == 6) 2 else 3
    val density = LocalDensity.current
    val noteSize = with(density) {
        (cellSize.toPx() * 0.14f).toSp()
    }

    Column(Modifier.fillMaxSize().padding(2.dp)) {
        repeat(rows) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(columns) { column ->
                    val digit = row * columns + column + 1
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (digit <= size && digit in notes) {
                            Text(
                                text = digit.toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = noteSize,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isRelated(state: GameUiState, index: Int): Boolean {
    val selected = state.selected ?: return false
    if (selected == index) return false
    return isRelatedTo(selected, state, index)
}

private fun isRelatedTo(anchor: Int, state: GameUiState, index: Int): Boolean {
    if (anchor == index) return false
    val size = state.size
    val anchorRow = anchor / size
    val anchorColumn = anchor % size
    val row = index / size
    val column = index % size
    val sameBox =
        row / state.boxRows == anchorRow / state.boxRows &&
            column / state.boxCols == anchorColumn / state.boxCols
    return row == anchorRow || column == anchorColumn || sameBox
}
