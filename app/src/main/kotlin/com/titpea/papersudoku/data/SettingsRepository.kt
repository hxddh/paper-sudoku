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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { System, Light, Dark }

/**
 * Deliberately few settings: Paper is the product's look, the timer stays out of sight, and
 * mistakes are the Coach's job. Language is the system's per-app language setting, and haptics
 * follow the system's touch-feedback setting.
 */
data class UserSettings(
    val theme: ThemeMode = ThemeMode.System,
    val autoClearNotes: Boolean = true,
    /** Techniques met in solved puzzles or explained by the Coach. */
    val techniquesMet: Set<Technique> = emptySet(),
    /** The player has written a first digit; the one-line how-to stops showing. */
    val wroteFirstDigit: Boolean = false,
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
            p[THEME_MODE] = next.theme.name
            p[AUTO_CLEAR_NOTES] = next.autoClearNotes
            p[TECHNIQUES_MET] = next.techniquesMet.map { it.name }.toSet()
            p[WROTE_FIRST_DIGIT] = next.wroteFirstDigit
        }
    }

    private fun Preferences.toSettings() = UserSettings(
        theme = ThemeMode.entries.firstOrNull { it.name == this[THEME_MODE] } ?: ThemeMode.System,
        autoClearNotes = this[AUTO_CLEAR_NOTES] ?: true,
        techniquesMet = this[TECHNIQUES_MET].orEmpty()
            .mapNotNull { name -> Technique.entries.firstOrNull { it.name == name } }
            .toSet(),
        wroteFirstDigit = this[WROTE_FIRST_DIGIT] ?: false,
    )

    private companion object {
        // Keys are shared with 2.x; keys older versions wrote that are no longer read ("haptics",
        // dropped in 4.0) are simply ignored.
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_CLEAR_NOTES = booleanPreferencesKey("auto_clear_notes")
        val TECHNIQUES_MET = stringSetPreferencesKey("techniques_met")
        val WROTE_FIRST_DIGIT = booleanPreferencesKey("wrote_first_digit")
    }
}
