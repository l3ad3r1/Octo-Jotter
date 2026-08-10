package com.l3ad3r1.octojotter.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.l3ad3r1.octojotter.ai.model.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/** Persists on-device AI choices (currently the selected chat/LLM model). */
class AiPreferences(private val context: Context) {

    companion object {
        val SELECTED_CHAT_MODEL_KEY = stringPreferencesKey("selected_chat_model_id")
    }

    val selectedChatModelId: Flow<String> = context.aiDataStore.data
        .map { prefs -> prefs[SELECTED_CHAT_MODEL_KEY] ?: ModelCatalog.DEFAULT_CHAT.id }

    suspend fun setSelectedChatModel(id: String) {
        context.aiDataStore.edit { prefs -> prefs[SELECTED_CHAT_MODEL_KEY] = id }
    }
}
