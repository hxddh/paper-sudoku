package com.titpea.papersudoku

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.titpea.papersudoku.data.CachedPuzzleSource
import com.titpea.papersudoku.data.DataStoreSettings
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.RoomGameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.SudokuDatabase
import com.titpea.papersudoku.game.model.PuzzleSource
import com.titpea.papersudoku.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

interface AppContainer {
    val settingsStore: SettingsStore
    val gameStore: GameStore
    val puzzles: PuzzleSource
    val clock: Clock

    /** The day the app was installed, as epoch day: where the notebook begins. */
    val firstDay: Long

    /** Monotonic milliseconds for the game clock; immune to wall-clock changes. */
    val ticker: () -> Long

    /** Called when today's daily is solved or put away, so the home-screen widget can redraw. */
    val onDailySaved: () -> Unit
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database = SudokuDatabase.create(context)

    override val settingsStore: SettingsStore = DataStoreSettings(context)
    override val gameStore: GameStore = RoomGameStore(database)
    override val puzzles: PuzzleSource = CachedPuzzleSource(File(context.cacheDir, "puzzles"))
    override val clock: Clock = SystemZoneClock
    override val firstDay: Long by lazy {
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }
            .getOrDefault(System.currentTimeMillis())
        Instant.ofEpochMilli(installed).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }
    override val ticker: () -> Long = { SystemClock.elapsedRealtime() }
    override val onDailySaved: () -> Unit = { TodayWidget.requestUpdate(context) }

    /**
     * Starts the first DataStore read and opens Room (running any migration) off the main thread,
     * so both overlap the Activity's start instead of following it: the splash waits for the
     * settings, and the first page for the database. Later reads share the open database and
     * DataStore's cached data; a failure here is only logged, and the real read retries it.
     */
    fun warmUp(scope: CoroutineScope) {
        scope.launch {
            runCatching { settingsStore.settings.first() }
                .onFailure { Log.w(TAG, "Settings warm-up failed", it) }
        }
        scope.launch {
            runCatching { database.openHelper.writableDatabase }
                .onFailure { Log.w(TAG, "Database warm-up failed", it) }
        }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}

/** The system clock in whatever time zone the device is in now, so "today" follows travel. */
private object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}

class SudokuApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Trace sections ("ps.*") are what the start-up benchmark measures beyond its timings.
        Trace.beginSection("ps.appCreate")
        try {
            container = DefaultAppContainer(this).also {
                it.warmUp(CoroutineScope(SupervisorJob() + Dispatchers.IO))
            }
        } finally {
            Trace.endSection()
        }
    }
}
