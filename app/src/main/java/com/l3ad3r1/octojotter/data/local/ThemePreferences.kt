package com.l3ad3r1.octojotter.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

class ThemePreferences(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        val FONT_FAMILY_KEY = stringPreferencesKey("font_family")
        // Not one of the six real choices — "keep the built-in serif headline
        // / sans body pairing" until the user actually picks a font.
        const val DEFAULT_FONT_FAMILY = "default"

        val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
        const val DEFAULT_FONT_SCALE = 1.0f
    }

    val themeMode: Flow<String> = context.themeDataStore.data
        .map { preferences ->
            preferences[THEME_MODE_KEY] ?: THEME_SYSTEM
        }

    suspend fun setThemeMode(mode: String) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    val fontFamily: Flow<String> = context.themeDataStore.data
        .map { preferences -> preferences[FONT_FAMILY_KEY] ?: DEFAULT_FONT_FAMILY }

    suspend fun setFontFamily(id: String) {
        context.themeDataStore.edit { preferences -> preferences[FONT_FAMILY_KEY] = id }
    }

    // A scale applied on top of each role's own size — 1.0 is the size the
    // type scale in Type.kt already specifies, not an absolute sp value, so
    // Display and Label stay proportional to each other as it moves.
    val fontScale: Flow<Float> = context.themeDataStore.data
        .map { preferences -> preferences[FONT_SCALE_KEY] ?: DEFAULT_FONT_SCALE }

    suspend fun setFontScale(scale: Float) {
        context.themeDataStore.edit { preferences -> preferences[FONT_SCALE_KEY] = scale }
    }
}
