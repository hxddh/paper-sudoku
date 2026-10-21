package com.titpea.papersudoku

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.titpea.papersudoku.data.DataStoreSettings
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.RoomGameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.SudokuDatabase
import java.time.Clock

interface AppContainer {
    val settingsStore: SettingsStore
    val gameStore: GameStore
    val clock: Clock

    /** Monotonic milliseconds for the game clock; immune to wall-clock changes. */
    val ticker: () -> Long
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database = SudokuDatabase.create(context)

    override val settingsStore: SettingsStore = DataStoreSettings(context)
    override val gameStore: GameStore = RoomGameStore(database.savedGameDao(), database.resultDao())
    override val clock: Clock = Clock.systemDefaultZone()
    override val ticker: () -> Long = { SystemClock.elapsedRealtime() }
}

class SudokuApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
