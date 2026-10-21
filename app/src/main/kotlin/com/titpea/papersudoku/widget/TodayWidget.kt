package com.titpea.papersudoku.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.titpea.papersudoku.MainActivity
import com.titpea.papersudoku.R
import com.titpea.papersudoku.SudokuApplication
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle

/**
 * Today's page on the home screen: the date, how far it has got, and a miniature of the page as
 * it stands. A tap opens the app on that page.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as SudokuApplication).container
        val date = LocalDate.now(container.clock)
        val saved = container.gameStore.load(Catalog.dailyKey(date.toEpochDay()))
        val game = saved ?: GameState.new(Catalog.daily(date.toEpochDay()))
        val status = context.getString(
            when {
                saved == null -> R.string.page_blank
                saved.solved -> R.string.widget_stamped
                else -> R.string.page_in_progress
            },
        )
        val bitmap = renderPage(context, game)
        val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, context.resources.configuration.locales[0])

        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Paper)
                    .cornerRadius(24.dp)
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(context.getString(R.string.today), style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    Text(date.dayOfMonth.toString(), style = TextStyle(color = InkColor, fontSize = 34.sp))
                    Text(weekday, style = TextStyle(color = Muted, fontSize = 13.sp))
                    Spacer(GlanceModifier.size(6.dp))
                    Text(status, style = TextStyle(color = InkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
                Spacer(GlanceModifier.width(8.dp))
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = context.getString(R.string.widget_page_description),
                    modifier = GlanceModifier.size(96.dp),
                )
            }
        }
    }

    companion object {
        private val Paper = ColorProvider(day = Color(0xFFF4EFE5), night = Color(0xFF14130F))
        private val InkColor = ColorProvider(day = Color(0xFF1F1C18), night = Color(0xFFEDE6DA))
        private val Muted = ColorProvider(day = Color(0xFF5F574C), night = Color(0xFFCFC5B5))
        private val Accent = ColorProvider(day = Color(0xFFA8442F), night = Color(0xFFEFA489))

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Redraws placed widgets; cheap when none are placed. */
        fun requestUpdate(context: Context) {
            val app = context.applicationContext
            scope.launch { runCatching { TodayWidget().updateAll(app) } }
        }

        /** Draws the page with the platform canvas (Glance cannot run Compose drawing). */
        fun renderPage(context: Context, game: GameState, px: Int = 288): Bitmap {
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val sheet = if (night) 0xFF0F0E0A.toInt() else 0xFFFFFDF8.toInt()
            val line = if (night) 0xFF4B463D.toInt() else 0xFFD3C8B8.toInt()
            val box = if (night) 0xFF9A9080.toInt() else 0xFF8E8273.toInt()
            val printed = if (night) 0xFFEDE6DA.toInt() else 0xFF1F1C18.toInt()
            val pen = if (night) 0xFFA9C8E8.toInt() else 0xFF2D5577.toInt()
            val size = game.size
            val cell = px / size.toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = sheet
            canvas.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.05f, px * 0.05f, paint)
            for (i in 1 until size) {
                val v = i % game.spec.boxCols == 0
                val h = i % game.spec.boxRows == 0
                paint.strokeWidth = if (v) 3f else 1.5f
                paint.color = if (v) box else line
                canvas.drawLine(i * cell, 0f, i * cell, px.toFloat(), paint)
                paint.strokeWidth = if (h) 3f else 1.5f
                paint.color = if (h) box else line
                canvas.drawLine(0f, i * cell, px.toFloat(), i * cell, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = box
            canvas.drawRoundRect(RectF(1.5f, 1.5f, px - 1.5f, px - 1.5f), px * 0.05f, px * 0.05f, paint)
            paint.style = Paint.Style.FILL
            val printedFace = ResourcesCompat.getFont(context, R.font.fraunces) ?: Typeface.SERIF
            val penFace = ResourcesCompat.getFont(context, R.font.fraunces_italic) ?: Typeface.SERIF
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = cell * 0.6f
            for (index in game.values.indices) {
                val value = game.values[index]
                if (value == 0) continue
                val given = game.isGiven(index)
                paint.typeface = if (given) printedFace else penFace
                paint.color = if (given) printed else pen
                val cx = (index % size + 0.5f) * cell
                val cy = (index / size + 0.5f) * cell - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(value.toString(), cx, cy, paint)
            }
            return bitmap
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
