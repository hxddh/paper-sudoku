package com.titpea.papersudoku.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
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
    val measurer = rememberTextMeasurer(cacheSize = 32)
    Canvas(modifier.aspectRatio(1f)) {
        val side = this.size.minDimension
        val cell = side / size
        drawRoundRect(ink.sheet, size = Size(side, side), cornerRadius = CornerRadius(side * 0.04f))
        for (i in 1 until size) {
            val v = i % boxCols == 0
            val h = i % boxRows == 0
            drawLine(if (v) ink.boxLine else ink.gridLine, Offset(i * cell, 0f), Offset(i * cell, side), if (v) 1.5f else 0.7f)
            drawLine(if (h) ink.boxLine else ink.gridLine, Offset(0f, i * cell), Offset(side, i * cell), if (h) 1.5f else 0.7f)
        }
        drawRoundRect(
            ink.boxLine,
            size = Size(side, side),
            cornerRadius = CornerRadius(side * 0.04f),
            style = Stroke(width = 1.5f),
        )
        if (page == null) return@Canvas
        // Pixels → sp through the density, so the digits fit the cell at any font scale.
        val fontSize = (cell * 0.62f).toSp()
        for (index in 0 until size * size) {
            val value = page.valueAt(index)
            if (value == 0) continue
            val given = page.isGiven(index)
            val layout = measurer.measure(
                value.toString(),
                TextStyle(
                    fontFamily = if (given) FrauncesPrinted else FrauncesPen,
                    fontSize = fontSize,
                    color = if (given) ink.printed else ink.pen,
                ),
            )
            val x = (index % size) * cell + (cell - layout.size.width) / 2f
            val y = (index / size) * cell + (cell - layout.size.height) / 2f
            drawText(layout, topLeft = Offset(x, y))
        }
    }
}
