package com.titpea.papersudoku.share

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.res.ResourcesCompat
import com.titpea.papersudoku.R
import com.titpea.papersudoku.game.difficultyRes
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Paper Sudoku, literally: prints the puzzle's givens on one page through the system print
 * service (or "Save as PDF"). Nothing leaves the device unless the user picks a printer.
 */
fun printPuzzle(context: Context, game: GameState) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val title = printTitle(context, game)
    manager.print(
        title,
        PuzzlePrintAdapter(context.applicationContext, game, title),
        PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
    )
}

private fun printTitle(context: Context, game: GameState): String {
    val spec = game.spec
    val difficulty = context.getString(difficultyRes(spec.difficulty))
    val day = spec.epochDay
    return if (spec.kind == PuzzleKind.Daily && day != null) {
        val date = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        context.getString(R.string.print_title_daily, date, difficulty)
    } else {
        context.getString(R.string.print_title_classic, (spec.number ?: 0) + 1, difficulty)
    }
}

private class PuzzlePrintAdapter(
    private val context: Context,
    private val game: GameState,
    private val title: String,
) : PrintDocumentAdapter() {

    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        val info = PrintDocumentInfo.Builder("paper-sudoku.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val pdf = PrintedPdfDocument(context, attributes ?: return callback.onWriteFailed(null))
        try {
            val page = pdf.startPage(0)
            drawPage(page.canvas, page.info.pageWidth.toFloat(), page.info.pageHeight.toFloat())
            pdf.finishPage(page)
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }
            FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
        }
    }

    /** Units are PostScript points (1/72 inch). */
    private fun drawPage(canvas: Canvas, width: Float, height: Float) {
        val printed = ResourcesCompat.getFont(context, R.font.fraunces) ?: Typeface.SERIF
        val margin = 54f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = printed
            textSize = 20f
            color = INK
        }
        canvas.drawText(title, margin, margin + 20f, titlePaint)

        val size = game.size
        val side = minOf(width - 2 * margin, height - 2 * margin - 120f)
        val left = (width - side) / 2f
        val top = margin + 60f
        val cell = side / size
        val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            strokeWidth = 0.6f
        }
        val thick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            strokeWidth = 1.8f
        }
        for (i in 0..size) {
            val x = left + i * cell
            val y = top + i * cell
            canvas.drawLine(x, top, x, top + side, if (i % game.spec.boxCols == 0) thick else thin)
            canvas.drawLine(left, y, left + side, y, if (i % game.spec.boxRows == 0) thick else thin)
        }
        val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = printed
            textSize = cell * 0.55f
            color = INK
            textAlign = Paint.Align.CENTER
        }
        val baseline = (digitPaint.descent() + digitPaint.ascent()) / 2f
        for (index in game.spec.givens.indices) {
            val digit = game.spec.givens[index]
            if (digit == 0) continue
            val cx = left + (index % size + 0.5f) * cell
            val cy = top + (index / size + 0.5f) * cell - baseline
            canvas.drawText(digit.toString(), cx, cy, digitPaint)
        }
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = MUTED
        }
        canvas.drawText(context.getString(R.string.print_footer), margin, height - margin, footer)
    }

    private companion object {
        const val INK = 0xFF1F1C18.toInt()
        const val MUTED = 0xFF5F574C.toInt()
    }
}
