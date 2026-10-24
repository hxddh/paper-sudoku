package com.titpea.papersudoku.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import androidx.glance.unit.ColorProvider as GlanceColor
import com.titpea.papersudoku.MainActivity
import com.titpea.papersudoku.R
import com.titpea.papersudoku.SudokuApplication
import com.titpea.papersudoku.designsystem.FrauncesAxes
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Today's page on the home screen: the date, how far it has got, and a miniature of the page as
 * it stands. A tap opens the app on today's page.
 *
 * The miniature is drawn as three alpha masks (grid, printed digits, the player's ink), each tinted
 * with a day/night colour, so it follows the system theme without being redrawn.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(Small, Wide))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as SudokuApplication).container
        val date = LocalDate.now(container.clock)
        val key = Catalog.dailyKey(date.toEpochDay())
        val blank = PageSummary.of(GameState.new(container.puzzles.daily(date.toEpochDay())))
        val pages = container.gameStore.page(key)
        // Read once before the first frame, so a started page never flashes as blank.
        val initial = pages.first()
        NewDayAlarm.schedule(context, date)
        val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, context.resources.configuration.locales[0])
        // The date in Fraunces, as in the app (Glance text can't use a bundled font).
        val numeral = Numeral.render(context, date.dayOfMonth.toString())
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TODAY)
            // SINGLE_TOP with CLEAR_TOP: a running activity gets onNewIntent instead of being recreated.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        provideContent {
            // Read inside the composition, so an update to a running session shows the latest page.
            val saved by remember { pages }.collectAsState(initial = initial)
            val page = saved ?: blank
            SideEffect { shown.set(Shown(key, saved, context.resources.configuration.locales[0])) }
            val status = context.getString(
                when {
                    saved == null -> R.string.widget_ready
                    page.solved -> R.string.widget_stamped
                    else -> R.string.page_in_progress
                },
            )
            val layers = remember(page.grid) { PageLayers.render(context, page) }
            val size = LocalSize.current
            val wide = size.width >= Wide.width
            // The miniature fits the cell the launcher gives, down to the smallest size.
            val side = minOf(96.dp, size.height - 28.dp, if (wide) 96.dp else size.width - 28.dp).coerceAtLeast(40.dp)
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(Paper)
                    .then(systemCorners())
                    .padding(14.dp)
                    .clickable(actionStartActivity(open)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (wide) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(context.getString(R.string.today), style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        Image(
                            provider = ImageProvider(numeral.bitmap),
                            contentDescription = date.dayOfMonth.toString(),
                            colorFilter = ColorFilter.tint(InkColor),
                            modifier = GlanceModifier.size(numeral.width, numeral.height),
                        )
                        // At the shortest size the weekday gives way, so the status always fits.
                        if (size.height >= 130.dp) {
                            Text(weekday, style = TextStyle(color = Muted, fontSize = 13.sp))
                            Spacer(GlanceModifier.size(6.dp))
                        }
                        Text(status, style = TextStyle(color = InkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                    Spacer(GlanceModifier.width(8.dp))
                }
                Miniature(layers, side, context.getString(R.string.widget_page_description) + context.getString(R.string.list_separator) + status)
            }
        }
    }

    companion object {
        private val Small = DpSize(110.dp, 110.dp)
        private val Wide = DpSize(220.dp, 110.dp)

        private val Paper = ColorProvider(day = Color(0xFFF4EFE5), night = Color(0xFF14130F))
        // A step lighter than the widget's paper at night too, as in the app.
        private val Sheet = ColorProvider(day = Color(0xFFFFFDF8), night = Color(0xFF1C1A16))
        private val InkColor = ColorProvider(day = Color(0xFF1F1C18), night = Color(0xFFEDE6DA))
        private val Muted = ColorProvider(day = Color(0xFF5F574C), night = Color(0xFFCFC5B5))
        private val Accent = ColorProvider(day = Color(0xFFA8442F), night = Color(0xFFEFA489))
        private val GridInk = ColorProvider(day = Color(0xFF9C9081), night = Color(0xFF6E675B))
        private val PenInk = ColorProvider(day = Color(0xFF2D5577), night = Color(0xFFA9C8E8))

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val pending = MutableStateFlow(false)

        /** What a widget last composed: today's key, its saved page (null: not started) and the locale. */
        private data class Shown(val key: String, val page: PageSummary?, val locale: Locale)

        private val shown = AtomicReference<Shown?>(null)

        /**
         * Redraws placed widgets if today's page differs from what they last showed; overlapping
         * requests collapse into one. Cheap when none are placed, and when nothing changed (the
         * app asks on every put-away). The midnight and clock changes go through [updateNow].
         */
        fun requestUpdate(context: Context) {
            if (!pending.compareAndSet(expect = false, update = true)) return
            val app = context.applicationContext
            scope.launch {
                pending.value = false
                runCatching {
                    if (!showsCurrent(app)) TodayWidget().updateAll(app)
                }
            }
        }

        private suspend fun showsCurrent(app: Context): Boolean {
            val last = shown.get() ?: return false
            val container = (app as? SudokuApplication)?.container ?: return false
            val key = Catalog.dailyKey(LocalDate.now(container.clock).toEpochDay())
            val now = Shown(key, container.gameStore.page(key).first(), app.resources.configuration.locales[0])
            return now == last
        }

        /** Redraws placed widgets now, then calls [done] whether or not that succeeded. */
        fun updateNow(context: Context, done: () -> Unit) {
            val app = context.applicationContext
            scope.launch {
                try {
                    runCatching { TodayWidget().updateAll(app) }
                } finally {
                    done()
                }
            }
        }

        /** The launcher's own widget radius (Android 12+; older launchers draw square widgets). */
        private fun systemCorners(): GlanceModifier =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
            } else {
                GlanceModifier
            }

        @Composable
        private fun Miniature(layers: PageLayers, side: Dp, description: String) {
            Box(
                modifier = GlanceModifier.size(side).background(Sheet).cornerRadius(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Layer(layers.grid, GridInk, description)
                Layer(layers.printed, InkColor, null)
                Layer(layers.pen, PenInk, null)
            }
        }

        @Composable
        private fun Layer(bitmap: Bitmap, tint: GlanceColor, description: String?) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = description,
                colorFilter = ColorFilter.tint(tint),
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
    }
}

/** A page drawn as alpha masks for tinting. Glance cannot run Compose drawing. */
class PageLayers(val grid: Bitmap, val printed: Bitmap, val pen: Bitmap) {
    companion object {
        fun render(context: Context, page: PageSummary, px: Int = 240): PageLayers {
            val size = page.size
            val cell = px / size.toFloat()
            fun layer(draw: Canvas.(Paint) -> Unit): Bitmap =
                Bitmap.createBitmap(px, px, Bitmap.Config.ALPHA_8).also { Canvas(it).draw(Paint(Paint.ANTI_ALIAS_FLAG)) }

            val grid = layer { paint ->
                for (i in 1 until size) {
                    val v = i % page.boxCols == 0
                    val h = i % page.boxRows == 0
                    paint.strokeWidth = if (v) 3f else 1.5f
                    drawLine(i * cell, 0f, i * cell, px.toFloat(), paint)
                    paint.strokeWidth = if (h) 3f else 1.5f
                    drawLine(0f, i * cell, px.toFloat(), i * cell, paint)
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                drawRect(1.5f, 1.5f, px - 1.5f, px - 1.5f, paint)
            }
            val printedFace = ResourcesCompat.getFont(context, R.font.fraunces) ?: Typeface.SERIF
            val penFace = ResourcesCompat.getFont(context, R.font.fraunces_italic) ?: Typeface.SERIF
            fun digits(given: Boolean) = layer { paint ->
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = cell * 0.6f
                paint.typeface = if (given) printedFace else penFace
                paint.setFontVariationSettings(if (given) FrauncesAxes.PRINTED else FrauncesAxes.PEN)
                for (index in 0 until size * size) {
                    val value = page.valueAt(index)
                    if (value == 0 || page.isGiven(index) != given) continue
                    val cx = (index % size + 0.5f) * cell
                    val cy = (index / size + 0.5f) * cell - (paint.descent() + paint.ascent()) / 2f
                    drawText(value.toString(), cx, cy, paint)
                }
            }
            return PageLayers(grid, digits(given = true), digits(given = false))
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // A new day, or the clock or time zone moved: today's page may be a different one.
            NewDayAlarm.ACTION_NEW_DAY, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                // The broadcast may be all that keeps the process alive: hold it until the redraw
                // (which also schedules the next midnight) is done.
                val result = goAsync()
                TodayWidget.updateNow(context) { result.finish() }
            }
            else -> super.onReceive(context, intent)
        }
    }
}

/** Wakes the widget shortly after local midnight (inexact, so no exact-alarm permission). */
object NewDayAlarm {
    const val ACTION_NEW_DAY = "com.titpea.papersudoku.action.NEW_DAY"
    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    fun schedule(context: Context, today: LocalDate) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000
        val intent = Intent(context, TodayWidgetReceiver::class.java).setAction(ACTION_NEW_DAY)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.setWindow(AlarmManager.RTC, at, WINDOW_MILLIS, pending)
    }
}

/** A numeral drawn in Fraunces as an alpha mask, tinted like the miniature. */
class Numeral(val bitmap: Bitmap, val width: Dp, val height: Dp) {
    companion object {
        private const val SIZE_DP = 36f

        fun render(context: Context, text: String): Numeral {
            val density = context.resources.displayMetrics.density
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = ResourcesCompat.getFont(context, R.font.fraunces_display) ?: Typeface.SERIF
                setFontVariationSettings(FrauncesAxes.DISPLAY)
                textSize = SIZE_DP * density
                fontFeatureSettings = "lnum"
            }
            val bounds = android.graphics.Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val pad = (2 * density).toInt()
            val w = bounds.width() + pad * 2
            val h = bounds.height() + pad * 2
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
            Canvas(bitmap).drawText(text, (pad - bounds.left).toFloat(), (pad - bounds.top).toFloat(), paint)
            return Numeral(bitmap, (w / density).dp, (h / density).dp)
        }
    }
}
