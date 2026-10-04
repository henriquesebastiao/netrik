package com.netrik.core.terminal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.terminalPrefs: DataStore<Preferences> by preferencesDataStore(name = "terminal")

/** Terminal preferences (for now, the font size from the "Font" menu). */
@Singleton
class TerminalPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {

    val fontSize: Flow<Int> = context.terminalPrefs.data.map { prefs -> TerminalFont.clamp(prefs[FONT_SIZE] ?: TerminalFont.DEFAULT) }

    suspend fun setFontSize(size: Int) {
        context.terminalPrefs.edit { it[FONT_SIZE] = TerminalFont.clamp(size) }
    }

    private companion object {
        val FONT_SIZE = intPreferencesKey("font_size")
    }
}
