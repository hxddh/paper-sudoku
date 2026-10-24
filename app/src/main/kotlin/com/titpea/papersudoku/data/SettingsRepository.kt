package com.titpea.papersudoku.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Learner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The one-line hints shown the first time something happens on a page (11.0). */
enum class FirstHint { Pencil, PickUp, Coach }

/**
 * What the app remembers about the player, not choices they make: there are no settings (11.0).
 * The theme follows the system, pencil marks are always tidied, language is the system's per-app
 * language setting, and haptics follow the system's touch-feedback setting.
 */
data class UserSettings(
    /** Techniques met in solved puzzles or explained by the Coach. */
    val techniquesMet: Set<Technique> = emptySet(),
    /** What the player has shown they know, per technique (10.0). */
    val learner: Learner = Learner(),
    /** The player has written a first digit; the one-line how-to stops showing. */
    val wroteFirstDigit: Boolean = false,
    /** The first-time hints already shown; each is shown once. */
    val hintsSeen: Set<FirstHint> = emptySet(),
)

interface SettingsStore {
    val settings: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
}

/** A corrupt file is replaced with defaults instead of failing every later write. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreSettings(context: Context) : SettingsStore {

    private val dataStore = context.settingsDataStore

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { p ->
            val next = transform(p.toSettings())
            // The settings 10.0 had are gone: their keys are dropped on the first write.
            p.remove(THEME_MODE)
            p.remove(AUTO_CLEAR_NOTES)
            p[TECHNIQUES_MET] = next.techniquesMet.map { it.name }.toSet()
            p[WROTE_FIRST_DIGIT] = next.wroteFirstDigit
            p[LEARNER] = next.learner.encode()
            p[HINTS_SEEN] = next.hintsSeen.map { it.name }.toSet()
        }
    }

    private fun Preferences.toSettings() = UserSettings(
        techniquesMet = this[TECHNIQUES_MET].orEmpty()
            .mapNotNull { name -> Technique.entries.firstOrNull { it.name == name } }
            .toSet(),
        wroteFirstDigit = this[WROTE_FIRST_DIGIT] ?: false,
        hintsSeen = this[HINTS_SEEN].orEmpty()
            .mapNotNull { name -> FirstHint.entries.firstOrNull { it.name == name } }
            .toSet(),
        // 9.0 kept clean pages per technique instead; they carry over until the first new record.
        learner = Learner.decode(this[LEARNER]) ?: Learner.from9(
            this[CLEAN_PAGES].orEmpty().mapNotNull { entry ->
                val technique = Technique.entries.firstOrNull { it.name == entry.substringBefore('=') }
                val count = entry.substringAfter('=', "").toIntOrNull()
                if (technique != null && count != null) technique to count else null
            }.toMap(),
        ),
    )

    private companion object {
        // Keys are shared with 2.x; keys older versions wrote that are no longer read ("haptics",
        // dropped in 4.0) are simply ignored. The theme and tidy-pencil settings (gone in 11.0)
        // are never read, only removed.
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_CLEAR_NOTES = booleanPreferencesKey("auto_clear_notes")
        val TECHNIQUES_MET = stringSetPreferencesKey("techniques_met")
        val WROTE_FIRST_DIGIT = booleanPreferencesKey("wrote_first_digit")
        /** 9.0: "Technique=count" entries, read once to seed [LEARNER]. */
        val CLEAN_PAGES = stringSetPreferencesKey("clean_pages")
        val LEARNER = stringPreferencesKey("learner")
        val HINTS_SEEN = stringSetPreferencesKey("hints_seen")
    }
}
