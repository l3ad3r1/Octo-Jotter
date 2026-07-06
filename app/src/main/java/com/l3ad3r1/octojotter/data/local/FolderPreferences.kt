package com.l3ad3r1.octojotter.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.folderDataStore: DataStore<Preferences> by preferencesDataStore(name = "folder_settings")

class FolderPreferences(private val context: Context) {
    companion object {
        val FOLDERS_KEY = stringSetPreferencesKey("custom_folders")
    }

    val customFolders: Flow<Set<String>> = context.folderDataStore.data
        .map { preferences ->
            preferences[FOLDERS_KEY] ?: emptySet()
        }

    suspend fun addFolder(folder: String) {
        if (folder.isBlank()) return
        context.folderDataStore.edit { preferences ->
            val current = preferences[FOLDERS_KEY] ?: emptySet()
            preferences[FOLDERS_KEY] = current + folder.trim()
        }
    }

    suspend fun deleteFolder(folder: String) {
        context.folderDataStore.edit { preferences ->
            val current = preferences[FOLDERS_KEY] ?: emptySet()
            preferences[FOLDERS_KEY] = current - folder
        }
    }
}
