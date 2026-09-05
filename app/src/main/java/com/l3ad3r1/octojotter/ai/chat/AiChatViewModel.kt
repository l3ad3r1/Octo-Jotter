package com.l3ad3r1.octojotter.ai.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.l3ad3r1.octojotter.ai.AiContainer
import com.l3ad3r1.octojotter.ai.index.NoteIndexingWorker
import com.l3ad3r1.octojotter.ai.model.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the on-device RAG chat screen (Phase 2). Retrieval + generation run
 * entirely on-device via [AiContainer.ragChat]; the chat GGUF is downloaded on
 * first use.
 */
class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val ai = AiContainer.get(application)

    data class Message(
        val role: Role,
        val text: String,
        val citations: List<Citation> = emptyList(),
        val streaming: Boolean = false,
    ) {
        enum class Role { USER, ASSISTANT }
    }

    /** Overall capability: needs arm64 + enough RAM (see AiCapability). */
    val chatSupported: Boolean = ai.capability.supportsChat

    val chatModelName: String = ai.chatModel.displayName
    val chatModelSizeLabel: String = ai.chatModel.file.sizeLabel

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _chatModelReady = MutableStateFlow(ai.isChatModelReady())
    val chatModelReady: StateFlow<Boolean> = _chatModelReady.asStateFlow()

    sealed interface Download {
        data object Idle : Download
        data class InProgress(val fraction: Float?) : Download
        data class Failed(val message: String) : Download
    }

    private val _download = MutableStateFlow<Download>(Download.Idle)
    val download: StateFlow<Download> = _download.asStateFlow()

    fun refreshChatModelReady() {
        _chatModelReady.value = ai.isChatModelReady()
    }

    fun downloadChatModel() {
        if (_download.value is Download.InProgress) return
        viewModelScope.launch {
            _download.value = Download.InProgress(null)
            val result = ai.modelManager.downloadChatModel(ai.chatModel) { p ->
                _download.value = Download.InProgress(p.fraction)
            }
            when (result) {
                is ModelManager.Result.Failure -> _download.value = Download.Failed(result.message)
                else -> {
                    _download.value = Download.Idle
                    refreshChatModelReady()
                    // Make sure the embedding index exists for retrieval.
                    NoteIndexingWorker.enqueue(getApplication())
                }
            }
        }
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _isGenerating.value) return

        _messages.value = _messages.value +
            Message(Message.Role.USER, q) +
            Message(Message.Role.ASSISTANT, "", streaming = true)
        _isGenerating.value = true

        viewModelScope.launch {
            val answer = StringBuilder()
            var citations: List<Citation> = emptyList()
            ai.ragChat().ask(q).collect { event ->
                when (event) {
                    is RagEvent.Sources -> {
                        citations = event.citations
                        updateAssistant(answer.toString(), citations, streaming = true)
                    }
                    is RagEvent.Token -> {
                        answer.append(event.text)
                        updateAssistant(answer.toString(), citations, streaming = true)
                    }
                    is RagEvent.Error -> {
                        updateAssistant(
                            answer.toString().ifBlank { "Sorry — ${event.message}" },
                            citations, streaming = false,
                        )
                    }
                    RagEvent.Done -> updateAssistant(answer.toString(), citations, streaming = false)
                }
            }
            _isGenerating.value = false
        }
    }

    private fun updateAssistant(text: String, citations: List<Citation>, streaming: Boolean) {
        val current = _messages.value.toMutableList()
        val idx = current.indexOfLast { it.role == Message.Role.ASSISTANT }
        if (idx >= 0) {
            current[idx] = current[idx].copy(text = text, citations = citations, streaming = streaming)
            _messages.value = current
        }
    }
}
