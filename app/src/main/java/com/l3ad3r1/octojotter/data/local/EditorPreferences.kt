package com.l3ad3r1.octojotter.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.editorDataStore: DataStore<Preferences> by preferencesDataStore(name = "editor_settings")

/** Typography preferences for the Markdown editor surface (the toolbar's "Aa" menu). */
class EditorPreferences(private val context: Context) {

    val fontSize: Flow<Int> = context.editorDataStore.data
        .map { (it[FONT_SIZE_KEY] ?: DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE) }

    val monospace: Flow<Boolean> = context.editorDataStore.data
        .map { it[MONOSPACE_KEY] ?: true }

    val showLineNumbers: Flow<Boolean> = context.editorDataStore.data
        .map { it[LINE_NUMBERS_KEY] ?: false }

    suspend fun setFontSize(size: Int) {
        context.editorDataStore.edit { it[FONT_SIZE_KEY] = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE) }
    }

    suspend fun setMonospace(enabled: Boolean) {
        context.editorDataStore.edit { it[MONOSPACE_KEY] = enabled }
    }

    suspend fun setShowLineNumbers(enabled: Boolean) {
        context.editorDataStore.edit { it[LINE_NUMBERS_KEY] = enabled }
    }

    companion object {
        val FONT_SIZE_KEY = intPreferencesKey("editor_font_size")
        val MONOSPACE_KEY = booleanPreferencesKey("editor_monospace")
        val LINE_NUMBERS_KEY = booleanPreferencesKey("editor_line_numbers")

        const val DEFAULT_FONT_SIZE = 16
        const val MIN_FONT_SIZE = 12
        const val MAX_FONT_SIZE = 26
    }
}
