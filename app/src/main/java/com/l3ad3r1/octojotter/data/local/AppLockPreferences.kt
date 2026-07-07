package com.l3ad3r1.octojotter.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock_preferences")

class AppLockPreferences(private val context: Context) {
    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")

    val appLockEnabled: Flow<Boolean> = context.appLockDataStore.data.map { preferences ->
        preferences[appLockEnabledKey] ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.appLockDataStore.edit { preferences ->
            preferences[appLockEnabledKey] = enabled
        }
    }
}
