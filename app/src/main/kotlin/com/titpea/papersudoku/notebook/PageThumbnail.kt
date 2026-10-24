package com.titpea.papersudoku.notebook

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.titpea.papersudoku.designsystem.FrauncesPen
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.data.PageSummary

/**
 * A miniature of a page as it was left: printed digits, the player's ink, and the grid. Without a
 * saved game it is a blank sheet with its box lines. Purely decorative for accessibility; the row
 * that contains it describes the page.
 */
@Composable
fun PageThumbnail(
    page: PageSummary?,
    size: Int,
    modifier: Modifier = Modifier,
    boxRows: Int = if (size == 6) 2 else 3,
    boxCols: Int = 3,
) {
    val ink = PaperTheme.ink
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // The page is read in draw: a page written on redraws its thumbnail, nothing is laid out again.
    val current = rememberUpdatedState(page)
    Spacer(
        modifier.aspectRatio(1f).drawWithCache {
            val side = this.size.minDimension
            val cell = side / size
            // Pixels → sp through the density, so the digits fit the cell at any font scale. One
            // layout per digit and cut, for every page drawn at this size.
            val fontSize = (cell * 0.62f).toSp()
            fun layouts(family: FontFamily) =
                Array(size) { measurer.measure((it + 1).toString(), TextStyle(fontFamily = family, fontSize = fontSize)) }
            val printed = if (side > 0f) layouts(FrauncesPrinted) else emptyArray()
            val pen = if (side > 0f) layouts(FrauncesPen) else emptyArray()
            // Line weights in dp, as on the board: cells, then boxes, then the frame.
            val cellLine = 0.75.dp.toPx()
            val boxLine = 1.25.dp.toPx()
            val frame = 1.5.dp.toPx()
            val corner = CornerRadius(side * 0.04f)
            onDrawBehind {
                if (side <= 0f) return@onDrawBehind
                drawRoundRect(ink.sheet, size = Size(side, side), cornerRadius = corner)
                for (i in 1 until size) {
                    val v = i % boxCols == 0
                    val h = i % boxRows == 0
                    drawLine(if (v) ink.boxLine else ink.gridLine, Offset(i * cell, 0f), Offset(i * cell, side), if (v) boxLine else cellLine)
                    drawLine(if (h) ink.boxLine else ink.gridLine, Offset(0f, i * cell), Offset(side, i * cell), if (h) boxLine else cellLine)
                }
                drawRoundRect(ink.boxLine, size = Size(side, side), cornerRadius = corner, style = Stroke(width = frame))
                val shown = current.value ?: return@onDrawBehind
                for (index in 0 until size * size) {
                    val value = shown.valueAt(index)
                    if (value !in 1..size) continue
                    val given = shown.isGiven(index)
                    val layout = (if (given) printed else pen)[value - 1]
                    val x = (index % size) * cell + (cell - layout.size.width) / 2f
                    val y = (index / size) * cell + (cell - layout.size.height) / 2f
                    drawText(layout, if (given) ink.printed else ink.pen, Offset(x, y))
                }
            }
        },
    )
}
