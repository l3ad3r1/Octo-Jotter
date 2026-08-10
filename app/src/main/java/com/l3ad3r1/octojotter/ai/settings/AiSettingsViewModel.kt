package com.l3ad3r1.octojotter.ai.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.l3ad3r1.octojotter.ai.AiContainer
import com.l3ad3r1.octojotter.ai.index.NoteIndexingWorker
import com.l3ad3r1.octojotter.ai.model.ChatModel
import com.l3ad3r1.octojotter.ai.model.EmbeddingModel
import com.l3ad3r1.octojotter.ai.model.ModelCatalog
import com.l3ad3r1.octojotter.ai.model.ModelManager
import com.l3ad3r1.octojotter.ai.model.ModelStorage
import com.l3ad3r1.octojotter.data.local.AiPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the in-app "On-device AI" settings screen: capability status, the
 * shared-storage (Hermes) grant, the embedding model, and the chat/LLM model
 * catalog with per-model download and selection.
 */
class AiSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val ai = AiContainer.get(application)
    private val manager: ModelManager = ai.modelManager
    private val prefs = AiPreferences(application)

    val statusLine: String = ai.capability.describe()
    val supportsAi: Boolean = ai.capability.supportsAi

    val embedding: EmbeddingModel = ModelCatalog.EMBEDDING
    val chatModels: List<ChatModel> = ModelCatalog.CHAT_MODELS

    /** Progress/idle state per download target (key = "embedding" or a chat model id). */
    sealed interface Download {
        data object Idle : Download
        data class InProgress(val fraction: Float?) : Download
        data class Failed(val message: String) : Download
    }

    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    val downloads: StateFlow<Map<String, Download>> = _downloads.asStateFlow()

    /** Bumped after any change so the screen re-reads on-disk presence. */
    private val _refresh = MutableStateFlow(0)
    val refresh: StateFlow<Int> = _refresh.asStateFlow()

    val selectedChatModelId: StateFlow<String> =
        prefs.selectedChatModelId.stateIn(viewModelScope, SharingStarted.Eagerly, ModelCatalog.DEFAULT_CHAT.id)

    fun refresh() { _refresh.value += 1 }

    // --- storage (shared "AI Models" folder used by Hermes) ---

    fun hasSharedAccess(): Boolean = manager.storage.hasSharedAccess()
    fun usingSharedStorage(): Boolean = manager.storage.usingSharedStorage
    fun grantStorageAccessIntent(): Intent = ModelStorage.allFilesAccessIntent(getApplication())

    // --- presence ---

    fun isEmbeddingReady(): Boolean = manager.isEmbeddingReady(embedding)
    fun isChatPresent(model: ChatModel): Boolean = manager.isChatModelPresent(model)

    // --- downloads ---

    fun downloadEmbedding() = download("embedding") {
        manager.downloadEmbeddingModel(embedding) { p -> setProgress("embedding", p.fraction) }
    }

    fun downloadChat(model: ChatModel) = download(model.id) {
        manager.downloadChatModel(model) { p -> setProgress(model.id, p.fraction) }
    }

    private fun download(key: String, block: suspend () -> ModelManager.Result) {
        if (_downloads.value[key] is Download.InProgress) return
        viewModelScope.launch {
            setDownload(key, Download.InProgress(null))
            when (val r = block()) {
                is ModelManager.Result.Failure -> setDownload(key, Download.Failed(r.message))
                else -> {
                    setDownload(key, Download.Idle)
                    refresh()
                    // A new embedding model means the note index must be rebuilt.
                    if (key == "embedding") NoteIndexingWorker.enqueue(getApplication())
                }
            }
        }
    }

    private fun setProgress(key: String, fraction: Float?) =
        setDownload(key, Download.InProgress(fraction))

    private fun setDownload(key: String, state: Download) {
        _downloads.value = _downloads.value.toMutableMap().apply { put(key, state) }
    }

    // --- selection ---

    fun selectChatModel(id: String) {
        viewModelScope.launch { prefs.setSelectedChatModel(id) }
    }
}
