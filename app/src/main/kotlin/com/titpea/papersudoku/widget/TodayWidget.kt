package com.titpea.papersudoku.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.titpea.papersudoku.MainActivity
import com.titpea.papersudoku.R
import com.titpea.papersudoku.SudokuApplication
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.designsystem.FrauncesAxes
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
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Today's page on the home screen: the date, how far it has got, and a miniature of the page as
 * it stands. A tap opens the app on today's page.
 *
 * Plain RemoteViews (layout/widget_today_*): the miniature is three alpha masks (grid, printed
 * digits, the player's ink), each tinted with a day/night colour resource, so it follows the
 * system theme without being redrawn. Redrawn when the app saves today's page, at midnight and
 * when the clock or time zone moves; nothing is scheduled beyond the midnight alarm.
 */
object TodayWidget {
    private const val TAG = "TodayWidget"

    /** The miniature alone below this width (dp); the date and status beside it from here. */
    private const val WIDE_DP = 220f
    private const val HEIGHT_DP = 110f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = MutableStateFlow(false)

    /** What the widgets last showed: today's key, its saved page (null: not started) and the locale. */
    private data class Shown(val key: String, val page: PageSummary?, val locale: Locale)

    private val shown = AtomicReference<Shown?>(null)

    /**
     * Redraws placed widgets if today's page differs from what they last showed; overlapping
     * requests collapse into one. Cheap when none are placed, and when nothing changed (the app
     * asks on every put-away). The midnight and clock changes go through [updateNow].
     */
    fun requestUpdate(context: Context) {
        if (!pending.compareAndSet(expect = false, update = true)) return
        val app = context.applicationContext
        scope.launch {
            pending.value = false
            runCatching { if (placed(app).isNotEmpty() && !showsCurrent(app)) update(app) }
                .onFailure { Log.w(TAG, "Could not redraw the widget", it) }
        }
    }

    /** Redraws placed widgets now, then calls [done] whether or not that succeeded. */
    fun updateNow(context: Context, done: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            try {
                runCatching { update(app) }.onFailure { Log.w(TAG, "Could not redraw the widget", it) }
            } finally {
                done()
            }
        }
    }

    private fun placed(app: Context): IntArray =
        AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, TodayWidgetReceiver::class.java))

    private suspend fun showsCurrent(app: Context): Boolean {
        val last = shown.get() ?: return false
        val container = (app as? SudokuApplication)?.container ?: return false
        val key = Catalog.dailyKey(LocalDate.now(container.clock).toEpochDay())
        val now = Shown(key, container.gameStore.page(key).first(), app.resources.configuration.locales[0])
        return now == last
    }

    private suspend fun update(app: Context) {
        val manager = AppWidgetManager.getInstance(app)
        val ids = placed(app)
        if (ids.isEmpty()) return
        val container = (app as SudokuApplication).container
        val date = LocalDate.now(container.clock)
        val key = Catalog.dailyKey(date.toEpochDay())
        val saved = container.gameStore.page(key).first()
        val page = saved ?: PageSummary.of(GameState.new(container.puzzles.daily(date.toEpochDay())))
        val locale = app.resources.configuration.locales[0]
        NewDayAlarm.schedule(app, date)
        val status = app.getString(
            when {
                saved == null -> R.string.widget_ready
                page.solved -> R.string.widget_stamped
                else -> R.string.page_in_progress
            },
        )
        val content = Content(
            layers = PageLayers.render(app, page),
            numeral = Numeral.render(app, date.dayOfMonth.toString()),
            day = date.dayOfMonth.toString(),
            status = status,
            description = app.getString(R.string.widget_page_description) + app.getString(R.string.list_separator) + status,
            open = openToday(app),
        )
        for (id in ids) manager.updateAppWidget(id, views(app, content, manager.getAppWidgetOptions(id)))
        shown.set(Shown(key, saved, locale))
    }

    private class Content(
        val layers: PageLayers,
        val numeral: Numeral,
        val day: String,
        val status: String,
        val description: String,
        val open: PendingIntent,
    )

    /**
     * The layouts for the sizes the launcher may give: Android 12+ picks among them itself as the
     * widget is resized; before that, one is chosen per orientation from the widget's options.
     */
    private fun views(app: Context, content: Content, options: Bundle): RemoteViews {
        fun at(widthDp: Float) = if (widthDp >= WIDE_DP) wide(app, content) else small(app, content)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(mapOf(SizeF(HEIGHT_DP, HEIGHT_DP) to small(app, content), SizeF(WIDE_DP, HEIGHT_DP) to wide(app, content)))
        } else {
            val portrait = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
            val landscape = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
            RemoteViews(at(landscape), at(portrait))
        }
    }

    private fun small(app: Context, content: Content) =
        RemoteViews(app.packageName, R.layout.widget_today_small).apply { page(content) }

    private fun wide(app: Context, content: Content) =
        RemoteViews(app.packageName, R.layout.widget_today_wide).apply {
            setImageViewBitmap(R.id.widget_numeral, content.numeral.bitmap)
            setContentDescription(R.id.widget_numeral, content.day)
            setTextViewText(R.id.widget_status, content.status)
            page(content)
        }

    private fun RemoteViews.page(content: Content) {
        setImageViewBitmap(R.id.widget_grid, content.layers.grid)
        setImageViewBitmap(R.id.widget_printed, content.layers.printed)
        setImageViewBitmap(R.id.widget_pen, content.layers.pen)
        setContentDescription(R.id.widget_page, content.description)
        setOnClickPendingIntent(android.R.id.background, content.open)
    }

    private fun openToday(app: Context): PendingIntent {
        val open = Intent(app, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TODAY)
            // SINGLE_TOP with CLEAR_TOP: a running activity gets onNewIntent instead of being recreated.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** A page drawn as alpha masks for tinting. */
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

/**
 * The widget's receiver. Its name is kept from the Glance widget it replaced, so widgets already
 * placed carry on.
 */
class TodayWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // A new day, or the clock or time zone moved: today's page may be a different one.
            NewDayAlarm.ACTION_NEW_DAY, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> redraw(context)
            else -> super.onReceive(context, intent)
        }
    }

    // Placed, restored, or after a reboot or an app update.
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = redraw(context)

    // Resized: before Android 12 the layout for the new size is chosen here.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) redraw(context)
    }

    /**
     * The broadcast may be all that keeps the process alive: hold it until the redraw (which also
     * schedules the next midnight) is done. Called only while a broadcast is being received.
     */
    private fun redraw(context: Context) {
        val result = goAsync()
        TodayWidget.updateNow(context) { result.finish() }
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
class Numeral(val bitmap: Bitmap) {
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
            val bitmap = Bitmap.createBitmap(bounds.width() + pad * 2, bounds.height() + pad * 2, Bitmap.Config.ALPHA_8)
            Canvas(bitmap).drawText(text, (pad - bounds.left).toFloat(), (pad - bounds.top).toFloat(), paint)
            return Numeral(bitmap)
        }
    }
}
