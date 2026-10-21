package com.titpea.papersudoku.share

import android.content.Context
import android.content.Intent
import com.titpea.papersudoku.R
import com.titpea.papersudoku.game.difficultyRes
import com.titpea.papersudoku.game.formatElapsed
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Spoiler-free text for the system share sheet. No link, no tracking. */
fun shareText(context: Context, game: GameState): String {
    val res = context.resources
    val spec = game.spec
    val difficulty = res.getString(difficultyRes(spec.difficulty))
    val time = formatElapsed(game.elapsedMillis / 1_000)
    val coach = res.getString(if (game.coach.deepest == 0) R.string.share_clean else R.string.share_coached)
    val day = spec.epochDay
    val heading = if (spec.kind == PuzzleKind.Daily && day != null) {
        val date = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        res.getString(R.string.share_heading_daily, date)
    } else {
        res.getString(R.string.share_heading_classic, (spec.number ?: 0) + 1)
    }
    return res.getString(R.string.share_body, heading, time, difficulty, coach)
}

fun sharePage(context: Context, game: GameState) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText(context, game))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
}
