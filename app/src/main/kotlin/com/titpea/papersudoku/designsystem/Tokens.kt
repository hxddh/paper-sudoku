package com.titpea.papersudoku.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * 11.0: the page's measures in one place. Every distance is a step of one scale, every corner one
 * of three radii and every tint one of four strengths, so screens built apart still line up.
 */

/** The only distances: 4, 8, 12, 16, 24, 32, 48. */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
    val XXXL = 48.dp

    /** The page's side margin: the board's edge, the keys' edge, a card's edge. */
    val Margin = L

    /** The one text edge: the margin plus 4dp optical, so letters line up with the board's frame. */
    val TextEdge = 20.dp

    /** Where the board sits under the page header, on every page-type screen. */
    val BoardTop = L
}

/** Corners: square cells, 2dp marks and pips, 8dp for everything held (board, cards, keys, buttons, sheets). */
object Radius {
    val Cell = 0.dp
    val Mark = 2.dp
    val Sheet = 8.dp
}

/** The 8dp corner of everything held; full rounding is only for dots and the icon ripple. */
val SheetShape: RoundedCornerShape = RoundedCornerShape(Radius.Sheet)

/** A 2dp mark: pips, mastery strokes, the held key's underline. */
val MarkShape: RoundedCornerShape = RoundedCornerShape(Radius.Mark)

/** Tints come in four strengths only. */
object Alpha {
    /** A whisper: the paper's grain, a cell's echo at night. */
    const val Faint = 0.05f

    /** A wash: same digits, a pressed key, the Coach's unit. */
    const val Wash = 0.08f

    /** A tint: the selection. */
    const val Tint = 0.16f
    const val Full = 1f
}

/** The hairline edge of cards and sheets, the same in both themes. */
object Hairline {
    val Width = 1.dp
}

/** The hairline `outlineVariant` edge every card and sheet carries. */
@Composable
fun hairline(): BorderStroke = BorderStroke(Hairline.Width, MaterialTheme.colorScheme.outlineVariant)

/**
 * A card or sheet of paper: 8dp corners, a step off the page, and a hairline edge in both themes.
 * [selected] lifts it one more step (the page open beside the notebook).
 */
@Composable
fun PaperSheet(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = SheetShape,
        color = sheetColor(selected),
        border = hairline(),
        modifier = modifier,
    ) { Column(content = content) }
}

/** A sheet that is one tap target as a whole. */
@Composable
fun PaperSheet(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = SheetShape,
        color = sheetColor(selected),
        border = hairline(),
        modifier = modifier,
    ) { Column(content = content) }
}

@Composable
private fun sheetColor(selected: Boolean) =
    if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
