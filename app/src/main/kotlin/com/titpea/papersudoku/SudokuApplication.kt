package com.titpea.papersudoku

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.titpea.papersudoku.data.CachedPuzzleSource
import com.titpea.papersudoku.data.DataStoreSettings
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.RoomGameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.SudokuDatabase
import com.titpea.papersudoku.game.model.PuzzleSource
import com.titpea.papersudoku.widget.TodayWidget
import java.io.File
import java.time.Clock

interface AppContainer {
    val settingsStore: SettingsStore
    val gameStore: GameStore
    val puzzles: PuzzleSource
    val clock: Clock

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
    override val clock: Clock = Clock.systemDefaultZone()
    override val ticker: () -> Long = { SystemClock.elapsedRealtime() }
    override val onDailySaved: () -> Unit = { TodayWidget.requestUpdate(context) }
}

class SudokuApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
